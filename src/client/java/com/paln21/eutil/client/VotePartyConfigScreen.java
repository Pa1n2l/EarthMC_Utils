// src/client/java/com/paln21/eutil/client/VotePartyConfigScreen.java
package com.paln21.eutil.client;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public class VotePartyConfigScreen extends Screen {

	private final Screen parent;
	private final VotePartyConfig config;

	private TextFieldWidget textColorField;
	private TextFieldWidget barColorField;

	public VotePartyConfigScreen(Screen parent) {
		super(Text.literal("VoteParty HUD 設定"));
		this.parent = parent;
		this.config = VotePartyConfig.get();
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int widgetWidth = 200;
		int y = this.height / 2 - 100;

		// タイトルはウィジェットとして描画(Screen#renderは触らない)
		this.addDrawableChild(new TextWidget(centerX - widgetWidth / 2, y, widgetWidth, 20, this.title, this.textRenderer));
		y += 24;

		this.addDrawableChild(CyclingButtonWidget.<VotePartyConfig.Corner>builder(
						corner -> Text.literal(cornerLabel(corner)), config.corner)
				.values(VotePartyConfig.Corner.values())
				.build(centerX - widgetWidth / 2, y, widgetWidth, 20, Text.literal("表示位置"),
						(button, value) -> config.corner = value));
		y += 24;

		this.addDrawableChild(new SliderWidget(centerX - widgetWidth / 2, y, widgetWidth, 20,
				Text.literal("横オフセット: " + config.offsetX), config.offsetX / 200.0) {
			@Override
			protected void updateMessage() {
				setMessage(Text.literal("横オフセット: " + (int) (this.value * 200)));
			}

			@Override
			protected void applyValue() {
				config.offsetX = (int) MathHelper.lerp(this.value, 0, 200);
			}
		});
		y += 24;

		this.addDrawableChild(new SliderWidget(centerX - widgetWidth / 2, y, widgetWidth, 20,
				Text.literal("縦オフセット: " + config.offsetY), config.offsetY / 200.0) {
			@Override
			protected void updateMessage() {
				setMessage(Text.literal("縦オフセット: " + (int) (this.value * 200)));
			}

			@Override
			protected void applyValue() {
				config.offsetY = (int) MathHelper.lerp(this.value, 0, 200);
			}
		});
		y += 24;

		this.addDrawableChild(CyclingButtonWidget.onOffBuilder(config.showBar)
				.build(centerX - widgetWidth / 2, y, widgetWidth, 20, Text.literal("進捗バー表示"),
						(button, value) -> config.showBar = value));
		y += 24;

		this.textColorField = new TextFieldWidget(this.textRenderer, centerX - widgetWidth / 2, y, widgetWidth, 20, Text.literal("文字色"));
		this.textColorField.setMaxLength(7);
		this.textColorField.setText(String.format("#%06X", config.textColor & 0xFFFFFF));
		this.addDrawableChild(this.textColorField);
		y += 24;

		this.barColorField = new TextFieldWidget(this.textRenderer, centerX - widgetWidth / 2, y, widgetWidth, 20, Text.literal("バー色"));
		this.barColorField.setMaxLength(7);
		this.barColorField.setText(String.format("#%06X", config.barFillColor & 0xFFFFFF));
		this.addDrawableChild(this.barColorField);
		y += 30;

		this.addDrawableChild(ButtonWidget.builder(Text.literal("完了"), button -> this.close())
				.dimensions(centerX - widgetWidth / 2, y, widgetWidth, 20)
				.build());
	}

	private static String cornerLabel(VotePartyConfig.Corner corner) {
		return switch (corner) {
			case TOP_LEFT -> "位置: 左上";
			case TOP_RIGHT -> "位置: 右上";
			case BOTTOM_LEFT -> "位置: 左下";
			case BOTTOM_RIGHT -> "位置: 右下";
		};
	}

	private static int parseHexColor(String text, int fallback) {
		try {
			String cleaned = text.startsWith("#") ? text.substring(1) : text;
			return Integer.parseInt(cleaned, 16) & 0xFFFFFF;
		} catch (Exception e) {
			return fallback;
		}
	}

	@Override
	public void close() {
		config.textColor = parseHexColor(this.textColorField.getText(), config.textColor);
		config.barFillColor = parseHexColor(this.barColorField.getText(), config.barFillColor);
		config.save();
		if (this.client != null) {
			this.client.setScreen(this.parent);
		}
	}

//	@Override
	public boolean shouldPauseGame() {
		return false;
	}
}