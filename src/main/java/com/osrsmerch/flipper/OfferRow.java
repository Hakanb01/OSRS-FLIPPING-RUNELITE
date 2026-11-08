package com.osrsmerch.flipper;

/** One live Grand Exchange offer as the panel shows it. */
public final class OfferRow
{
	final int slot;
	final Side side;
	final Phase phase;
	final int itemId;
	final String itemName;
	final int quantitySold;
	final int totalQuantity;
	final long price;
	/** Profit per piece after tax against the known buy price; null when unknown (or for a buy). */
	final Long profitPerItem;

	public OfferRow(int slot, Side side, Phase phase, int itemId, String itemName, int quantitySold, int totalQuantity, long price, Long profitPerItem)
	{
		this.slot = slot;
		this.side = side;
		this.phase = phase;
		this.itemId = itemId;
		this.itemName = itemName;
		this.quantitySold = quantitySold;
		this.totalQuantity = totalQuantity;
		this.price = price;
		this.profitPerItem = profitPerItem;
	}

	double fraction()
	{
		return totalQuantity <= 0 ? 0 : Math.min(1.0, (double) quantitySold / totalQuantity);
	}
}
