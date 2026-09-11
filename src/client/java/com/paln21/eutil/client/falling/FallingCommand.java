// src/client/java/com/paln21/eutil/client/falling/FallingCommand.java
package com.paln21.eutil.client.falling;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class FallingCommand {

	private static final int ITEMS_PER_PAGE = 5;
	private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM/dd HH:mm");

	// クライアントは単一プレイヤー視点なので状態はstaticで十分
	private static List<FallingTown> lastResult = List.of();
	private static String lastNationFilter = null;
	private static int currentPage = 1;
	private static int totalPages = 1;

	public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    literal("falling")
                            .executes(ctx -> run(ctx, null, null))
                            .then(argument("sort", StringArgumentType.word())
                                    .suggests((ctx, builder) -> {
                                        for (String s : new String[]{"alphabetical", "founded", "residents", "size", "balance", "capital", "open"}) {
                                            builder.suggest(s);
                                        }
                                        return builder.buildFuture();
                                    })
                                    .executes(ctx -> run(ctx, StringArgumentType.getString(ctx, "sort"), null))
                                    .then(argument("nation", StringArgumentType.greedyString())
                                            .executes(ctx -> run(ctx, StringArgumentType.getString(ctx, "sort"), StringArgumentType.getString(ctx, "nation"))))));

            dispatcher.register(
                    literal("fallingpage")
                            .then(argument("direction", StringArgumentType.word())
                                    .executes(FallingCommand::page)));
        });
    }

	private static int run(CommandContext<FabricClientCommandSource> ctx, String sortOption, String targetNation) {
		FabricClientCommandSource source = ctx.getSource();

		List<FallingTown> cached = FallingCache.getFallingCache();
		if (cached.isEmpty()) {
			Text msg = FallingCache.isBuilding()
					? Text.translatable("eutil.falling.cache_building")
					: Text.translatable("eutil.falling.cache_empty");
			source.sendFeedback(msg);
			return 1;
		}

		List<FallingTown> townsList = new ArrayList<>(cached);
		if (targetNation != null && !targetNation.isBlank()) {
			String query = targetNation.toLowerCase(Locale.ROOT);
			townsList.removeIf(t -> t.nation() == null || !t.nation().toLowerCase(Locale.ROOT).contains(query));
		}

		if (townsList.isEmpty()) {
			Text msg = (targetNation != null && !targetNation.isBlank())
					? Text.translatable("eutil.falling.no_towns_in_nation", targetNation)
					: Text.translatable("eutil.falling.no_towns");
			source.sendFeedback(msg);
			return 1;
		}

		sortTowns(townsList, sortOption == null ? "default" : sortOption);

		lastResult = townsList;
		lastNationFilter = targetNation;
		currentPage = 1;
		totalPages = (int) Math.ceil(townsList.size() / (double) ITEMS_PER_PAGE);

		source.sendFeedback(buildPageMessage());
		return 1;
	}

	private static int page(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();

		if (lastResult.isEmpty()) {
			source.sendFeedback(Text.translatable("eutil.falling.run_first"));
			return 1;
		}

		String direction = StringArgumentType.getString(ctx, "direction");
		switch (direction) {
			case "first" -> currentPage = 1;
			case "prev" -> currentPage = Math.max(1, currentPage - 1);
			case "next" -> currentPage = Math.min(totalPages, currentPage + 1);
			case "last" -> currentPage = totalPages;
			default -> {
				return 1;
			}
		}

		source.sendFeedback(buildPageMessage());
		return 1;
	}

	/** 残り時間を「⏳残り X日Y時間 (崩壊予定: MM/dd HH:mm JST)」の形式で返す。緊急度で色分けする。 */
	private static String remainingTimeText(long deletionTimeMs) {
		long remainingMs = Math.max(0, deletionTimeMs - System.currentTimeMillis());
		long totalMinutes = remainingMs / 60000;
		long days = totalMinutes / (60 * 24);
		long hours = (totalMinutes / 60) % 24;
		long minutes = totalMinutes % 60;

		String color;
		if (days < 1) {
			color = "§c"; // 1日未満: 赤
		} else if (days < 3) {
			color = "§6"; // 3日未満: オレンジ
		} else {
			color = "§a"; // それ以外: 緑
		}

		String remainingStr = days > 0
				? Text.translatable("eutil.falling.duration_with_days", days, hours, minutes).getString()
				: Text.translatable("eutil.falling.duration_no_days", hours, minutes).getString();
		String deletionDateStr = ZonedDateTime.ofInstant(Instant.ofEpochMilli(deletionTimeMs), JST).format(DATE_FMT);

		return color + Text.translatable("eutil.falling.remaining_time", remainingStr, deletionDateStr).getString();
	}

	private static void sortTowns(List<FallingTown> townsList, String sortOption) {
		Comparator<FallingTown> comparator = switch (sortOption) {
			case "alphabetical" -> Comparator.comparing(FallingTown::name, String.CASE_INSENSITIVE_ORDER);
			case "founded" -> Comparator.comparingLong(FallingTown::registered).reversed();
			case "residents" -> Comparator.comparingInt(FallingTown::residents).reversed();
			case "size" -> Comparator.comparingInt(FallingTown::plots).reversed();
			case "balance" -> Comparator.comparingDouble(FallingTown::balance).reversed();
			case "capital" -> Comparator.comparing(FallingTown::isCapital).reversed();
			case "open" -> Comparator.comparing(FallingTown::isOpen).reversed();
			default -> Comparator.comparingLong(FallingTown::deletionTimeMs);
		};
		townsList.sort(comparator);
	}

	private static Text buildPageMessage() {
		int start = (currentPage - 1) * ITEMS_PER_PAGE;
		int end = Math.min(start + ITEMS_PER_PAGE, lastResult.size());
		List<FallingTown> currentItems = lastResult.subList(start, end);

		String titleNationStr = (lastNationFilter != null && !lastNationFilter.isBlank()) ? " [" + lastNationFilter + "]" : "";
		String titleStr = Text.translatable("eutil.falling.list_title",
				lastResult.size(), titleNationStr, currentPage, totalPages).getString();
		MutableText message = Text.literal("§e" + titleStr + "§r\n");

		int itemNum = start;
		for (FallingTown t : currentItems) {
			String mapUrl = "https://map.earthmc.net/?x=" + t.x() + "&z=" + t.z() + "&zoom=3";
			itemNum++;
			String capIcon = t.isCapital() ? "⭕" : "❌";
			String openIcon = t.isOpen() ? "⭕" : "❌";
			String spawnIcon = t.canOutsidersSpawn() ? "⭕" : "❌";
			String pvpIcon = t.pvp() ? "⭕" : "❌";

			String coordsStr = Text.translatable("eutil.falling.coords", t.x(), t.y(), t.z()).getString();
			String mayorStr = Text.translatable("eutil.falling.mayor", t.mayor()).getString();
			String statsStr = Text.translatable("eutil.falling.stats", t.residents(), t.plots(), t.balance()).getString();
			String flagsStr = Text.translatable("eutil.falling.flags", capIcon, openIcon, spawnIcon, pvpIcon).getString();

			message.append(Text.literal("§6" + itemNum + ". " + t.name() + "§r (" + t.nation() + ")\n"));
			message.append(Text.literal("  " + remainingTimeText(t.deletionTimeMs()) + "\n"));
			message.append(Text.literal("  " + coordsStr + "\n")
			    .styled(style -> style.withClickEvent(new ClickEvent.OpenUrl(java.net.URI.create(mapUrl)))));
			message.append(Text.literal("  " + mayorStr + "\n"));
			message.append(Text.literal("  " + statsStr + "\n"));
			message.append(Text.literal("  " + flagsStr + "\n\n"));
		}

		message.append(navButton("<<", "first", currentPage > 1));
		message.append(Text.literal(" "));
		message.append(navButton("<", "prev", currentPage > 1));
		message.append(Text.literal("  "));
		message.append(navButton(">", "next", currentPage < totalPages));
		message.append(Text.literal(" "));
		message.append(navButton(">>", "last", currentPage < totalPages));

		return message;
	}

    private static MutableText navButton(String label, String direction, boolean enabled) {
        String color = enabled ? "§a" : "§8";
        MutableText text = Text.literal(color + label + "§r");
        if (enabled) {
            text.styled(style -> style.withClickEvent(new ClickEvent.RunCommand("/fallingpage " + direction)));
        }
        return text;
    }
}