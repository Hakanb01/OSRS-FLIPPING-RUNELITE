package com.osrsmerch.flipper;

import java.util.Collections;
import java.util.List;

/** The bot's answer to GET /api/runelite/summary. Gp amounts are text (exact gp); parse with {@link #gp(String)}. */
public final class Summary
{
	public int version;
	public long generatedAt;
	public String timezone;
	public Today today;
	public String totalProfit;
	public int unknownCostSales;
	public List<AccountLine> accounts = Collections.emptyList();
	public List<ItemLine> items = Collections.emptyList();
	public List<ChartPoint> chart = Collections.emptyList();
	public List<OpenBuy> openBuys = Collections.emptyList();
	public List<FlipLine> lastFlips = Collections.emptyList();
	public Advice advice;

	public static final class Today
	{
		public String day;
		public String profit;
		public int flips;
	}

	public static final class AccountLine
	{
		public String accountHash;
		public String accountName;
		public String today;
		public String total;
	}

	public static final class ItemLine
	{
		public int itemId;
		public String name;
		public int quantity;
		public String today;
		public String total;
	}

	public static final class ChartPoint
	{
		public String day;
		public String profit;
	}

	public static final class OpenBuy
	{
		public int id;
		public int itemId;
		public String name;
		public int quantity;
		public String buyPrice;
		public String buyTotal;
		public long since;
		public String accountHash;
		public String accountName;
	}

	public static final class FlipLine
	{
		public int id;
		public int itemId;
		public String name;
		public int quantity;
		public String soldTotal;
		/** Null when the cost of this sale is unknown. */
		public String profit;
		public boolean costKnown;
		public long soldAt;
		public String accountName;
	}

	public static final class Advice
	{
		/** ok, none, no-budget or vip-only. */
		public String status;
		public String budget;
		public String source;
		public List<AdviceItem> items = Collections.emptyList();
	}

	public static final class AdviceItem
	{
		public int itemId;
		public String name;
		public String buyPrice;
		public String sellPrice;
		public int quantity;
		public String netProfit;
		public Double fillHours;
	}

	/** Exact gp from the bot's text; 0 for anything unreadable. */
	public static long gp(String s)
	{
		if (s == null)
		{
			return 0;
		}
		try
		{
			return Long.parseLong(s);
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}
}
