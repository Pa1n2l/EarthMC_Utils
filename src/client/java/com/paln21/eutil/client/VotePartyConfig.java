// src/client/java/com/paln21/eutil/client/VotePartyConfig.java
package com.paln21.eutil.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class VotePartyConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance()
			.getConfigDir()
			.resolve("eutil-voteparty.json");

	public enum Corner {
		TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
	}

	public Corner corner = Corner.TOP_RIGHT;
	public int offsetX = 4;
	public int offsetY = 4;
	public boolean showBar = true;
	public int textColor = 0xFFFFFF;   // RGB (下位24bit)
	public int barFillColor = 0x55FF55; // RGB (下位24bit)

	private static VotePartyConfig instance;

	public static VotePartyConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static VotePartyConfig load() {
		if (Files.exists(CONFIG_PATH)) {
			try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
				VotePartyConfig loaded = GSON.fromJson(reader, VotePartyConfig.class);
				if (loaded != null) return loaded;
			} catch (IOException | JsonSyntaxException ignored) {
			}
		}
		return new VotePartyConfig();
	}

	public void save() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException ignored) {
		}
	}
}