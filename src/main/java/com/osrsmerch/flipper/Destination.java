package com.osrsmerch.flipper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * An id for "this bot and this link code", made with SHA-256 so the code itself is never saved in the state. When it changes
 * while messages are waiting, they must not silently go to another member's list.
 */
public final class Destination
{
	private Destination()
	{
	}

	public static String id(String baseUrl, String code)
	{
		try
		{
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] h = md.digest((normalize(baseUrl) + "\0" + code).getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < 8; i++)
			{
				sb.append(String.format("%02x", h[i]));
			}
			return sb.toString();
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new IllegalStateException(e);
		}
	}

	static String normalize(String url)
	{
		String u = url == null ? "" : url.trim();
		while (u.endsWith("/"))
		{
			u = u.substring(0, u.length() - 1);
		}
		return u.toLowerCase();
	}
}
