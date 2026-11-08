package com.osrsmerch.flipper;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.util.OptionalLong;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

/**
 * Shows only: a tooltip with the profit of the hovered inventory item against the known buy price, at the current Grand
 * Exchange price after tax. It states a number and never tells the player what to do.
 */
final class InventoryProfitTooltip extends Overlay
{
	private static final int INVENTORY_GROUP = 149;
	private static final int INVENTORY_CHILD = 0;

	private final Client client;
	private final FlipperPlugin plugin;
	private final ItemManager itemManager;
	private final TooltipManager tooltipManager;

	InventoryProfitTooltip(Client client, FlipperPlugin plugin, ItemManager itemManager, TooltipManager tooltipManager)
	{
		this.client = client;
		this.plugin = plugin;
		this.itemManager = itemManager;
		this.tooltipManager = tooltipManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!plugin.config().inventoryTooltip() || client.isMenuOpen())
		{
			return null;
		}
		Widget inv = client.getWidget(INVENTORY_GROUP, INVENTORY_CHILD);
		if (inv == null || inv.isHidden())
		{
			return null;
		}
		net.runelite.api.Point mouse = client.getMouseCanvasPosition();
		Point p = new Point(mouse.getX(), mouse.getY());
		Widget[] items = inv.getDynamicChildren();
		if (items == null)
		{
			return null;
		}
		for (Widget item : items)
		{
			if (item.getItemId() <= 0 || item.getItemQuantity() <= 0 || !item.getBounds().contains(p))
			{
				continue;
			}
			int id = itemManager.canonicalize(item.getItemId());
			OptionalLong cost = plugin.knownCost(id);
			if (!cost.isPresent())
			{
				tooltipManager.add(new Tooltip("OSRS Flipping: buy price unknown"));
				return null;
			}
			long price = itemManager.getItemPrice(id);
			if (price <= 0)
			{
				return null;
			}
			long perItem = Tax.afterTax(id, price) - cost.getAsLong();
			long total = perItem * item.getItemQuantity();
			tooltipManager.add(new Tooltip("OSRS Flipping: " + Money.signed(total) + " gp at GE price after tax (bought at " + Money.exact(cost.getAsLong()) + " each)"));
			return null;
		}
		return null;
	}
}
