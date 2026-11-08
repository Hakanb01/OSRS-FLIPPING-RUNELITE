package com.osrsmerch.flipper;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Starts RuneLite in developer mode with this plugin loaded: {@code ./gradlew run}. Not a unit test. */
public class FlipperPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(FlipperPlugin.class);
		RuneLite.main(args);
	}
}
