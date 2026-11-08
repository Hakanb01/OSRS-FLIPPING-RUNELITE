package com.osrsmerch.flipper;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The Grand Exchange tax, only to show a profit on screen: 2% per piece, rounded down, at most 5,000,000, and nothing for the
 * exempt items. The same rule as the bot (src/modules/economy/tax.ts); the bot stays the authority and subtracts the tax once
 * from the gross amount the plugin sends. Keep the exempt list in line with migrations/001_base.sql.
 */
public final class Tax
{
	public static final long RATE_BP = 200;
	public static final long CAP = 5_000_000L;

	private static final Set<Integer> EXEMPT = Arrays.stream(new Integer[]{
		13190, 3008, 3010, 3012, 3014, 882, 806, 884, 807, 886, 808, 558, 365, 2309, 1891, 2140, 2142, 347, 379, 355, 2327, 351, 329, 315,
		361, 8011, 8010, 28824, 8009, 3853, 28790, 8008, 2552, 8013, 8007, 1755, 5325, 1785, 2347, 1733, 233, 5341, 8794, 5329, 5343, 1735,
		952, 5331
	}).collect(Collectors.toSet());

	private Tax()
	{
	}

	public static long perItem(int itemId, long sellPrice)
	{
		if (sellPrice <= 0 || EXEMPT.contains(itemId))
		{
			return 0;
		}
		return Math.min(CAP, sellPrice * RATE_BP / 10_000);
	}

	/** What one piece brings in after tax. */
	public static long afterTax(int itemId, long sellPrice)
	{
		return sellPrice - perItem(itemId, sellPrice);
	}
}
