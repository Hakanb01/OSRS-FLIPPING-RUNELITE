package com.osrsmerch.flipper;

/** What the outbox needs from the config; read again at every attempt, so a change applies at once. */
public interface SendSettings
{
	/** The master switch. Off means no network request of any kind. */
	boolean enabled();

	/** Address of the bot, for example https://bot.example.com . */
	String baseUrl();

	/** The personal link code. A secret: never logged. */
	String code();

	/** Opt-in: send the account hash and name with each message. */
	boolean includeAccount();

	/** Opt-in: send the gp stack with each message. */
	boolean includeGp();
}
