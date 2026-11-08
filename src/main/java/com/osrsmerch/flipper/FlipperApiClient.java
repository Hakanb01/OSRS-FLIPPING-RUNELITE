package com.osrsmerch.flipper;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * The only class that touches the network. It uses RuneLite's injected OkHttpClient and must be called from a worker thread,
 * never the client thread. The link code goes in the Authorization header and nowhere else: it is not logged and not part of
 * any error text.
 */
@Slf4j
@Singleton
public class FlipperApiClient implements OfferSender
{
	public static final String OFFERS_PATH = "api/runelite/v2/offers";
	public static final String SUMMARY_PATH = "api/runelite/summary";
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	private final OkHttpClient http;
	private final Gson gson;

	@Inject
	public FlipperApiClient(OkHttpClient http, Gson gson)
	{
		this.http = http.newBuilder()
			.callTimeout(15, TimeUnit.SECONDS)
			.followRedirects(false)
			.followSslRedirects(false)
			.build();
		this.gson = gson;
	}

	/** The address with the path added, or null when it is not allowed: https only, plain http only for the own computer. */
	static HttpUrl endpoint(String baseUrl, String path)
	{
		if (baseUrl == null)
		{
			return null;
		}
		HttpUrl base = HttpUrl.parse(baseUrl.trim());
		if (base == null)
		{
			return null;
		}
		String host = base.host();
		boolean loopback = host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]") || host.equals("::1");
		if (!base.isHttps() && !loopback)
		{
			return null;
		}
		HttpUrl.Builder b = base.newBuilder();
		for (String seg : path.split("/"))
		{
			b.addPathSegment(seg);
		}
		return b.build();
	}

	@Override
	public SendResult send(OfferMessage message, SendSettings settings)
	{
		HttpUrl url = endpoint(settings.baseUrl(), OFFERS_PATH);
		if (url == null)
		{
			return new SendResult(0, 0, "The server address must start with https://");
		}
		String body = message.toJson(settings.includeAccount(), settings.includeGp()).toString();
		Request request = new Request.Builder()
			.url(url)
			.header("Authorization", "Bearer " + settings.code().trim())
			.header("User-Agent", "RuneLite-OSRS-Flipping")
			.post(RequestBody.create(JSON, body))
			.build();
		try (Response response = http.newCall(request).execute())
		{
			int code = response.code();
			if (code == 200)
			{
				return SendResult.ok();
			}
			return new SendResult(code, retryAfter(response), errorText(response));
		}
		catch (IOException e)
		{
			log.debug("OSRS Flipping: request failed: {}", e.getClass().getSimpleName());
			return new SendResult(0, 0, "Cannot reach the bot");
		}
	}

	/** Result of a summary request: the data, or the HTTP status and a short reason. */
	public static final class SummaryResult
	{
		public final Summary summary;
		public final int status;
		public final String error;

		SummaryResult(Summary summary, int status, String error)
		{
			this.summary = summary;
			this.status = status;
			this.error = error;
		}
	}

	/** GET /api/runelite/summary. {@code gp} (may be null) is the manual budget for the "best flip" advice. */
	public SummaryResult summary(SendSettings settings, Long gp)
	{
		HttpUrl url = endpoint(settings.baseUrl(), SUMMARY_PATH);
		if (url == null)
		{
			return new SummaryResult(null, 0, "The server address must start with https://");
		}
		if (gp != null && gp > 0)
		{
			url = url.newBuilder().addQueryParameter("gp", Long.toString(gp)).build();
		}
		Request request = new Request.Builder()
			.url(url)
			.header("Authorization", "Bearer " + settings.code().trim())
			.header("User-Agent", "RuneLite-OSRS-Flipping")
			.get()
			.build();
		try (Response response = http.newCall(request).execute())
		{
			if (response.code() != 200)
			{
				return new SummaryResult(null, response.code(), response.code() == 401 ? OfferOutbox.EXPIRED_TEXT : errorText(response));
			}
			String text = response.body() == null ? "" : response.body().string();
			Summary s = gson.fromJson(text, Summary.class);
			return s == null ? new SummaryResult(null, 200, "Empty answer") : new SummaryResult(s, 200, null);
		}
		catch (IOException | JsonSyntaxException e)
		{
			log.debug("OSRS Flipping: summary failed: {}", e.getClass().getSimpleName());
			return new SummaryResult(null, 0, "Cannot reach the bot");
		}
	}

	private static long retryAfter(Response response)
	{
		String h = response.header("Retry-After");
		if (h == null)
		{
			return 0;
		}
		try
		{
			return Math.max(0, Long.parseLong(h.trim()));
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	/** The bot's {"error": "..."} text, cut short; the code never appears in it. */
	private String errorText(Response response)
	{
		try
		{
			if (response.body() == null)
			{
				return null;
			}
			String text = response.body().string();
			com.google.gson.JsonObject o = gson.fromJson(text, com.google.gson.JsonObject.class);
			if (o != null && o.has("error") && o.get("error").isJsonPrimitive())
			{
				String e = o.get("error").getAsString();
				return e.length() > 200 ? e.substring(0, 200) : e;
			}
		}
		catch (IOException | RuntimeException e)
		{
			// fall through: no readable error text
		}
		return null;
	}
}
