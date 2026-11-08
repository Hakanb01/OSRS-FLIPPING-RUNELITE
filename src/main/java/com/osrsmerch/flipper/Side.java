package com.osrsmerch.flipper;

/** Buy or sell side of a Grand Exchange offer; EMPTY is an unused slot. */
public enum Side
{
	BUY, SELL, EMPTY;

	/** The word the bot contract uses. */
	public String wire()
	{
		return this == BUY ? "buy" : "sell";
	}
}
