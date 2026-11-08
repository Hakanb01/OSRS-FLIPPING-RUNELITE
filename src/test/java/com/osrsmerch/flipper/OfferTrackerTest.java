package com.osrsmerch.flipper;

import static com.osrsmerch.flipper.TestSupport.buy;
import static com.osrsmerch.flipper.TestSupport.sell;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.osrsmerch.flipper.TestSupport.MemoryStore;
import java.util.List;
import org.junit.Test;

public class OfferTrackerTest
{
	private final MemoryStore store = new MemoryStore();
	private final FlipperState state = new FlipperState(store, new com.google.gson.Gson());
	private final OfferTracker t = TestSupport.tracker(state);

	private List<OfferMessage> queue()
	{
		return state.queueCopy();
	}

	@Test
	public void newOfferWithNothingFilledQueuesNothing()
	{
		OfferTracker.Result r = t.onOffer(1, null, null, 0, buy(Phase.ACTIVE, 4151, 0, 10, 1000, 0), false);
		assertTrue(queue().isEmpty());
		assertEquals(OfferTracker.Result.BASELINE, r);
	}

	@Test
	public void partialThenCompleteSendsCumulativeWithSameId()
	{
		t.onOffer(1, null, null, 0, buy(Phase.ACTIVE, 4151, 0, 10, 1000, 0), false);
		t.onOffer(1, null, null, 0, buy(Phase.ACTIVE, 4151, 4, 10, 1000, 3990), false);
		t.onOffer(1, null, null, 0, buy(Phase.DONE, 4151, 10, 10, 1000, 9990), false);
		List<OfferMessage> q = queue();
		assertEquals(2, q.size());
		assertEquals(4, q.get(0).quantity);
		assertEquals(3990, q.get(0).totalGp);
		assertEquals("active", q.get(0).status);
		assertEquals(10, q.get(1).quantity);
		assertEquals(9990, q.get(1).totalGp);
		assertEquals("complete", q.get(1).status);
		assertEquals(q.get(0).offerId, q.get(1).offerId);
		assertNotEquals("0", q.get(0).offerId);
	}

	@Test
	public void identicalEventIsIgnored()
	{
		SavedOffer o = buy(Phase.ACTIVE, 4151, 4, 10, 1000, 3990);
		t.onOffer(1, null, null, 0, buy(Phase.ACTIVE, 4151, 0, 10, 1000, 0), false);
		t.onOffer(1, null, null, 0, o, false);
		OfferTracker.Result again = t.onOffer(1, null, null, 0, o, false);
		assertEquals(OfferTracker.Result.IGNORED_DUPLICATE, again);
		assertEquals(1, queue().size());
	}

	@Test
	public void cancelWithNothingFilledQueuesNothing()
	{
		t.onOffer(1, null, null, 2, sell(Phase.ACTIVE, 4151, 0, 5, 2000, 0), false);
		t.onOffer(1, null, null, 2, sell(Phase.CANCELLED, 4151, 0, 5, 2000, 0), false);
		assertTrue(queue().isEmpty());
	}

	@Test
	public void cancelAfterPartialFillKeepsOnlyFilledPartAndSameId()
	{
		t.onOffer(1, null, null, 2, sell(Phase.ACTIVE, 4151, 0, 5, 2000, 0), false);
		t.onOffer(1, null, null, 2, sell(Phase.ACTIVE, 4151, 2, 5, 2000, 4000), false);
		t.onOffer(1, null, null, 2, sell(Phase.CANCELLED, 4151, 2, 5, 2000, 4000), false);
		List<OfferMessage> q = queue();
		assertEquals(2, q.size());
		assertEquals("cancelled", q.get(1).status);
		assertEquals(2, q.get(1).quantity);
		assertEquals(4000, q.get(1).totalGp);
		assertEquals(q.get(0).offerId, q.get(1).offerId);
	}

	@Test
	public void reusedSlotGetsNewOfferId()
	{
		t.onOffer(1, null, null, 0, buy(Phase.ACTIVE, 4151, 0, 10, 1000, 0), false);
		t.onOffer(1, null, null, 0, buy(Phase.DONE, 4151, 10, 10, 1000, 10000), false);
		// slot emptied (collected), then the same offer is placed again
		t.onOffer(1, null, null, 0, SavedOffer.empty(), false);
		t.onOffer(1, null, null, 0, buy(Phase.ACTIVE, 4151, 0, 10, 1000, 0), false);
		t.onOffer(1, null, null, 0, buy(Phase.ACTIVE, 4151, 3, 10, 1000, 3000), false);
		List<OfferMessage> q = queue();
		assertEquals(2, q.size());
		assertNotEquals(q.get(0).offerId, q.get(1).offerId);
		assertEquals(3, q.get(1).quantity);
	}

