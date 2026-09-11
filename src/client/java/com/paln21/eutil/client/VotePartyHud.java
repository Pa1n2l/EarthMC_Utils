// src/client/java/com/paln21/eutil/client/VotePartyHud.java
package com.paln21.eutil.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

public class VotePartyHud {

	private static final int BAR_WIDTH = 100;
	private static final int BAR_HEIGHT = 4;

	public static void register() {
		HudRenderCallback.EVENT.register(VotePartyHud::render);
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.options.hudHidden) return;

		VotePartyConfig config = VotePartyConfig.get();
		EarthMcApi.VotePartyStatus status = EarthMcApi.getLatest();

		String line1;
		String line2 = null;
		float progress = 0f;

		if (!status.valid()) {
			line1 = "VoteParty: 読込中...";
		} else {
			line1 = "VoteParty: " + status.votesReceived() + " / " + status.target();
			line2 = "残り " + status.numRemaining() + " 票";
			progress = status.progress();
		}

		int screenWidth = context.getScaledWindowWidth();
		int screenHeight = context.getScaledWindowHeight();

		int width1 = client.textRenderer.getWidth(line1);
		int width2 = line2 != null ? client.textRenderer.getWidth(line2) : 0;
		int blockWidth = Math.max(Math.max(width1, width2), config.showBar ? BAR_WIDTH : 0);
		int blockHeight = 10 + (line2 != null ? 10 : 0) + (config.showBar ? BAR_HEIGHT + 3 : 0);

		int x;
		int y;

		switch (config.corner) {
			case TOP_LEFT -> {
				x = config.offsetX;
				y = config.offsetY;
			}
			case BOTTOM_LEFT -> {
				x = config.offsetX;
				y = screenHeight - blockHeight - config.offsetY;
			}
			case BOTTOM_RIGHT -> {
				x = screenWidth - blockWidth - config.offsetX;
				y = screenHeight - blockHeight - config.offsetY;
			}
			default -> { // TOP_RIGHT
				x = screenWidth - blockWidth - config.offsetX;
				y = config.offsetY;
			}
		}

		int textColor = 0xFFFFFFFF & (0xFF000000 | (config.textColor & 0xFFFFFF));
		int cursorY = y;

		context.drawTextWithShadow(client.textRenderer, line1, x + (blockWidth - width1), cursorY, textColor);
		cursorY += 10;

		if (line2 != null) {
			context.drawTextWithShadow(client.textRenderer, line2, x + (blockWidth - width2), cursorY, textColor);
			cursorY += 10;
		}

		if (config.showBar) {
			cursorY += 2;
			int barX = x + (blockWidth - BAR_WIDTH);
			int fillColor = 0xFF000000 | (config.barFillColor & 0xFFFFFF);

			context.fill(barX, cursorY, barX + BAR_WIDTH, cursorY + BAR_HEIGHT, 0x80000000);
			int filled = Math.round(BAR_WIDTH * progress);
			if (filled > 0) {
				context.fill(barX, cursorY, barX + filled, cursorY + BAR_HEIGHT, fillColor);
			}
		}
	}
}