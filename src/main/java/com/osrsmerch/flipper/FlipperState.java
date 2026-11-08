package com.osrsmerch.flipper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * The saved state of the plugin: per account the slots and cost basis, the queue of messages to send, the parked messages and
 * the destination the queue belongs to. Everything is saved together as one JSON string after each change, so the saved slots
 * and the saved queue always agree; a restart or crash can never keep a slot that moved on while its message was lost, or the
 * other way round. All changes go through the one lock of this object.
 */
@Slf4j
public final class FlipperState
{
	public static final int VERSION = 1;
	public static final int MAX_QUEUE = 2000;
	public static final int MAX_PARKED = 50;

	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	/** The JSON form: plain lists and maps. */
	static final class Snapshot
	{
		int version = VERSION;
		Map<String, AccountState> accounts = new HashMap<>();
		List<OfferMessage> queue = new ArrayList<>();
		List<ParkedMessage> parked = new ArrayList<>();
		String destination;
		String pendingDestination;
		LinkedHashMap<String, Acked> acked = new LinkedHashMap<>();
	}

	/** What the current link already confirmed for one offer id (cumulative). */
	static final class Acked
	{
		int quantity;
		long gp;

		Acked(int quantity, long gp)
		{
			this.quantity = quantity;
			this.gp = gp;
		}
	}

	/** Confirmed counts kept per offer id, newest last; old ids drop off (an offer that old is long finished). */
	static final int MAX_ACKED = 2000;

	private final TrackingStore store;
	final Map<String, AccountState> accounts = new HashMap<>();
	/** The queue of messages to send, oldest first. Only the outbox sends from it. */
	final ConcurrentLinkedQueue<OfferMessage> queue = new ConcurrentLinkedQueue<>();
	final List<ParkedMessage> parked = new ArrayList<>();
	/** Id of the bot link the queue belongs to (never the code itself). */
	String destination;
	/** A new link was set while messages were waiting: sending is on hold until the player decides. */
	String pendingDestination;
	/** Per offer id: the cumulative counts the current link confirmed. Needed to start a new link from zero. */
	final LinkedHashMap<String, Acked> acked = new LinkedHashMap<>();

	public FlipperState(TrackingStore store)
	{
		this.store = store;
	}

	/** Reads the saved state; unreadable data starts empty (and is not overwritten until the next change). */
	public synchronized void load()
	{
		String json = store.read();
		if (json == null || json.isEmpty())
		{
			return;
		}
		try
		{
			Snapshot s = GSON.fromJson(json, Snapshot.class);
			if (s == null || s.version != VERSION)
			{
				log.warn("OSRS Flipping: saved state has an unknown version, starting empty");
				return;
			}
			accounts.clear();
			if (s.accounts != null)
			{
				accounts.putAll(s.accounts);
			}
			queue.clear();
			if (s.queue != null)
			{
				queue.addAll(s.queue);
			}
			parked.clear();
			if (s.parked != null)
			{
				parked.addAll(s.parked);
			}
			destination = s.destination;
			pendingDestination = s.pendingDestination;
			acked.clear();
			if (s.acked != null)
			{
				acked.putAll(s.acked);
			}
		}
		catch (RuntimeException e)
		{
			log.warn("OSRS Flipping: saved state could not be read, starting empty");
		}
	}

	/** Writes everything. Call after each change, while holding no other lock. */
	public synchronized void save()
	{
		Snapshot s = new Snapshot();
		s.accounts = new HashMap<>(accounts);
		s.queue = new ArrayList<>(queue);
		s.parked = new ArrayList<>(parked);
		s.destination = destination;
		s.pendingDestination = pendingDestination;
		s.acked = new LinkedHashMap<>(acked);
		store.write(GSON.toJson(s));
	}

	public synchronized AccountState account(String accountHash)
	{
		return accounts.computeIfAbsent(accountHash, k -> new AccountState());
	}

	public synchronized AccountState accountOrNull(String accountHash)
	{
		return accounts.get(accountHash);
	}

	public synchronized int queueSize()
	{
		return queue.size();
	}

	public synchronized List<OfferMessage> queueCopy()
	{
		return new ArrayList<>(queue);
	}

	public synchronized List<ParkedMessage> parkedCopy()
	{
		return new ArrayList<>(parked);
	}

