package io.kimpton.pluginprofiler;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Plugin Profiler",
	description = "Shows the CPU cost of every installed plugin via in-client sampling",
	tags = {"performance", "profiler", "cpu", "lag", "fps"}
)
public class PluginProfilerPlugin extends Plugin
{
	@Inject
	private PluginProfilerConfig config;

	@Override
	protected void startUp() throws Exception
	{
		log.debug("Plugin Profiler started");
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.debug("Plugin Profiler stopped");
	}

	@Provides
	PluginProfilerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PluginProfilerConfig.class);
	}
}
