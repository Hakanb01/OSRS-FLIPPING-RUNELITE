package com.osrsmerch.flipper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import org.junit.Test;

public class OfferMessageJsonTest
{
	private final OfferMessage m = new OfferMessage("abc", 4151, "sell", 3, 3_000_000_000L, 1_700_000_000L, "complete", "123", "Zezima", 5_000_000_000L);

	@Test
	public void defaultBodyHasNoAccountAndNoGp()
	{
		JsonObject o = m.toJson(false, false);
		assertFalse(o.has("accountHash"));
		assertFalse(o.has("accountName"));
		assertFalse(o.has("gp"));
		assertEquals("abc", o.get("offerId").getAsString());
		assertEquals("complete", o.get("status").getAsString());
		assertEquals(3_000_000_000L, o.get("totalGp").getAsLong());
	}

	@Test
	public void accountAndGpAreSeparateOptIns()
	{
		assertTrue(m.toJson(true, false).has("accountHash"));
		assertFalse(m.toJson(true, false).has("gp"));
		assertTrue(m.toJson(false, true).has("gp"));
		assertFalse(m.toJson(false, true).has("accountName"));
	}

	@Test
	public void nameNeverGoesWithoutHash()
	{
		OfferMessage n = new OfferMessage("abc", 1, "buy", 1, 10, 1, "active", null, "Zezima", null);
		assertFalse(n.toJson(true, true).has("accountName"));
	}

	@Test
	public void messagesRoundTripThroughSavedState()
	{
		TestSupport.MemoryStore store = new TestSupport.MemoryStore();
		FlipperState s = new FlipperState(store, new com.google.gson.Gson());
		s.enqueue(m);
		s.save();
		FlipperState again = new FlipperState(store, new com.google.gson.Gson());
		again.load();
		OfferMessage back = again.queueCopy().get(0);
		assertEquals(m.totalGp, back.totalGp);
		assertEquals(m.gp, back.gp);
	}

	@Test
	public void taxHelpers()
	{
		assertEquals(98, Tax.afterTax(4151, 100));
		assertEquals(5_000_000, Tax.perItem(4151, 3_000_000_000L));
		assertEquals(Long.valueOf(25_000_000L), Money.parse("25m"));
		assertEquals(Long.valueOf(1_500L), Money.parse("1.5k"));
	}
}
