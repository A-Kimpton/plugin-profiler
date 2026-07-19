package io.kimpton.pluginprofiler;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(PluginProfilerConfig.GROUP)
public interface PluginProfilerConfig extends Config
{
	String GROUP = "plugin-profiler";

	@Range(min = 20, max = 200)
	@ConfigItem(
		keyName = "sampleRateHz",
		name = "Sample rate (Hz)",
		description = "How many times per second to sample the client and Swing threads",
		position = 1
	)
	default int sampleRateHz()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "autoPauseLoggedOut",
		name = "Pause when logged out",
		description = "Automatically pause sampling while not logged in",
		position = 2
	)
	default boolean autoPauseLoggedOut()
	{
		return true;
	}
}
