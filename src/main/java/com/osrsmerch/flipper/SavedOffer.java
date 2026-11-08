package com.osrsmerch.flipper;

import java.util.Objects;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;

/**
 * What one Grand Exchange slot looked like at one moment. Immutable. Two offers are equal when every field is equal; the
 * tracker uses that to ignore events the game repeats (for example right after login).
 */
public final class SavedOffer
{
	private final Side side;
	private final Phase phase;
	private final int itemId;
	private final int quantitySold;
	private final int totalQuantity;
	private final long price;
	/** Total gp moved so far, as RuneLite reports it. For a sell this is the amount before Grand Exchange tax. */
	private final long spent;

	public SavedOffer(Side side, Phase phase, int itemId, int quantitySold, int totalQuantity, long price, long spent)
	{
		this.side = side;
		this.phase = phase;
		this.itemId = itemId;
		this.quantitySold = quantitySold;
		this.totalQuantity = totalQuantity;
		this.price = price;
		this.spent = spent;
	}

	public static SavedOffer empty()
	{
		return new SavedOffer(Side.EMPTY, Phase.EMPTY, 0, 0, 0, 0, 0);
	}

	/** Reads RuneLite's offer object; nothing is changed in the game. */
	public static SavedOffer from(GrandExchangeOffer offer)
	{
		GrandExchangeOfferState state = offer.getState();
		Side side;
		Phase phase;
		switch (state)
		{
			case BUYING:
				side = Side.BUY;
				phase = Phase.ACTIVE;
				break;
			case BOUGHT:
				side = Side.BUY;
				phase = Phase.DONE;
				break;
			case CANCELLED_BUY:
				side = Side.BUY;
				phase = Phase.CANCELLED;
				break;
			case SELLING:
				side = Side.SELL;
				phase = Phase.ACTIVE;
				break;
			case SOLD:
				side = Side.SELL;
				phase = Phase.DONE;
				break;
			case CANCELLED_SELL:
				side = Side.SELL;
				phase = Phase.CANCELLED;
				break;
			default:
				return empty();
		}
		return new SavedOffer(side, phase, offer.getItemId(), offer.getQuantitySold(), offer.getTotalQuantity(), offer.getPrice(), offer.getSpent());
	}

	public Side getSide()
	{
		return side;
	}

	public Phase getPhase()
	{
		return phase;
	}

	public int getItemId()
	{
		return itemId;
	}

	public int getQuantitySold()
	{
		return quantitySold;
	}

	public int getTotalQuantity()
	{
		return totalQuantity;
	}

	public long getPrice()
	{
		return price;
	}

	public long getSpent()
	{
		return spent;
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof SavedOffer))
		{
			return false;
		}
		SavedOffer that = (SavedOffer) o;
		return side == that.side && phase == that.phase && itemId == that.itemId && quantitySold == that.quantitySold
			&& totalQuantity == that.totalQuantity && price == that.price && spent == that.spent;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(side, phase, itemId, quantitySold, totalQuantity, price, spent);
	}

	@Override
	public String toString()
	{
		return side + "/" + phase + " item " + itemId + " " + quantitySold + "/" + totalQuantity + " @" + price + " spent " + spent;
	}
}
