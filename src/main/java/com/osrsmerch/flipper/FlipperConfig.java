package com.osrsmerch.flipper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(FlipperConfig.GROUP)
public interface FlipperConfig extends Config
{
	String GROUP = "flipper";

	@ConfigSection(
		name = "Link to your bot",
		description = "Send your Grand Exchange trades to your own OSRS Flipping Discord bot",
		position = 0
	)
	String linkSection = "link";

	@ConfigSection(
		name = "In game",
		description = "What the plugin shows on screen. It only shows; it never clicks or fills anything in",
		position = 1
	)
	String gameSection = "game";

	@ConfigItem(
		keyName = "sendTrades",
		name = "Send trades",
		description = "Send bought, sold, partly filled and cancelled Grand Exchange offers to your OSRS Flipping bot. Off by default: nothing leaves your computer until you turn this on",
		warning = "This feature sends your Grand Exchange trades and your IP address to a server that is not controlled or verified by the RuneLite developers (the OSRS Flipping bot address you filled in below). Only turn it on if you trust that server. Are you sure you want to enable this?",
		section = linkSection,
		position = 0
	)
	default boolean sendTrades()
	{
		return false;
	}

	@ConfigItem(
		keyName = "serverUrl",
		name = "Server URL",
		description = "The address of your OSRS Flipping bot, from the owner of the bot. Must start with https://",
		section = linkSection,
		position = 1
	)
	default String serverUrl()
	{
		return "";
	}

	@ConfigItem(
		keyName = "linkCode",
		name = "Link code",
		description = "The code from /runelite link in Discord. Keep it private",
		secret = true,
		section = linkSection,
		position = 2
	)
	default String linkCode()
	{
		return "";
	}

	@ConfigItem(
		keyName = "sendAccount",
		name = "Send account name and ID",
		description = "Also send your account name and account hash with each trade, so the bot can keep several accounts apart. Off by default",
		section = linkSection,
		position = 3
	)
	default boolean sendAccount()
	{
		return false;
	}

	@ConfigItem(
		keyName = "sendGp",
		name = "Send gp stack",
		description = "Also send the coins in your inventory with each trade, so the bot can suggest flips for your gp. Off by default",
		section = linkSection,
		position = 4
	)
	default boolean sendGp()
	{
		return false;
	}

	@ConfigItem(
		keyName = "colorSlots",
		name = "Colour sell slots",
		description = "Tint your sell offers in the Grand Exchange green or red by the profit against your known buy price",
		section = gameSection,
		position = 0
	)
	default boolean colorSlots()
	{
		return true;
	}

	@ConfigItem(
		keyName = "inventoryTooltip",
		name = "Inventory profit tooltip",
		description = "Show the profit against your known buy price when you hover an item in your inventory",
		section = gameSection,
		position = 1
	)
	default boolean inventoryTooltip()
	{
		return true;
	}
}
