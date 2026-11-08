package com.osrsmerch.flipper;

/**
 * Everything kept per (account, slot): the last offer seen, the random id of this offer, the fills that were already there
 * when the offer was first seen (the baseline, never sent) and what was last sent to the bot.
 */
public final class SlotRecord
{
	SavedOffer offer;
	/** Random id of this offer; never the slot number, because slots are reused. Null for an empty slot. */
	String offerId;
	/** Fills already present when this offer was first seen (login or first run). They are not trades of this session. */
	int baseQuantity;
	long baseSpent;
	/** Cumulative fills (after the baseline) and status of the last message queued for this offer. */
	int sentQuantity;
	long sentGp;
	String sentStatus;

	public SlotRecord(SavedOffer offer, String offerId, int baseQuantity, long baseSpent)
	{
		this.offer = offer;
		this.offerId = offerId;
		this.baseQuantity = baseQuantity;
		this.baseSpent = baseSpent;
	}

	public SavedOffer getOffer()
	{
		return offer;
	}

	public String getOfferId()
	{
		return offerId;
	}

	public int getBaseQuantity()
	{
		return baseQuantity;
	}

	public long getBaseSpent()
	{
		return baseSpent;
	}

	public int getSentQuantity()
	{
		return sentQuantity;
	}

	public long getSentGp()
	{
		return sentGp;
	}

	public String getSentStatus()
	{
		return sentStatus;
	}

	/** Fills of this offer that count as trades: what was filled minus the baseline. */
	public int cumulativeQuantity()
	{
		return Math.max(0, offer.getQuantitySold() - baseQuantity);
	}

	public long cumulativeGp()
	{
		return Math.max(0, offer.getSpent() - baseSpent);
	}
}
