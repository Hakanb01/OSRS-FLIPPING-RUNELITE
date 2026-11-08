package com.osrsmerch.flipper;

/** The outcome of one request to the bot. */
public final class SendResult
{
	/** HTTP status, or 0 for a network failure or a refused address. */
	final int status;
	/** Seconds from a Retry-After header, or 0. */
	final long retryAfterSec;
	/** Short text from the bot or a local reason; never contains the code. */
	final String error;

	public SendResult(int status, long retryAfterSec, String error)
	{
		this.status = status;
		this.retryAfterSec = retryAfterSec;
		this.error = error;
	}

	public static SendResult ok()
	{
		return new SendResult(200, 0, null);
	}

	public boolean success()
	{
		return status == 200;
	}

	public int getStatus()
	{
		return status;
	}

	public long getRetryAfterSec()
	{
		return retryAfterSec;
	}

	public String getError()
	{
		return error;
	}
}
