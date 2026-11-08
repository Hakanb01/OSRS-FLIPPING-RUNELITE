package com.osrsmerch.flipper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

final class TestSupport
{
	private TestSupport()
	{
	}

	static final class MemoryStore implements TrackingStore
	{
		String json;
		int writes;

		@Override
		public String read()
		{
			return json;
		}

		@Override
		public void write(String j)
		{
			json = j;
			writes++;
		}
	}

	/** Records every message and answers with the next prepared result (success when none is left). */
	static final class FakeSender implements OfferSender
	{
		final List<OfferMessage> sent = new ArrayList<>();
		final List<SendResult> answers = new ArrayList<>();
		int calls;

		@Override
		public SendResult send(OfferMessage m, SendSettings s)
		{
			calls++;
			SendResult r = answers.isEmpty() ? SendResult.ok() : answers.remove(0);
			if (r.success())
			{
				sent.add(m);
			}
			return r;
		}
	}

	static final class Settings implements SendSettings
	{
		boolean enabled = true;
		String url = "https://bot.example.com";
		String code = "secret-code";
		boolean account;
		boolean gp;

		public boolean enabled()
		{
			return enabled;
		}

		public String baseUrl()
		{
			return url;
		}

		public String code()
		{
			return code;
		}

		public boolean includeAccount()
		{
			return account;
		}

		public boolean includeGp()
		{
			return gp;
		}
	}

	/** A tracker whose offer ids are o1, o2, ... so tests can compare them. */
	static OfferTracker tracker(FlipperState state)
	{
		AtomicInteger n = new AtomicInteger();
		return new OfferTracker(state, () -> 1_700_000_000L, () -> "o" + n.incrementAndGet());
	}

	static SavedOffer buy(Phase p, int item, int sold, int total, long price, long spent)
	{
		return new SavedOffer(Side.BUY, p, item, sold, total, price, spent);
	}

	static SavedOffer sell(Phase p, int item, int sold, int total, long price, long spent)
	{
		return new SavedOffer(Side.SELL, p, item, sold, total, price, spent);
	}

	static AtomicLong clock()
	{
		return new AtomicLong(1_000_000L);
	}
}
