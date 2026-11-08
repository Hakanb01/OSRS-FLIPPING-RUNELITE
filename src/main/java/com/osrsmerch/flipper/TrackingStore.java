package com.osrsmerch.flipper;

/** Where the whole saved state lives as one JSON string. One key, so slots and queue are always written together. */
public interface TrackingStore
{
	/** The saved JSON, or null if nothing was saved yet. */
	String read();

	void write(String json);
}
