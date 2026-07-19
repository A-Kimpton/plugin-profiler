package io.kimpton.pluginprofiler.sampler;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import net.runelite.client.plugins.fakecore.FakeCorePlugin;
import net.runelite.client.plugins.fakecore.sub.FakeSubPlugin;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class StackAttributorTest
{
	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static StackTraceElement frame(String className, String method)
	{
		return new StackTraceElement(className, method, null, -1);
	}

	private StackAttributor attributorWithFakes()
	{
		StackAttributor attributor = new StackAttributor();
		attributor.rebuild(Arrays.asList(new FakeCorePlugin(), new FakeSubPlugin()));
		return attributor;
	}

	@Test
	public void normalizeStripsLambdaAndInnerClasses()
	{
		assertEquals("com.foo.Bar", StackAttributor.normalize("com.foo.Bar$$Lambda$123/0x00007f8"));
		assertEquals("com.foo.Bar", StackAttributor.normalize("com.foo.Bar$1"));
		assertEquals("com.foo.Bar", StackAttributor.normalize("com.foo.Bar$Inner$2"));
		assertEquals("com.foo.Bar", StackAttributor.normalize("com.foo.Bar"));
	}

	@Test
	public void corePluginOwnedByPackagePrefix()
	{
		StackAttributor.Attribution result = attributorWithFakes().attribute(new StackTraceElement[]{
			frame("net.runelite.client.plugins.fakecore.FakeCorePlugin", "onGameTick"),
		});

		assertEquals("Fake Core", result.getOwner());
		assertEquals("FakeCorePlugin.onGameTick", result.getTopFrame());
	}

	@Test
	public void longestPackagePrefixWins()
	{
		StackAttributor.Attribution result = attributorWithFakes().attribute(new StackTraceElement[]{
			frame("net.runelite.client.plugins.fakecore.sub.Helper", "run"),
		});

		assertEquals("Fake Sub", result.getOwner());
	}

	@Test
	public void engineFramesPassThroughToPluginFrame()
	{
		// plugin called into the RuneLite API; the sample landed inside engine
		// code — must still attribute to the plugin beneath, not "engine"
		StackAttributor.Attribution result = attributorWithFakes().attribute(new StackTraceElement[]{
			frame("net.runelite.api.coords.WorldPoint", "fromLocal"),
			frame("net.runelite.client.plugins.fakecore.FakeCorePlugin", "scanScene"),
			frame("net.runelite.client.eventbus.EventBus", "post"),
			frame("net.runelite.client.RuneLite", "main"),
		});

		assertEquals("Fake Core", result.getOwner());
		assertEquals("FakeCorePlugin.scanScene", result.getTopFrame());
	}

	@Test
	public void eventDispatchFramesBelongToSubscriber()
	{
		StackAttributor.Attribution result = attributorWithFakes().attribute(new StackTraceElement[]{
			frame("net.runelite.client.plugins.fakecore.FakeCorePlugin$$Lambda$42/0x1", "accept"),
			frame("net.runelite.client.eventbus.EventBus", "post"),
			frame("net.runelite.client.callback.Hooks", "post"),
		});

		assertEquals("Fake Core", result.getOwner());
	}

	@Test
	public void stackWithNoPluginFrameIsEngine()
	{
		StackAttributor.Attribution result = attributorWithFakes().attribute(new StackTraceElement[]{
			frame("java.util.HashMap", "resize"),
			frame("net.runelite.client.eventbus.EventBus", "post"),
			frame("net.runelite.client.RuneLite", "main"),
		});

		assertEquals(StackAttributor.ENGINE, result.getOwner());
		assertNull(result.getTopFrame());
	}

	@Test
	public void profilerOwnFramesAreSelf()
	{
		StackAttributor.Attribution result = attributorWithFakes().attribute(new StackTraceElement[]{
			frame("io.kimpton.pluginprofiler.sampler.SamplerService", "sampleTick"),
			frame("net.runelite.client.RuneLite", "main"),
		});

		assertEquals(StackAttributor.SELF, result.getOwner());
	}

	@Test
	public void hubPluginOwnsClassesInItsJar() throws Exception
	{
		// hub loaders are probed by jar membership (findResource), so a plain
		// URLClassLoader over a directory with marker .class files stands in
		// for a PluginHubClassLoader
		File root = tmp.getRoot();
		Path pluginClass = root.toPath().resolve("com/hubdev/cool/CoolPlugin.class");
		Path shadedClass = root.toPath().resolve("org/shadedlib/Util.class");
		Files.createDirectories(pluginClass.getParent());
		Files.createDirectories(shadedClass.getParent());
		Files.createFile(pluginClass);
		Files.createFile(shadedClass);

		try (URLClassLoader hubLoader = new URLClassLoader(new URL[]{root.toURI().toURL()}, null))
		{
			StackAttributor attributor = new StackAttributor();
			attributor.install(
				Collections.emptyList(),
				Collections.singletonList(new StackAttributor.HubEntry(hubLoader, "Cool Plugin")));

			assertEquals("Cool Plugin", attributor.attribute(new StackTraceElement[]{
				frame("com.hubdev.cool.CoolPlugin", "onGameTick"),
			}).getOwner());

			// a library shaded into the hub jar attributes to the shading plugin
			assertEquals("Cool Plugin", attributor.attribute(new StackTraceElement[]{
				frame("org.shadedlib.Util", "parse"),
			}).getOwner());

			// unrelated classes are not owned by the hub plugin
			assertEquals(StackAttributor.ENGINE, attributor.attribute(new StackTraceElement[]{
				frame("com.otherdev.Other", "run"),
			}).getOwner());
		}
	}

	@Test
	public void rebuildClearsCachedOwnership()
	{
		StackAttributor attributor = attributorWithFakes();
		StackTraceElement[] stack = {frame("net.runelite.client.plugins.fakecore.FakeCorePlugin", "onGameTick")};

		assertEquals("Fake Core", attributor.attribute(stack).getOwner());

		attributor.rebuild(Collections.emptyList());
		assertEquals(StackAttributor.ENGINE, attributor.attribute(stack).getOwner());
	}
}
