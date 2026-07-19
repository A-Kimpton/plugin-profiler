package io.kimpton.pluginprofiler;

import com.google.inject.Provides;
import io.kimpton.pluginprofiler.sampler.LogSink;
import io.kimpton.pluginprofiler.sampler.SamplerService;
import io.kimpton.pluginprofiler.sampler.StackAttributor;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ExternalPluginsChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;

@Slf4j
@PluginDescriptor(
	name = "Plugin Profiler",
	description = "Shows the CPU cost of every installed plugin via in-client sampling",
	tags = {"performance", "profiler", "cpu", "lag", "fps"}
)
public class PluginProfilerPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private PluginProfilerConfig config;

	private StackAttributor attributor;
	private SamplerService sampler;

	@Override
	protected void startUp() throws Exception
	{
		attributor = new StackAttributor();
		attributor.rebuild(pluginManager.getPlugins());

		sampler = new SamplerService(attributor, new LogSink());
		sampler.setClientThread(client.getClientThread());
		SwingUtilities.invokeLater(() -> sampler.setEdtThread(Thread.currentThread()));
		updatePaused(client.getGameState());
		sampler.start(config.sampleRateHz());
	}

	@Override
	protected void shutDown() throws Exception
	{
		if (sampler != null)
		{
			sampler.stop();
			sampler = null;
		}
		attributor = null;
	}

	@Subscribe
	public void onPluginChanged(PluginChanged event)
	{
		attributor.rebuild(pluginManager.getPlugins());
	}

	@Subscribe
	public void onExternalPluginsChanged(ExternalPluginsChanged event)
	{
		attributor.rebuild(pluginManager.getPlugins());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (sampler.getClientThread() == null)
		{
			sampler.setClientThread(client.getClientThread());
		}
		updatePaused(event.getGameState());
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!PluginProfilerConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		sampler.start(config.sampleRateHz());
		updatePaused(client.getGameState());
	}

	private void updatePaused(GameState state)
	{
		boolean inGame = state == GameState.LOGGED_IN
			|| state == GameState.LOADING
			|| state == GameState.HOPPING
			|| state == GameState.CONNECTION_LOST;
		sampler.setPaused(config.autoPauseLoggedOut() && !inGame);
	}

	@Provides
	PluginProfilerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PluginProfilerConfig.class);
	}
}
