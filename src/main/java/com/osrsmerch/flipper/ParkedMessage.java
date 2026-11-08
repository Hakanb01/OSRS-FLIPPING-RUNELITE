package com.osrsmerch.flipper;

/** A message the bot refused (400/409). It is not retried; it stays visible in the panel so the problem can be seen. */
public final class ParkedMessage
{
	final OfferMessage message;
	final int status;
	final String error;
	final long at;

	public ParkedMessage(OfferMessage message, int status, String error, long at)
	{
		this.message = message;
		this.status = status;
		this.error = error;
		this.at = at;
	}

	public OfferMessage getMessage()
	{
		return message;
	}

	public int getStatus()
	{
		return status;
	}

	public String getError()
	{
		return error;
	}

	public long getAt()
	{
		return at;
	}
}
