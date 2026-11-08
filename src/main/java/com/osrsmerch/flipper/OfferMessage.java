package com.osrsmerch.flipper;

import com.google.gson.JsonObject;

/**
 * One message for the bot, cumulative: {@code quantity} and {@code totalGp} are everything filled so far for this offer id (after
 * the baseline). For a sell {@code totalGp} is the amount before Grand Exchange tax, exactly what RuneLite reports as spent;
 * the bot subtracts the tax once. Account and gp are kept here but only written to the wire when the player opted in.
 */
public final class OfferMessage
{
	final String offerId;
	final int itemId;
	final String type;
	final int quantity;
	final long totalGp;
	final long time;
	final String status;
	final String accountHash;
	final String accountName;
	final Long gp;

	public OfferMessage(String offerId, int itemId, String type, int quantity, long totalGp, long time, String status, String accountHash, String accountName, Long gp)
	{
		this.offerId = offerId;
		this.itemId = itemId;
		this.type = type;
		this.quantity = quantity;
		this.totalGp = totalGp;
		this.time = time;
		this.status = status;
		this.accountHash = accountHash;
		this.accountName = accountName;
		this.gp = gp;
	}

	public String getOfferId()
	{
		return offerId;
	}

	public int getItemId()
	{
		return itemId;
	}

	public String getType()
	{
		return type;
	}

	public int getQuantity()
	{
		return quantity;
	}

	public long getTotalGp()
	{
		return totalGp;
	}

	public long getTime()
	{
		return time;
	}

	public String getStatus()
	{
		return status;
	}

	public String getAccountHash()
	{
		return accountHash;
	}

	/**
	 * The v2 request body. The account (hash and name) and the gp stack are only written when their own opt-ins are on; the
	 * name is never sent without the hash.
	 */
	public JsonObject toJson(boolean includeAccount, boolean includeGp)
	{
		JsonObject o = new JsonObject();
		o.addProperty("offerId", offerId);
		o.addProperty("itemId", itemId);
		o.addProperty("type", type);
		o.addProperty("quantity", quantity);
		o.addProperty("totalGp", totalGp);
		o.addProperty("time", time);
		o.addProperty("status", status);
		if (includeAccount && accountHash != null)
		{
			o.addProperty("accountHash", accountHash);
			if (accountName != null && !accountName.isEmpty())
			{
				o.addProperty("accountName", accountName);
			}
		}
		if (includeGp && gp != null)
		{
			o.addProperty("gp", gp);
		}
		return o;
	}
}
