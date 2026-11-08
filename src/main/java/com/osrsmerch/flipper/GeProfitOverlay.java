package com.osrsmerch.flipper;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.function.IntFunction;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Shows only: tints a sell offer in the Grand Exchange window green or red by the profit against the known buy price. No
 * text, no marker that asks for a click, and nothing is changed in the game.
 */
final class GeProfitOverlay extends Overlay
{
	private static final int GE_GROUP = 465;
	private static final int FIRST_SLOT_CHILD = 7;
	private static final Color GREEN = new Color(0, 200, 83, 60);
	private static final Color RED = new Color(220, 40, 40, 60);

	private final Client client;
	private final FlipperPlugin plugin;

	GeProfitOverlay(Client client, FlipperPlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!plugin.config().colorSlots())
		{
			return null;
		}
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		if (offers == null)
		{
			return null;
		}
		for (int slot = 0; slot < offers.length; slot++)
		{
			Widget w = client.getWidget(GE_GROUP, FIRST_SLOT_CHILD + slot);
			if (w == null || w.isHidden())
			{
				continue;
			}
			Long profit = plugin.profitPerItem(SavedOffer.from(offers[slot]));
			if (profit == null || profit == 0)
			{
				continue;
			}
			Rectangle b = w.getBounds();
			g.setColor(profit > 0 ? GREEN : RED);
			g.fill(b);
		}
		return null;
	}
}
