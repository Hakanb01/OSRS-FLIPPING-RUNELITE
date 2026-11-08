package com.osrsmerch.flipper;

import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Sends the queue to the bot, one message per attempt, oldest first, from the thread that calls {@link #tick()} (never the client
 * thread). A success is followed by the next attempt after 5 seconds; a temporary failure (network, 429, 5xx, 404) backs off from
 * 5 up to 45 seconds, or as long as Retry-After says. 401 pauses everything ("link expired"). 400 and 409 are not repeated: the
 * message is parked with the reason. A message is removed only after the bot confirmed it, so a crash resends it; the bot counts
 * the same cumulative fill once.
 */
@Slf4j
public final class OfferOutbox
{
	public static final long SUCCESS_DELAY_MS = 5_000;
	public static final long MAX_BACKOFF_MS = 45_000;
	/** A Retry-After longer than this is shortened; the player can always press retry. */
	public static final long MAX_RETRY_AFTER_MS = 10 * 60_000;
	public static final String EXPIRED_TEXT = "Link expired: do /runelite link again in Discord and paste the new code";

	public enum Status
	{
		OFF, IDLE, WAITING, BACKOFF, LINK_EXPIRED, HELD
	}

	/** What the panel shows. */
	public static final class View
	{
		public final Status status;
		public final int queued;
		public final int parked;
		public final String error;
		public final long retryAtMs;
		public final long lastSuccessMs;
		public final int heldMessages;

		View(Status status, int queued, int parked, String error, long retryAtMs, long lastSuccessMs, int heldMessages)
		{
			this.status = status;
			this.queued = queued;
			this.parked = parked;
			this.error = error;
			this.retryAtMs = retryAtMs;
			this.lastSuccessMs = lastSuccessMs;
			this.heldMessages = heldMessages;
		}
	}

	private final FlipperState state;
	private final OfferSender sender;
	private final SendSettings settings;
	private final LongSupplier nowMs;

	private long nextAttemptMs;
	private int failures;
	private boolean expired;
	private String expiredDestination;
	private String lastError;
	private long lastSuccessMs;
	/** A request is under way (sent outside the lock); a second tick waits for the next one. */
	private boolean sending;
	private final Supplier<String> ids;

	public OfferOutbox(FlipperState state, OfferSender sender, SendSettings settings, LongSupplier nowMs)
	{
		this(state, sender, settings, nowMs, OfferTracker::newId);
	}

	OfferOutbox(FlipperState state, OfferSender sender, SendSettings settings, LongSupplier nowMs, Supplier<String> ids)
	{
		this.state = state;
		this.sender = sender;
		this.settings = settings;
		this.nowMs = nowMs;
		this.ids = ids;
	}

	/** True when sending is switched on and a usable address and code are set. */
	private boolean ready()
	{
		return settings.enabled() && !blank(settings.baseUrl()) && !blank(settings.code());
	}

	private static boolean blank(String s)
	{
		return s == null || s.trim().isEmpty();
	}

	/**
	 * One step; call about once a second. Makes at most one request and none at all when sending is off, the destination
	 * needs the player's decision, the link is expired or it is not yet time.
	 */
	public void tick()
	{
		OfferMessage head;
		String dest;
		// Decide under the lock, send without it: the panel (client thread) reads view() and must never wait for the network.
		synchronized (this)
		{
			if (sending || !ready())
			{
				return;
			}
			dest = Destination.id(settings.baseUrl(), settings.code());
			if (!checkDestination(dest))
			{
				return;
			}
			if (expired)
			{
				if (dest.equals(expiredDestination))
				{
					return;
				}
				expired = false;
				lastError = null;
				failures = 0;
			}
			long now = nowMs.getAsLong();
			if (now < nextAttemptMs)
			{
				return;
			}
			synchronized (state)
			{
				head = state.queue.peek();
			}
			if (head == null)
			{
				return;
			}
			sending = true;
		}
		SendResult r;
		try
		{
			r = sender.send(head, settings);
		}
		finally
		{
			synchronized (this)
			{
				sending = false;
			}
		}
		synchronized (this)
		{
			handle(head, r, dest);
		}
	}