	@Test
	public void lowerCountersInTheSlotMeanANewOffer()
	{
		t.onOffer(1, null, null, 0, buy(Phase.ACTIVE, 4151, 5, 10, 1000, 5000), false);
		t.onOffer(1, null, null, 0, buy(Phase.ACTIVE, 4151, 2, 10, 1000, 2000), false);
		List<OfferMessage> q = queue();
		assertEquals(1, q.size());
		assertEquals("o2", q.get(0).offerId);
		assertEquals(2, q.get(0).quantity);
	}

	@Test
	public void otherSideItemPriceOrTotalMeanANewOffer()
	{
		assertTrue(OfferTracker.isNewOffer(buy(Phase.ACTIVE, 1, 5, 10, 1000, 5000), sell(Phase.ACTIVE, 1, 5, 10, 1000, 5000)));
		assertTrue(OfferTracker.isNewOffer(buy(Phase.ACTIVE, 1, 5, 10, 1000, 5000), buy(Phase.ACTIVE, 2, 5, 10, 1000, 5000)));
		assertTrue(OfferTracker.isNewOffer(buy(Phase.ACTIVE, 1, 5, 10, 1000, 5000), buy(Phase.ACTIVE, 1, 5, 10, 1100, 5000)));
		assertTrue(OfferTracker.isNewOffer(buy(Phase.ACTIVE, 1, 5, 10, 1000, 5000), buy(Phase.ACTIVE, 1, 5, 11, 1000, 5000)));
		assertFalse(OfferTracker.isNewOffer(buy(Phase.ACTIVE, 1, 5, 10, 1000, 5000), buy(Phase.DONE, 1, 10, 10, 1000, 10000)));
		assertFalse(OfferTracker.isNewOffer(buy(Phase.ACTIVE, 1, 5, 10, 1000, 5000), buy(Phase.CANCELLED, 1, 5, 10, 1000, 5000)));
	}

	@Test
	public void loginReplayIsNeverBookedAsATrade()
	{
		// first login: slot already holds 4 of 10
		t.onOffer(1, null, null, 3, buy(Phase.ACTIVE, 4151, 4, 10, 1000, 3990), true);
		assertTrue(queue().isEmpty());
		// the same offer fills further: only the increase is booked, cumulative in the message
		t.onOffer(1, null, null, 3, buy(Phase.ACTIVE, 4151, 7, 10, 1000, 6990), false);
		List<OfferMessage> q = queue();
		assertEquals(1, q.size());
		assertEquals(3, q.get(0).quantity);
		assertEquals(3000, q.get(0).totalGp);
	}

	@Test
	public void fillsWhileLoggedOutCountOnceAtLogin()
	{
		t.onOffer(1, null, null, 3, buy(Phase.ACTIVE, 4151, 0, 10, 1000, 0), false);
		t.onOffer(1, null, null, 3, buy(Phase.ACTIVE, 4151, 2, 10, 1000, 2000), false);
		assertEquals(1, queue().size());
		// logout: the game reports the slot empty, then at login empty again and the real offer (6 of 10 now)
		t.onOffer(1, null, null, 3, SavedOffer.empty(), true);
		t.onOffer(1, null, null, 3, SavedOffer.empty(), true);
		t.onOffer(1, null, null, 3, buy(Phase.ACTIVE, 4151, 6, 10, 1000, 6000), true);
		// the 4 filled while away are real trades: same offer id, cumulative 6, nothing counted twice
		List<OfferMessage> q = queue();
		assertEquals(2, q.size());
		assertEquals(q.get(0).offerId, q.get(1).offerId);
		assertEquals(6, q.get(1).quantity);
		assertEquals(6000, q.get(1).totalGp);
		assertEquals(6, state.account("1").costBasis.heldQuantity(4151));
		// the same replay again (another login) books nothing
		t.onOffer(1, null, null, 3, buy(Phase.ACTIVE, 4151, 6, 10, 1000, 6000), true);
		assertEquals(2, queue().size());
	}

