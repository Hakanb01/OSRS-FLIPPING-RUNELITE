package com.osrsmerch.flipper;

/** Sends one message to the bot. The real one uses RuneLite's OkHttpClient; tests use a fake. */
public interface OfferSender
{
	SendResult send(OfferMessage message, SendSettings settings);
}
