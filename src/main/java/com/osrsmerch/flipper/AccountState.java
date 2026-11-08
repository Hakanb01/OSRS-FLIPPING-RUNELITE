package com.osrsmerch.flipper;

import java.util.HashMap;
import java.util.Map;

/** The saved state of one RuneLite account: its slots and its cost basis. Accounts never share anything. */
public final class AccountState
{
	final Map<Integer, SlotRecord> slots = new HashMap<>();
	final CostBasis costBasis = new CostBasis();
	/** Last display name seen; shown in the panel. */
	String name;

	public CostBasis getCostBasis()
	{
		return costBasis;
	}

	public SlotRecord slot(int slot)
	{
		return slots.get(slot);
	}
}