	@Test
	public void emptySlotSeenFirstThenAnInstantFillCountsInFull()
	{
		// slot 5 is empty when we first look (after the login burst); the next offer fills at once
		t.onOffer(1, null, null, 5, SavedOffer.empty(), false);
		t.onOffer(1, null, null, 5, buy(Phase.DONE, 4151, 10, 10, 1000, 9800), false);
		List<OfferMessage> q = queue();
		assertEquals(1, q.size());
		assertEquals(10, q.get(0).quantity);
		assertEquals(9800, q.get(0).totalGp);
	}

	@Test
	public void emptyReportsDuringLoginNeverResetAKnownOffer()
	{
		t.onOffer(1, null, null, 1, buy(Phase.ACTIVE, 4151, 0, 10, 1000, 0), false);
		t.onOffer(1, null, null, 1, buy(Phase.DONE, 4151, 10, 10, 1000, 10000), false);
		t.onOffer(1, null, null, 1, SavedOffer.empty(), true);
		t.onOffer(1, null, null, 1, buy(Phase.DONE, 4151, 10, 10, 1000, 10000), true);
		assertEquals(1, queue().size());
		assertEquals(10, state.account("1").costBasis.heldQuantity(4151));
	}

	@Test
	public void newLinkStartsFromZeroAndKeepsOnlyUnconfirmedFills()
	{
		t.onOffer(1, null, null, 0, buy(Phase.ACTIVE, 4151, 0, 10, 1000, 0), false);
		t.onOffer(1, null, null, 0, buy(Phase.ACTIVE, 4151, 4, 10, 1000, 4000), false);
		// link A confirmed the 4
		state.destination = "A";
		assertTrue(state.ack(queue().get(0)));
		// 2 more fill and wait in the queue (cumulative 6)
		t.onOffer(1, null, null, 0, buy(Phase.ACTIVE, 4151, 6, 10, 1000, 6000), false);
		state.switchDestination("B", true, () -> "new");
		List<OfferMessage> q = queue();
		assertEquals(1, q.size());
		assertEquals("new", q.get(0).offerId);
		assertEquals(2, q.get(0).quantity);
		assertEquals(2000, q.get(0).totalGp);
		// the next fill for B is cumulative from B's zero: 7 filled - 4 at A = 3
		t.onOffer(1, null, null, 0, buy(Phase.ACTIVE, 4151, 7, 10, 1000, 7000), false);
		q = queue();
		assertEquals(3, q.get(q.size() - 1).quantity);
		assertEquals("new", q.get(q.size() - 1).offerId);
		assertEquals("B", state.destination);
	}

	@Test
	public void newLinkWithoutKeepingDropsWaitingFillsForGood()
	{
		t.onOffer(1, null, null, 0, buy(Phase.ACTIVE, 4151, 0, 10, 1000, 0), false);
		t.onOffer(1, null, null, 0, buy(Phase.ACTIVE, 4151, 4, 10, 1000, 4000), false);
		state.destination = "A";
		state.switchDestination("B", false, () -> "new");
		assertTrue(queue().isEmpty());
		t.onOffer(1, null, null, 0, buy(Phase.ACTIVE, 4151, 5, 10, 1000, 5000), false);
		assertEquals(1, queue().get(0).quantity);
	}

	@Test
	public void fullQueueNeverDropsUnconfirmedTradesOfOtherOffers()
	{
		int n = FlipperState.MAX_QUEUE + 50;
		for (int i = 0; i < n; i++)
		{
			t.onOffer(1, null, null, 0, buy(Phase.ACTIVE, 1000 + i, 0, 10, 100, 0), false);
			t.onOffer(1, null, null, 0, buy(Phase.ACTIVE, 1000 + i, 1, 10, 100, 100), false);
		}
		assertEquals(n, queue().size());
		assertEquals(1000, queue().get(0).itemId);
	}

	@Test
	public void loginReplayOfFinishedOfferBooksNothingEvenAfterRestart()
	{
		t.onOffer(1, null, null, 1, sell(Phase.DONE, 4151, 5, 5, 2000, 10000), true);
		FlipperState again = new FlipperState(store, new com.google.gson.Gson());
		again.load();
		OfferTracker t2 = TestSupport.tracker(again);
		t2.onOffer(1, null, null, 1, sell(Phase.DONE, 4151, 5, 5, 2000, 10000), true);
		assertTrue(again.queueCopy().isEmpty());
	}

