package com.osrsmerch.flipper;

import java.text.NumberFormat;
import java.util.Locale;

/** Short, readable gp amounts for the panel: 1.2m, 350k, -45k. */
public final class Money
{
	private Money()
	{
	}

	public static String exact(long gp)
	{
		return NumberFormat.getIntegerInstance(Locale.US).format(gp);
	}

	public static String shortGp(long gp)
	{
		long a = Math.abs(gp);
		String s;
		if (a >= 1_000_000_000L)
		{
			s = trim(a / 1_000_000_000.0, 2) + "b";
		}
		else if (a >= 1_000_000L)
		{
			s = trim(a / 1_000_000.0, 2) + "m";
		}
		else if (a >= 10_000L)
		{
			s = Math.round(a / 1_000.0) + "k";
		}
		else
		{
			s = Long.toString(a);
		}
		return gp < 0 ? "-" + s : s;
	}

	/** With a plus sign for gains: +1.2m, -45k, 0. */
	public static String signed(long gp)
	{
		return gp > 0 ? "+" + shortGp(gp) : shortGp(gp);
	}

	/** A budget the way players type it: 50m, 250k, 1.5b, or plain digits; null when it cannot be read. */
	public static Long parse(String text)
	{
		if (text == null)
		{
			return null;
		}
		String t = text.trim().toLowerCase(Locale.ROOT).replace(",", "").replace("_", "");
		if (t.isEmpty())
		{
			return null;
		}
		long mult = 1;
		char last = t.charAt(t.length() - 1);
		if (last == 'k' || last == 'm' || last == 'b')
		{
			mult = last == 'k' ? 1_000L : last == 'm' ? 1_000_000L : 1_000_000_000L;
			t = t.substring(0, t.length() - 1);
		}
		try
		{
			double v = Double.parseDouble(t);
			if (v <= 0 || Double.isNaN(v) || Double.isInfinite(v) || v * mult > 9e15)
			{
				return null;
			}
			return Math.round(v * mult);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	private static String trim(double v, int decimals)
	{
		String s = String.format(Locale.US, "%." + decimals + "f", v);
		if (s.indexOf('.') >= 0)
		{
			s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
		}
		return s;
	}
}
