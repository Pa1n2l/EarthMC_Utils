// src/client/java/com/paln21/eutil/client/EarthMcApi.java
package com.paln21.eutil.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class EarthMcApi {
	private static final String SERVER_ENDPOINT = "https://api.earthmc.net/v4/";
	private static final int POLL_INTERVAL_SECONDS = 60;

	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.build();

	public record VotePartyStatus(int target, int numRemaining, boolean valid) {
		public static final VotePartyStatus UNKNOWN = new VotePartyStatus(0, 0, false);

		public int votesReceived() {
			return Math.max(0, target - numRemaining);
		}

		public float progress() {
			if (target <= 0) return 0f;
			return Math.max(0f, Math.min(1f, votesReceived() / (float) target));
		}
	}

	private static final AtomicReference<VotePartyStatus> LATEST = new AtomicReference<>(VotePartyStatus.UNKNOWN);
	private static ScheduledExecutorService scheduler;

	public static VotePartyStatus getLatest() {
		return LATEST.get();
	}

	public static synchronized void start() {
		if (scheduler != null) return;
		scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "EarthMC-API-Poller");
			t.setDaemon(true);
			return t;
		});
		scheduler.scheduleWithFixedDelay(EarthMcApi::poll, 0, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
	}

	private static void poll() {
		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(SERVER_ENDPOINT))
					.timeout(Duration.ofSeconds(10))
					.GET()
					.build();

			HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
					.thenAccept(EarthMcApi::handleResponse)
					.exceptionally(ex -> null);
		} catch (Exception ignored) {
		}
	}

	private static void handleResponse(HttpResponse<String> response) {
		try {
			if (response.statusCode() != 200) return;

			JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
			JsonObject voteParty = root.getAsJsonObject("voteParty");
			if (voteParty == null) return;

			int target = voteParty.get("target").getAsInt();
			int numRemaining = voteParty.get("numRemaining").getAsInt();

			LATEST.set(new VotePartyStatus(target, numRemaining, true));
		} catch (Exception ignored) {
		}
	}
}