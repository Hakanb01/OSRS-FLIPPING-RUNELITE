package com.osrsmerch.flipper;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;

/**
 * What the player paid, per item, for what they still hold according to the trades seen by the plugin. A buy adds, a sell takes
 * away at the average price. Used only to show a profit in the game; an item with no known buy has an unknown cost.
 */
public final class CostBasis
{
	static final class Lot
	{
		long quantity;
		long cost;
	}

	private final Map<Integer, Lot> lots = new HashMap<>();

	public void buy(int itemId, long quantity, long gp)
	{
		if (quantity <= 0 || gp < 0)
		{
			return;
		}
		Lot lot = lots.computeIfAbsent(itemId, k -> new Lot());
		lot.quantity += quantity;
		lot.cost += gp;
	}

	public void sell(int itemId, long quantity)
	{
		Lot lot = lots.get(itemId);
		if (lot == null || quantity <= 0)
		{
			return;
		}
		if (quantity >= lot.quantity)
		{
			lots.remove(itemId);
			return;
		}
		lot.cost -= lot.cost * quantity / lot.quantity;
		lot.quantity -= quantity;
	}

	/** Average cost per piece, rounded to the nearest gp; empty when nothing is known. */
	public OptionalLong averageCost(int itemId)
	{
		Lot lot = lots.get(itemId);
		if (lot == null || lot.quantity <= 0)
		{
			return OptionalLong.empty();
		}
		return OptionalLong.of((lot.cost * 2 + lot.quantity) / (lot.quantity * 2));
	}

	public long heldQuantity(int itemId)
	{
		Lot lot = lots.get(itemId);
		return lot == null ? 0 : lot.quantity;
	}
}