	@Test
	public void restartKeepsBaseAndOfferId()
	{
		t.onOffer(1, null, null, 0, buy(Phase.ACTIVE, 4151, 0, 10, 1000, 0), false);
		t.onOffer(1, null, null, 0, buy(Phase.ACTIVE, 4151, 4, 10, 1000, 3990), false);
		String id = queue().get(0).offerId;
		FlipperState again = new FlipperState(store, new com.google.gson.Gson());
		again.load();
		OfferTracker t2 = TestSupport.tracker(again);
		// login replay of the same state after restart: nothing new
		t2.onOffer(1, null, null, 0, buy(Phase.ACTIVE, 4151, 4, 10, 1000, 3990), true);
		assertEquals(1, again.queueCopy().size());
		t2.onOffer(1, null, null, 0, buy(Phase.DONE, 4151, 10, 10, 1000, 9990), false);
		List<OfferMessage> q = again.queueCopy();
		assertEquals(2, q.size());
		assertEquals(id, q.get(1).offerId);
		assertEquals(10, q.get(1).quantity);
	}

	@Test
	public void twoAccountsStayApart()
	{
		t.onOffer(1, "One", null, 0, buy(Phase.ACTIVE, 4151, 0, 10, 1000, 0), false);
		t.onOffer(2, "Two", null, 0, buy(Phase.ACTIVE, 4151, 0, 10, 1000, 0), false);
		t.onOffer(1, "One", null, 0, buy(Phase.ACTIVE, 4151, 2, 10, 1000, 2000), false);
		t.onOffer(2, "Two", null, 0, buy(Phase.ACTIVE, 4151, 6, 10, 1000, 6000), false);
		List<OfferMessage> q = queue();
		assertEquals(2, q.size());
		assertEquals("1", q.get(0).accountHash);
		assertEquals("2", q.get(1).accountHash);
		assertNotEquals(q.get(0).offerId, q.get(1).offerId);
		assertEquals(2, q.get(0).quantity);
		assertEquals(6, q.get(1).quantity);
		assertEquals(2, state.accountOrNull("1").getCostBasis().heldQuantity(4151));
		assertEquals(6, state.accountOrNull("2").getCostBasis().heldQuantity(4151));
	}

	@Test
	public void costBasisFollowsBuysAndSells()
	{
		t.onOffer(1, null, null, 0, buy(Phase.ACTIVE, 4151, 0, 10, 1000, 0), false);
		t.onOffer(1, null, null, 0, buy(Phase.DONE, 4151, 10, 10, 1000, 10000), false);
		assertEquals(1000, state.accountOrNull("1").getCostBasis().averageCost(4151).getAsLong());
		t.onOffer(1, null, null, 1, sell(Phase.ACTIVE, 4151, 0, 10, 1200, 0), false);
		t.onOffer(1, null, null, 1, sell(Phase.DONE, 4151, 10, 10, 1200, 12000), false);
		assertTrue(!state.accountOrNull("1").getCostBasis().averageCost(4151).isPresent());
	}

	@Test
	public void sellSendsGrossAmountAsReportedWithoutTaxChange()
	{
		t.onOffer(1, null, null, 1, sell(Phase.ACTIVE, 4151, 0, 10, 1200, 0), false);
		t.onOffer(1, null, null, 1, sell(Phase.DONE, 4151, 10, 10, 1200, 12000), false);
		assertEquals(12000, queue().get(0).totalGp);
	}

	@Test
	public void emptySlotEventDoesNotQueueAnything()
	{
		OfferTracker.Result r = t.onOffer(1, null, null, 5, SavedOffer.empty(), false);
		assertEquals(OfferTracker.Result.IGNORED_EMPTY, r);
		assertTrue(queue().isEmpty());
	}

	@Test
	public void savedStateAndQueueAreWrittenTogether()
	{
		t.onOffer(1, null, null, 0, buy(Phase.ACTIVE, 4151, 0, 10, 1000, 0), false);
		t.onOffer(1, null, null, 0, buy(Phase.ACTIVE, 4151, 4, 10, 1000, 3990), false);
		FlipperState.Snapshot s = state.parse(store.json);
		assertEquals(1, s.queue.size());
		assertEquals(4, s.accounts.get("1").slots.get(0).sentQuantity);
	}
}