	private void handle(OfferMessage head, SendResult r, String dest)
	{
		long now = nowMs.getAsLong();
		if (r.success())
		{
			state.ack(head);
			failures = 0;
			lastError = null;
			lastSuccessMs = now;
			nextAttemptMs = now + SUCCESS_DELAY_MS;
		}
		else if (r.status == 401)
		{
			expired = true;
			expiredDestination = dest;
			lastError = EXPIRED_TEXT;
		}
		else if (r.status == 400 || r.status == 409 || r.status == 413)
		{
			state.park(head, r.status, r.error == null ? "refused" : r.error, now / 1000);
			lastError = "The bot refused a message (" + r.status + "): " + (r.error == null ? "refused" : r.error);
			nextAttemptMs = now + SUCCESS_DELAY_MS;
		}
		else
		{
			failures++;
			long backoff = Math.min(MAX_BACKOFF_MS, SUCCESS_DELAY_MS << Math.min(failures - 1, 4));
			if (r.retryAfterSec > 0)
			{
				backoff = Math.min(MAX_RETRY_AFTER_MS, r.retryAfterSec * 1000);
			}
			nextAttemptMs = now + backoff;
			lastError = r.status == 0 ? (r.error == null ? "Cannot reach the bot" : r.error)
				: r.status == 404 ? "The bot does not know this address (wrong URL or an older bot)"
				: "The bot answered " + r.status;
		}
	}

	/**
	 * Compares the current link with the one the queue belongs to. A new link with messages waiting puts everything on hold until
	 * {@link #resolveHeld(boolean)}; with an empty queue the new link is simply adopted.
	 *
	 * @return true when sending may continue
	 */
	private boolean checkDestination(String dest)
	{
		synchronized (state)
		{
			if (dest.equals(state.destination))
			{
				if (state.pendingDestination != null)
				{
					state.pendingDestination = null;
					state.save();
				}
				return true;
			}
			if (state.destination == null)
			{
				state.destination = dest;
				state.pendingDestination = null;
				state.save();
				return true;
			}
			if (state.queue.isEmpty())
			{
				// Another link with nothing waiting: it starts from zero, so it never gets fills the old link already had.
				state.switchDestination(dest, true, ids);
				return true;
			}
			if (!dest.equals(state.pendingDestination))
			{
				state.pendingDestination = dest;
				state.save();
			}
			return false;
		}
	}

	/** The player's answer to "link changed, N messages waiting": send them to the new link, or throw them away. */
	public synchronized void resolveHeld(boolean send)
	{
		synchronized (state)
		{
			if (state.pendingDestination == null)
			{
				return;
			}
			state.switchDestination(state.pendingDestination, send, ids);
		}
		expired = false;
		lastError = null;
		failures = 0;
		nextAttemptMs = 0;
	}

	/** The "retry now" button: forget the backoff and the expired mark. */
	public synchronized void retryNow()
	{
		expired = false;
		failures = 0;
		lastError = null;
		nextAttemptMs = 0;
	}

	public synchronized View view()
	{
		int queued;
		int parked;
		boolean held;
		synchronized (state)
		{
			queued = state.queue.size();
			parked = state.parked.size();
			held = state.pendingDestination != null;
		}
		Status s;
		if (!ready())
		{
			s = Status.OFF;
		}
		else if (held)
		{
			s = Status.HELD;
		}
		else if (expired)
		{
			s = Status.LINK_EXPIRED;
		}
		else if (failures > 0)
		{
			s = Status.BACKOFF;
		}
		else if (queued > 0)
		{
			s = Status.WAITING;
		}
		else
		{
			s = Status.IDLE;
		}
		return new View(s, queued, parked, lastError, nextAttemptMs, lastSuccessMs, held ? queued : 0);
	}

	/** For tests: messages waiting, oldest first. */
	List<OfferMessage> queued()
	{
		return state.queueCopy();
	}
}
