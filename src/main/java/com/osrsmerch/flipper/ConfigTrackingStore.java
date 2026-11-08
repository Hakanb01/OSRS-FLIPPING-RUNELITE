package com.osrsmerch.flipper;

import net.runelite.client.config.ConfigManager;

/** Keeps the state in RuneLite's own config file (per RuneLite profile), so it survives a restart. */
public final class ConfigTrackingStore implements TrackingStore
{
	static final String KEY = "state";
	private final ConfigManager configManager;

	public ConfigTrackingStore(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	@Override
	public String read()
	{
		return configManager.getConfiguration(FlipperConfig.GROUP, KEY);
	}

	@Override
	public void write(String json)
	{
		configManager.setConfiguration(FlipperConfig.GROUP, KEY, json);
	}
}
