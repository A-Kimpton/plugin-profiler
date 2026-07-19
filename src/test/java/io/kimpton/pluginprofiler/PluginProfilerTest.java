package io.kimpton.pluginprofiler;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class PluginProfilerTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(PluginProfilerPlugin.class);
		RuneLite.main(args);
	}
}
