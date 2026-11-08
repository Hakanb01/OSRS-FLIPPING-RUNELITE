package com.osrsmerch.flipper;

/** Where an offer is in its life: still trading, finished, or cancelled. */
public enum Phase
{
	ACTIVE, DONE, CANCELLED, EMPTY;

	public boolean terminal()
	{
		return this == DONE || this == CANCELLED;
	}

	/** The status word of the v2 contract. */
	public String wire()
	{
		return this == DONE ? "complete" : this == CANCELLED ? "cancelled" : "active";
	}
}
