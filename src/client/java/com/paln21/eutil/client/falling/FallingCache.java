// src/client/java/com/paln21/eutil/client/falling/FallingCache.java
package com.paln21.eutil.client.falling;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class FallingCache {

	private static final String TOWNS_API_URL = "https://api.earthmc.net/v4/towns";
	private static final String PLAYERS_API_URL = "https://api.earthmc.net/v4/players";

	private static final Path CACHE_FILE_PATH = FabricLoader.getInstance()
			.getConfigDir().resolve("eutil").resolve("falling_cache.json");

	private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
	private static final long DAY_MS = 24L * 60 * 60 * 1000;
	private static final int GENERATION_HOUR = 19;
	private static final int GENERATION_MINUTE = 1; // 19:00ジャストではなくAPI負荷を避けて1分後

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	private static volatile List<FallingTown> cachedFallingTowns = new ArrayList<>();
	private static volatile boolean building = false;
	private static ScheduledExecutorService scheduler;

	private FallingCache() {
	}

	/**
	 * キーが存在しないか、値が JsonNull（明示的な null）の場合でも
	 * ClassCastException を起こさずに null を返す安全な getAsJsonObject。
	 */
	private static JsonObject getObj(JsonObject parent, String key) {
		if (parent == null) return null;
		JsonElement el = parent.get(key);
		return (el != null && el.isJsonObject()) ? el.getAsJsonObject() : null;
	}

	/**
	 * キーが存在しないか値が JsonNull の場合は def を返す、値取得用の安全なヘルパー群。
	 * Gson は JsonNull に対して getAsString() 等を呼ぶと UnsupportedOperationException
	 * (メッセージが単に "JsonNull" になる) を投げるため、has() チェックだけでは不十分。
	 */
	private static JsonElement safeEl(JsonObject obj, String key) {
		if (obj == null) return null;
		JsonElement el = obj.get(key);
		return (el != null && !el.isJsonNull()) ? el : null;
	}

	private static String getStr(JsonObject obj, String key, String def) {
		JsonElement el = safeEl(obj, key);
		return el != null ? el.getAsString() : def;
	}

	private static long getLong(JsonObject obj, String key, long def) {
		JsonElement el = safeEl(obj, key);
		return el != null ? el.getAsLong() : def;
	}

	private static int getInt(JsonObject obj, String key, int def) {
		JsonElement el = safeEl(obj, key);
		return el != null ? el.getAsInt() : def;
	}

	private static double getDouble(JsonObject obj, String key, double def) {
		JsonElement el = safeEl(obj, key);
		return el != null ? el.getAsDouble() : def;
	}

	private static boolean getBool(JsonObject obj, String key, boolean def) {
		JsonElement el = safeEl(obj, key);
		return el != null ? el.getAsBoolean() : def;
	}

	public static List<FallingTown> getFallingCache() {
		return cachedFallingTowns;
	}

	public static boolean isBuilding() {
		return building;
	}

	public static synchronized void start() {
		if (scheduler != null) return;
		scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "EUtil-FallingCache");
			t.setDaemon(true);
			return t;
		});
		// 起動時にローカルキャッシュを即読み込み(古ければバックグラウンドで再構築)
		scheduler.execute(FallingCache::buildFallingCache);
		// 以降は「次の19:01 JST」ちょうどを狙ったワンショットタイマーを再帰的に仕込む
		scheduleNextGeneration();
	}

	private static void scheduleNextGeneration() {
		if (scheduler == null || scheduler.isShutdown()) return;
		long delayMs = millisUntilNextGenerationTime();
		scheduler.schedule(() -> {
			buildFallingCache();
			scheduleNextGeneration();
		}, delayMs, TimeUnit.MILLISECONDS);
	}

	/** 次の 19:01:00 JST までの残りミリ秒を算出(過ぎていれば翌日分) */
	private static long millisUntilNextGenerationTime() {
		ZonedDateTime now = ZonedDateTime.now(JST);
		ZonedDateTime next = now.withHour(GENERATION_HOUR).withMinute(GENERATION_MINUTE).withSecond(0).withNano(0);
		if (!now.isBefore(next)) {
			next = next.plusDays(1);
		}
		return Duration.between(now, next).toMillis();
	}

	/** 直近(最新)の 19:00:00 JST のタイムスタンプ(ms)を算出。キャッシュ有効性の判定に使用。 */
	private static long getLast19PMJST() {
		ZonedDateTime now = ZonedDateTime.now(JST);
		ZonedDateTime today19 = now.withHour(19).withMinute(0).withSecond(0).withNano(0);
		if (now.isBefore(today19)) {
			today19 = today19.minusDays(1);
		}
		return today19.toInstant().toEpochMilli();
	}

	public static synchronized void buildFallingCache() {
		if (building) return;
		building = true;
		try {
			// 1. ローカルキャッシュファイルの確認
			if (Files.exists(CACHE_FILE_PATH)) {
				try {
					String fileData = Files.readString(CACHE_FILE_PATH, StandardCharsets.UTF_8);
					JsonObject json = JsonParser.parseString(fileData).getAsJsonObject();

					long fileLastUpdated = json.has("lastUpdated") ? json.get("lastUpdated").getAsLong() : 0L;
					long thresholdMs = getLast19PMJST();

					if (fileLastUpdated >= thresholdMs) {
						List<FallingTown> loaded = new ArrayList<>();
						JsonArray dataArray = json.getAsJsonArray("data");
						if (dataArray != null) {
							for (JsonElement el : dataArray) {
								loaded.add(GSON.fromJson(el, FallingTown.class));
							}
						}
						cachedFallingTowns = loaded;
						System.out.println("[eutil/FallingCache] 有効なキャッシュを検出、再生成をスキップします。");
						return;
					}
				} catch (Exception ignored) {
				}
			}

			// 2. 新規構築
			System.out.println("[eutil/FallingCache] 0% : キャッシュ生成を開始します...");

			JsonArray allTowns = getJsonArray(TOWNS_API_URL);
			if (allTowns == null || allTowns.isEmpty()) {
				System.out.println("[eutil/FallingCache] 100% : 町データが存在しませんでした。");
				return;
			}

			System.out.println("[eutil/FallingCache] 10% : 全街リスト取得完了 (" + allTowns.size() + "件)。詳細を取得中...");

			List<String> townNames = new ArrayList<>();
			for (JsonElement el : allTowns) {
				String tn = getStr(el.isJsonObject() ? el.getAsJsonObject() : null, "name", null);
				if (tn != null) townNames.add(tn);
			}

			List<JsonObject> detailedTowns = fetchInChunksWithProgress(TOWNS_API_URL, townNames, "町詳細の取得", 80);

			System.out.println("[eutil/FallingCache] 50% : 町詳細取得完了。市長データを取得中...");

			Set<String> mayorNamesSet = new LinkedHashSet<>();
			for (JsonObject t : detailedTowns) {
				JsonObject mayor = getObj(t, "mayor");
				String mn = getStr(mayor, "name", null);
				if (mn != null) {
					mayorNamesSet.add(mn);
				}
			}

			List<JsonObject> detailedPlayers = fetchInChunksWithProgress(PLAYERS_API_URL, new ArrayList<>(mayorNamesSet), "市長データの取得", 80);

			System.out.println("[eutil/FallingCache] 90% : データ照合とフィルタリングを実行中...");

			Map<String, Long> mayorOnlineMap = new HashMap<>();
			for (JsonObject p : detailedPlayers) {
				String pName = getStr(p, "name", null);
				if (pName == null) continue;
				JsonObject timestamps = getObj(p, "timestamps");
				JsonElement lastOnlineEl = safeEl(timestamps, "lastOnline");
				if (lastOnlineEl != null) {
					mayorOnlineMap.put(pName.toLowerCase(Locale.ROOT), lastOnlineEl.getAsLong());
				}
			}

			long now = System.currentTimeMillis();
			List<FallingTown> newCache = new ArrayList<>();

			for (JsonObject t : detailedTowns) {
				JsonObject mayor = getObj(t, "mayor");
				String mayorName = getStr(mayor, "name", null);
				if (mayorName == null) continue;

				Long lastOnlineMs = mayorOnlineMap.get(mayorName.toLowerCase(Locale.ROOT));
				if (lastOnlineMs == null) continue;

				ZonedDateTime lastOnlineJst = Instant.ofEpochMilli(lastOnlineMs).atZone(JST);
				ZonedDateTime deletionDate = lastOnlineJst.plusDays(42).withHour(19).withMinute(0).withSecond(0).withNano(0);

				long deletionTimeMs = deletionDate.toInstant().toEpochMilli();
				if (deletionTimeMs < lastOnlineMs + 42 * DAY_MS) {
					deletionTimeMs += DAY_MS;
				}

				if (deletionTimeMs <= now) continue;
				if (deletionTimeMs - now > 7 * DAY_MS) continue;

				JsonObject nation = getObj(t, "nation");
				JsonObject status = getObj(t, "status");
				JsonObject stats = getObj(t, "stats");
				JsonObject perms = getObj(t, "perms");
				JsonObject coordinates = getObj(t, "coordinates");
				JsonObject spawn = getObj(coordinates, "spawn");

				int x = (int) Math.round(getDouble(spawn, "x", 0));
				int y = (int) Math.round(getDouble(spawn, "y", 0));
				int z = (int) Math.round(getDouble(spawn, "z", 0));

				JsonObject flags = getObj(perms, "flags");
				boolean pvp = getBool(flags, "pvp", false);

				JsonObject timestamps = getObj(t, "timestamps");

				newCache.add(new FallingTown(
						getStr(t, "name", "不明"),
						getStr(nation, "name", "無所属"),
						mayorName,
						getInt(stats, "numResidents", 0),
						getInt(stats, "numTownBlocks", 0),
						getDouble(stats, "balance", 0),
						x, y, z,
						"https://map.earthmc.net/?worldname=earth&mapname=flat&zoom=4&x=" + x + "&y=" + y + "&z=" + z,
						getBool(status, "isCapital", false),
						getBool(status, "isOpen", false),
						getBool(status, "canOutsidersSpawn", false),
						pvp,
						lastOnlineMs / 1000,
						deletionTimeMs,
						deletionTimeMs / 1000,
						getLong(timestamps, "registered", 0)
				));
			}

			newCache.sort(Comparator.comparingLong(FallingTown::deletionTimeMs));
			cachedFallingTowns = newCache;

			Files.createDirectories(CACHE_FILE_PATH.getParent());
			JsonObject payload = new JsonObject();
			payload.addProperty("lastUpdated", now);
			payload.add("data", GSON.toJsonTree(cachedFallingTowns));
			Files.writeString(CACHE_FILE_PATH, GSON.toJson(payload), StandardCharsets.UTF_8);

			System.out.println("[eutil/FallingCache] 100% : 完了しました。 (該当町数: " + newCache.size() + "件)");

		} catch (Exception e) {
			System.err.println("[eutil/FallingCache] エラー発生により中断: " + e);
			e.printStackTrace();
		} finally {
			building = false;
		}
	}

	private static JsonArray getJsonArray(String url) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.timeout(Duration.ofSeconds(10))
				.GET()
				.build();
		HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) return null;
		return JsonParser.parseString(response.body()).getAsJsonArray();
	}

	private static List<List<String>> chunkList(List<String> list, int size) {
		List<List<String>> chunks = new ArrayList<>();
		for (int i = 0; i < list.size(); i += size) {
			chunks.add(list.subList(i, Math.min(i + size, list.size())));
		}
		return chunks;
	}

	private static List<JsonObject> fetchInChunksWithProgress(String url, List<String> items, String label, long delayMs) throws InterruptedException {
		List<List<String>> chunks = chunkList(items, 100);
		int totalChunks = chunks.size();
		List<JsonObject> results = new ArrayList<>();

		for (int i = 0; i < totalChunks; i++) {
			List<String> chunk = chunks.get(i);
			int attempts = 0;
			boolean success = false;

			while (attempts < 3 && !success) {
				try {
					JsonObject body = new JsonObject();
					JsonArray query = new JsonArray();
					chunk.forEach(query::add);
					body.add("query", query);

					HttpRequest request = HttpRequest.newBuilder()
							.uri(URI.create(url))
							.timeout(Duration.ofSeconds(15))
							.header("Content-Type", "application/json")
							.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
							.build();

					HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

					if (response.statusCode() == 429) {
						attempts++;
						System.out.println("[eutil/FallingCache] 429: 2秒待機してリトライします... (" + attempts + "/3)");
						Thread.sleep(2000);
						continue;
					}

					if (response.statusCode() == 200) {
						JsonArray arr = JsonParser.parseString(response.body()).getAsJsonArray();
						for (JsonElement el : arr) {
							results.add(el.getAsJsonObject());
						}
						success = true;
					} else {
						System.err.println("[eutil/FallingCache] 詳細取得失敗: HTTP " + response.statusCode());
						break;
					}
				} catch (IOException e) {
					attempts++;
					System.err.println("[eutil/FallingCache] 詳細取得失敗: " + e.getMessage());
					break;
				}
			}

			int progress = (int) Math.floor(((i + 1) / (double) totalChunks) * 100);
			System.out.println("[eutil/FallingCache] " + label + ": " + progress + "% 完了 (" + (i + 1) + "/" + totalChunks + " チャンク)");
			Thread.sleep(delayMs);
		}
		return results;
	}
}