	/** Adds a message; a full queue only drops messages that a later message for the same offer replaces. */
	synchronized void enqueue(OfferMessage m)
	{
		queue.add(m);
		// Over the limit: only drop messages that a later cumulative message for the same offer replaces. Unconfirmed trades of
		// other offers are never dropped, so nothing is lost; the queue may then stay above MAX_QUEUE.
		while (queue.size() > MAX_QUEUE)
		{
			OfferMessage drop = null;
			for (OfferMessage q : queue)
			{
				if (q != m && laterFor(q))
				{
					drop = q;
					break;
				}
			}
			if (drop == null)
			{
				break;
			}
			queue.remove(drop);
		}
	}

	private boolean laterFor(OfferMessage q)
	{
		boolean seen = false;
		for (OfferMessage x : queue)
		{
			if (x == q)
			{
				seen = true;
			}
			else if (seen && x.offerId.equals(q.offerId))
			{
				return true;
			}
		}
		return false;
	}

	/** Removes the head after the bot confirmed it, unless the head changed meanwhile. */
	synchronized boolean ack(OfferMessage m)
	{
		boolean removed = queue.peek() == m && queue.remove(m);
		if (removed)
		{
			acked.remove(m.offerId);
			acked.put(m.offerId, new Acked(m.quantity, m.totalGp));
			while (acked.size() > MAX_ACKED)
			{
				acked.remove(acked.keySet().iterator().next());
			}
			save();
		}
		return removed;
	}

	/**
	 * Moves everything to another bot link so that link starts from zero: every offer gets a new id, the fills the old link
	 * already confirmed become part of the baseline (never sent again), and waiting messages either go to the new link without
	 * those confirmed fills ({@code keepQueued}) or are dropped together with their fills.
	 */
	synchronized void switchDestination(String dest, boolean keepQueued, Supplier<String> ids)
	{
		Map<String, String> newIds = new HashMap<>();
		for (AccountState a : accounts.values())
		{
			for (SlotRecord rec : a.slots.values())
			{
				if (rec.offerId == null)
				{
					continue;
				}
				Acked done = acked.get(rec.offerId);
				int aq = done == null ? 0 : done.quantity;
				long ag = done == null ? 0 : done.gp;
				// Without keeping the queue, the waiting fills are given up as well: the baseline moves to what was queued.
				int moveQ = keepQueued ? aq : Math.max(aq, rec.sentQuantity);
				long moveG = keepQueued ? ag : Math.max(ag, rec.sentGp);
				rec.baseQuantity += moveQ;
				rec.baseSpent += moveG;
				rec.sentQuantity = Math.max(0, rec.sentQuantity - moveQ);
				rec.sentGp = Math.max(0, rec.sentGp - moveG);
				if (rec.sentQuantity == 0)
				{
					rec.sentStatus = null;
				}
				rec.offerId = newIds.computeIfAbsent(rec.offerId, k -> ids.get());
			}
		}
		List<OfferMessage> keep = new ArrayList<>();
		if (keepQueued)
		{
			for (OfferMessage m : queue)
			{
				Acked done = acked.get(m.offerId);
				int q = m.quantity - (done == null ? 0 : done.quantity);
				long g = m.totalGp - (done == null ? 0 : done.gp);
				if (q > 0 && g >= q)
				{
					keep.add(new OfferMessage(newIds.computeIfAbsent(m.offerId, k -> ids.get()), m.itemId, m.type, q, g, m.time, m.status, m.accountHash, m.accountName, m.gp));
				}
			}
		}
		queue.clear();
		queue.addAll(keep);
		acked.clear();
		destination = dest;
		pendingDestination = null;
		save();
	}

	/** The bot refused this message for good: remove it from the queue and keep it, with the reason, in the parked list. */
	synchronized void park(OfferMessage m, int status, String error, long nowSec)
	{
		if (queue.peek() == m)
		{
			queue.remove(m);
		}
		parked.add(new ParkedMessage(m, status, error, nowSec));
		while (parked.size() > MAX_PARKED)
		{
			parked.remove(0);
		}
		save();
	}

	synchronized void clearParked()
	{
		parked.clear();
		save();
	}

	/** Parsed copy of the whole state, for tests. */
	static Snapshot parse(String json)
	{
		return GSON.fromJson(json, Snapshot.class);
	}

	static Gson gson()
	{
		return GSON;
	}
}
