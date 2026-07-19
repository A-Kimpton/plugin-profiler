package io.kimpton.pluginprofiler.sampler;

import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Value;
import net.runelite.client.plugins.Plugin;

/**
 * Resolves stack frames to the plugin that owns them.
 *
 * Ownership rules:
 * - Classes in this profiler's own package are always {@link #SELF}.
 * - Plugins loaded by the application classloader (core + sideloaded) own their
 *   package subtree; longest package prefix wins.
 * - Hub plugins own every class their jar contains, checked via
 *   {@link URLClassLoader#findResource} — membership is tested without loading
 *   or defining any class, so shaded libraries attribute to the shading plugin.
 *
 * A stack is attributed to the first plugin-owned frame walking top-down;
 * RuneLite and game-engine frames pass through. Only a stack with no
 * plugin-owned frame at all is bucketed as {@link #ENGINE}.
 */
public class StackAttributor
{
	public static final String SELF = "Plugin Profiler (self)";
	public static final String ENGINE = "RuneLite / game engine";

	private static final String SELF_PACKAGE = "io.kimpton.pluginprofiler.";
	private static final String NO_OWNER = "";

	private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();
	private volatile List<PackagePrefix> packagePrefixes = new ArrayList<>();
	private volatile List<HubEntry> hubEntries = new ArrayList<>();

	@Value
	static class PackagePrefix
	{
		String prefix;
		String pluginName;
	}

	@Value
	static class HubEntry
	{
		URLClassLoader loader;
		String pluginName;
	}

	public void rebuild(Collection<Plugin> plugins)
	{
		ClassLoader appLoader = Plugin.class.getClassLoader();
		List<PackagePrefix> prefixes = new ArrayList<>();
		List<HubEntry> hubs = new ArrayList<>();

		for (Plugin plugin : plugins)
		{
			Class<?> clazz = plugin.getClass();
			if (clazz.getName().startsWith(SELF_PACKAGE))
			{
				continue;
			}

			ClassLoader loader = clazz.getClassLoader();
			if (loader == appLoader)
			{
				String pkg = clazz.getPackage() != null ? clazz.getPackage().getName() : NO_OWNER;
				if (!pkg.isEmpty())
				{
					prefixes.add(new PackagePrefix(pkg + ".", plugin.getName()));
				}
			}
			else if (loader instanceof URLClassLoader)
			{
				hubs.add(new HubEntry((URLClassLoader) loader, plugin.getName()));
			}
		}

		install(prefixes, hubs);
	}

	void install(List<PackagePrefix> prefixes, List<HubEntry> hubs)
	{
		List<PackagePrefix> sorted = new ArrayList<>(prefixes);
		sorted.sort(Comparator.comparingInt((PackagePrefix p) -> p.getPrefix().length()).reversed());
		packagePrefixes = sorted;
		hubEntries = new ArrayList<>(hubs);
		cache.clear();
	}

	/**
	 * Attribute a sampled stack. Returns the owner label and, for plugin-owned
	 * samples, the first plugin-owned frame ({@code Class.method}).
	 */
	public Attribution attribute(StackTraceElement[] stack)
	{
		for (StackTraceElement frame : stack)
		{
			String owner = resolve(frame.getClassName());
			if (!owner.isEmpty())
			{
				String cls = frame.getClassName();
				int lastDot = cls.lastIndexOf('.');
				String topFrame = (lastDot >= 0 ? cls.substring(lastDot + 1) : cls) + "." + frame.getMethodName();
				return new Attribution(owner, topFrame);
			}
		}
		return new Attribution(ENGINE, null);
	}

	String resolve(String className)
	{
		String normalized = normalize(className);
		String cached = cache.get(normalized);
		if (cached != null)
		{
			return cached;
		}

		String owner = resolveUncached(normalized);
		cache.put(normalized, owner);
		return owner;
	}

	private String resolveUncached(String className)
	{
		if (className.startsWith(SELF_PACKAGE))
		{
			return SELF;
		}

		for (PackagePrefix entry : packagePrefixes)
		{
			if (className.startsWith(entry.getPrefix()))
			{
				return entry.getPluginName();
			}
		}

		String resourcePath = className.replace('.', '/') + ".class";
		for (HubEntry hub : hubEntries)
		{
			try
			{
				if (hub.getLoader().findResource(resourcePath) != null)
				{
					return hub.getPluginName();
				}
			}
			catch (RuntimeException ignored)
			{
				// closed loader mid-rebuild; treat as not owned
			}
		}

		return NO_OWNER;
	}

	/**
	 * Strip lambda ({@code X$$Lambda$12/0x...}) and inner/anonymous class
	 * ({@code X$1}, {@code X$Inner}) suffixes down to the top-level class,
	 * which is what determines ownership.
	 */
	static String normalize(String className)
	{
		int dollar = className.indexOf('$');
		return dollar < 0 ? className : className.substring(0, dollar);
	}

	@Value
	public static class Attribution
	{
		String owner;
		String topFrame;
	}
}
