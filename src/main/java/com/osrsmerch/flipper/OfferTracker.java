package com.osrsmerch.flipper;

import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns Grand Exchange events into messages for the bot. It only reads what RuneLite reports.
 *
 * Per (account, slot) the last saved offer is the base; an event identical to it is ignored; only the increase of quantity and gp is booked. A new offer in
 * the same slot (other side, item, price or total, lower counters, or an active offer after a finished one) gets a new random
 * offer id, never the slot number. Only fills already present in a slot this account never had saved (first run) are a starting
 * point that is not sent. Login replays are matched against the saved slot: equal is ignored, the empty slots the game reports
 * at login are ignored, and fills that happened while logged out count once. Cancelling counts only what was filled.
 */
@Slf4j
public final class OfferTracker
{
	/** What happened with an event; only used by the caller for logging and by tests. */
	public enum Result
	{
		IGNORED_DUPLICATE, IGNORED_EMPTY, BASELINE, NEW_OFFER, UPDATED
	}

	private final FlipperState state;
	private final LongSupplier nowSec;
	private final Supplier<String> idGenerator;

	public OfferTracker(FlipperState state, LongSupplier nowSec)
	{
		this(state, nowSec, OfferTracker::newId);
	}

	/** A random offer id (32 hex characters). */
	static String newId()
	{
		return UUID.randomUUID().toString().replace("-", "");
	}

	OfferTracker(FlipperState state, LongSupplier nowSec, Supplier<String> idGenerator)
	{
		this.state = state;
		this.nowSec = nowSec;
		this.idGenerator = idGenerator;
	}

	/**
	 * @param accountHash RuneLite account hash; accounts are kept strictly apart
	 * @param accountName display name or null
	 * @param gp          gp stack for the optional gp opt-in, or null
	 * @param loginBurst  the event arrived right after login or a world hop (the game replays all slots then)
	 */
	public Result onOffer(long accountHash, String accountName, Long gp, int slot, SavedOffer observed, boolean loginBurst)
	{
		synchronized (state)
		{
			String hash = Long.toString(accountHash);
			AccountState account = state.account(hash);
			if (accountName != null)
			{
				account.name = accountName;
			}
			SlotRecord prev = account.slots.get(slot);

			if (observed.getPhase() == Phase.EMPTY)
			{
				// At login, a hop or while logged out the game reports every slot as empty before the real state. Clearing a
				// known offer then would make the replayed offer look new and book its old fills again, so those are ignored.
				if (loginBurst)
				{
					return Result.IGNORED_EMPTY;
				}
				// The slot was collected or cleared (or is empty the first time we see it): remember it as empty, so the
				// next offer there is a new one whose fills all count.
				if (prev == null || prev.offer.getPhase() != Phase.EMPTY)
				{
					account.slots.put(slot, new SlotRecord(SavedOffer.empty(), null, 0, 0));
					state.save();
				}
				return Result.IGNORED_EMPTY;
			}
			if (prev != null && prev.offer.equals(observed))
			{
				return Result.IGNORED_DUPLICATE;
			}

			// The saved slot is the base, also at login: a replay equal to it is a duplicate (above), fills that grew while
			// we were away are real trades and count once, as the difference with what was saved.
			boolean isNew = prev == null || isNewOffer(prev.offer, observed);
			Result result;
			SlotRecord rec;
			int bookedQuantity;
			long bookedSpent;
			if (isNew)
			{
				// Only a slot never seen before (first run on this account) starts from what is there: those fills are older
				// than the plugin and are not sent. A new offer in a known slot counts from zero.
				boolean baseline = prev == null;
				int baseQuantity = baseline ? observed.getQuantitySold() : 0;
				long baseSpent = baseline ? observed.getSpent() : 0;
				rec = new SlotRecord(observed, idGenerator.get(), baseQuantity, baseSpent);
				bookedQuantity = observed.getQuantitySold() - baseQuantity;
				bookedSpent = observed.getSpent() - baseSpent;
				result = baseline ? Result.BASELINE : Result.NEW_OFFER;
				account.slots.put(slot, rec);
			}
			else
			{
				rec = prev;
				bookedQuantity = observed.getQuantitySold() - prev.offer.getQuantitySold();
				bookedSpent = observed.getSpent() - prev.offer.getSpent();
				rec.offer = observed;
				result = Result.UPDATED;
			}

			if (bookedQuantity > 0)
			{
				if (observed.getSide() == Side.BUY)
				{
					account.costBasis.buy(observed.getItemId(), bookedQuantity, bookedSpent);
				}
				else
				{
					account.costBasis.sell(observed.getItemId(), bookedQuantity);
				}
			}
			emitIfChanged(hash, account, rec, gp);
			state.save();
			return result;
		}
	}

	/** A message is queued when the cumulative fills or the status differ from the last one queued; never for zero fills. */
	private void emitIfChanged(String hash, AccountState account, SlotRecord rec, Long gp)
	{
		int cumQuantity = rec.cumulativeQuantity();
		long cumGp = rec.cumulativeGp();
		String status = rec.offer.getPhase().wire();
		if (cumQuantity <= 0 || cumGp < cumQuantity)
		{
			return;
		}
		if (cumQuantity == rec.sentQuantity && cumGp == rec.sentGp && status.equals(rec.sentStatus))
		{
			return;
		}
		state.enqueue(new OfferMessage(rec.offerId, rec.offer.getItemId(), rec.offer.getSide().wire(), cumQuantity, cumGp, nowSec.getAsLong(), status, hash, account.name, gp));
		rec.sentQuantity = cumQuantity;
		rec.sentGp = cumGp;
		rec.sentStatus = status;
	}

	/**
	 * A new offer in the slot: other side, item, price or total, lower counters, a slot that was empty, or an active offer after a
	 * finished or cancelled one. Not every change of state: active to done and active to cancelled stay the same offer.
	 */
	static boolean isNewOffer(SavedOffer prev, SavedOffer next)
	{
		return prev.getPhase() == Phase.EMPTY
			|| prev.getSide() != next.getSide()
			|| prev.getItemId() != next.getItemId()
			|| prev.getPrice() != next.getPrice()
			|| prev.getTotalQuantity() != next.getTotalQuantity()
			|| prev.getQuantitySold() > next.getQuantitySold()
			|| prev.getSpent() > next.getSpent()
			|| (prev.getPhase().terminal() && next.getPhase() == Phase.ACTIVE);
	}
}
