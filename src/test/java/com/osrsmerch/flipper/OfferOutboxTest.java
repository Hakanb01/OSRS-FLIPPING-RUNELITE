package com.osrsmerch.flipper;

import static com.osrsmerch.flipper.TestSupport.buy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.osrsmerch.flipper.TestSupport.FakeSender;
import com.osrsmerch.flipper.TestSupport.MemoryStore;
import com.osrsmerch.flipper.TestSupport.Settings;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

public class OfferOutboxTest
{
	private final MemoryStore store = new MemoryStore();
	private final FlipperState state = new FlipperState(store);
	private final FakeSender sender = new FakeSender();
	private final Settings settings = new Settings();
	private final AtomicLong now = TestSupport.clock();
	private final OfferOutbox outbox = new OfferOutbox(state, sender, settings, now::get);
	private final OfferTracker tracker = TestSupport.tracker(state);

	private void fill(int sold)
	{
		tracker.onOffer(1, null, null, 0, buy(Phase.ACTIVE, 4151, 0, 10, 1000, 0), false);
		tracker.onOffer(1, null, null, 0, buy(Phase.ACTIVE, 4151, sold, 10, 1000, sold * 1000L), false);
	}

	@Test
	public void sendsOldestFirstAndWaitsFiveSecondsAfterSuccess()
	{
		fill(2);
		tracker.onOffer(1, null, null, 0, buy(Phase.ACTIVE, 4151, 5, 10, 1000, 5000), false);
		outbox.tick();
		assertEquals(1, sender.sent.size());
		assertEquals(2, sender.sent.get(0).quantity);
		outbox.tick();
		assertEquals(1, sender.calls);
		now.addAndGet(5_000);
		outbox.tick();
		assertEquals(2, sender.sent.size());
		assertEquals(5, sender.sent.get(1).quantity);
		assertEquals(0, state.queueSize());
	}

	@Test
	public void thePanelNeverWaitsForTheNetwork() throws Exception
	{
		fill(2);
		final boolean[] viewDone = {false};
		// A slow request: while it is under way another thread (the panel) reads the status; that must not block.
		OfferSender slow = (m, s) ->
		{
			Thread t = new Thread(() ->
			{
				outbox2Holder[0].view();
				viewDone[0] = true;
			});
			t.start();
			try
			{
				t.join(2_000);
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
			}
			return SendResult.ok();
		};
		OfferOutbox o = new OfferOutbox(state, slow, settings, now::get);
		outbox2Holder[0] = o;
		o.tick();
		assertTrue(viewDone[0]);
		assertEquals(0, state.queueSize());
	}

	private final OfferOutbox[] outbox2Holder = new OfferOutbox[1];

	@Test
	public void nothingIsSentWhenSendingIsOff()
	{
		settings.enabled = false;
		fill(2);
		outbox.tick();
		assertEquals(0, sender.calls);
		assertEquals(OfferOutbox.Status.OFF, outbox.view().status);
		assertEquals(1, state.queueSize());
	}

	@Test
	public void nothingIsSentWithoutCode()
	{
		settings.code = "";
		fill(2);
		outbox.tick();
		assertEquals(0, sender.calls);
	}

	@Test
	public void temporaryErrorBacksOffUpToFortyFiveSecondsAndKeepsMessage()
	{
		fill(2);
		for (int i = 0; i < 8; i++)
		{
			sender.answers.add(new SendResult(500, 0, null));
		}
		long previousWait = 0;
		for (int i = 0; i < 8; i++)
		{
			outbox.tick();
			long wait = outbox.view().retryAtMs - now.get();
			assertTrue(wait >= previousWait && wait <= OfferOutbox.MAX_BACKOFF_MS);
			previousWait = wait;
			now.addAndGet(wait);
		}
		assertEquals(OfferOutbox.MAX_BACKOFF_MS, previousWait);
		assertEquals(1, state.queueSize());
		outbox.tick();
		assertEquals(0, state.queueSize());
	}

	@Test
	public void retryAfterHeaderWins()
	{
		fill(2);
		sender.answers.add(new SendResult(429, 30, null));
		outbox.tick();
		assertEquals(30_000, outbox.view().retryAtMs - now.get());
	}

	@Test
	public void unauthorizedPausesWithMessageUntilNewLink()
	{
		fill(2);
		sender.answers.add(new SendResult(401, 0, null));
		outbox.tick();
		OfferOutbox.View v = outbox.view();
		assertEquals(OfferOutbox.Status.LINK_EXPIRED, v.status);
		assertTrue(v.error.contains("/runelite link"));
		int calls = sender.calls;
		now.addAndGet(120_000);
		outbox.tick();
		assertEquals(calls, sender.calls);
		settings.code = "new-code";
		outbox.tick();
		assertEquals(calls, sender.calls);
		assertEquals(OfferOutbox.Status.HELD, outbox.view().status);
		outbox.resolveHeld(true);
		now.addAndGet(5_000);
		outbox.tick();
		assertEquals(0, state.queueSize());
	}

	@Test
	public void badRequestIsParkedNotRepeated()
	{
		fill(2);
		sender.answers.add(new SendResult(409, 0, "amount went down"));
		outbox.tick();
		assertEquals(0, state.queueSize());
		assertEquals(1, state.parkedCopy().size());
		assertEquals(1, outbox.view().parked);
		now.addAndGet(60_000);
		outbox.tick();
		assertEquals(1, sender.calls);
	}

	@Test
	public void queueSurvivesRestartAndIsNotLostOrCountedTwice()
	{
		fill(2);
		sender.answers.add(new SendResult(0, 0, null));
		outbox.tick();
		FlipperState again = new FlipperState(store);
		again.load();
		assertEquals(1, again.queueSize());
		FakeSender s2 = new FakeSender();
		OfferOutbox o2 = new OfferOutbox(again, s2, settings, now::get);
		now.addAndGet(60_000);
		o2.tick();
		assertEquals(1, s2.sent.size());
		assertEquals(2, s2.sent.get(0).quantity);
		FlipperState third = new FlipperState(store);
		third.load();
		assertEquals(0, third.queueSize());
	}

	@Test
	public void newLinkWithWaitingMessagesDoesNotSilentlyGoToAnotherMember()
	{
		fill(2);
		sender.answers.add(new SendResult(500, 0, null));
		outbox.tick();
		settings.code = "someone-elses-code";
		now.addAndGet(60_000);
		int calls = sender.calls;
		outbox.tick();
		assertEquals(calls, sender.calls);
		outbox.resolveHeld(false);
		assertEquals(0, state.queueSize());
	}

	@Test
	public void codeNeverAppearsInSavedState()
	{
		fill(2);
		outbox.tick();
		assertFalse(store.json.contains(settings.code));
	}
}
