package com.osrsmerch.flipper;

import java.util.Collections;
import java.util.List;

/** Everything the panel shows, in one object, so the panel is only drawing. Built off the Swing thread, drawn on it. */
public final class PanelModel
{
	final OfferOutbox.View link;
	final boolean sendingEnabled;
	final boolean linkConfigured;
	final List<OfferRow> offers;
	/** Null until the bot answered once. */
	final Summary summary;
	final String summaryError;
	/** The budget typed in the panel, or null. */
	final Long manualBudget;
	final boolean gpOptIn;

	public PanelModel(OfferOutbox.View link, boolean sendingEnabled, boolean linkConfigured, List<OfferRow> offers, Summary summary, String summaryError, Long manualBudget, boolean gpOptIn)
	{
		this.link = link;
		this.sendingEnabled = sendingEnabled;
		this.linkConfigured = linkConfigured;
		this.offers = offers == null ? Collections.emptyList() : offers;
		this.summary = summary;
		this.summaryError = summaryError;
		this.manualBudget = manualBudget;
		this.gpOptIn = gpOptIn;
	}
}
