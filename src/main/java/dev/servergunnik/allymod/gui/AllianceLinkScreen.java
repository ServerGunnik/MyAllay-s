package dev.servergunnik.allymod.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Okienko do wklejenia linku do configu sojuszu (Ctrl+V). */
public final class AllianceLinkScreen extends Screen {
	private static final int PANEL_W = 340;
	private static final int PANEL_H = 110;
	private static final int TINT = 0xB0000000;

	private final AllianceConfigsScreen parent;
	private EditBox urlBox;

	public AllianceLinkScreen(AllianceConfigsScreen parent) {
		super(Component.translatable("allymod.alliance.link.title"));
		this.parent = parent;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		int panelX = (this.width - PANEL_W) / 2;
		int panelY = (this.height - PANEL_H) / 2;

		String typed = urlBox != null ? urlBox.getValue() : "";
		urlBox = new EditBox(this.font, panelX + 10, panelY + 45, PANEL_W - 20, 20,
				Component.translatable("allymod.alliance.link.title"));
		urlBox.setMaxLength(2048);
		urlBox.setHint(Component.literal("https://...").withStyle(ChatFormatting.DARK_GRAY));
		urlBox.setValue(typed);
		addRenderableWidget(urlBox);
		setInitialFocus(urlBox);

		addRenderableWidget(Button.builder(
				Component.translatable("allymod.alliance.link.add").withStyle(ChatFormatting.GREEN),
				b -> submit()
		).bounds(panelX + PANEL_W - 180, panelY + PANEL_H - 28, 80, 20).build());
		addRenderableWidget(Button.builder(
				Component.translatable("allymod.alliance.link.cancel"),
				b -> this.onClose()
		).bounds(panelX + PANEL_W - 90, panelY + PANEL_H - 28, 80, 20).build());
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		g.fill(0, 0, this.width, this.height, TINT);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		int panelX = (this.width - PANEL_W) / 2;
		int panelY = (this.height - PANEL_H) / 2;
		g.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xE01a1a1a);
		g.centeredText(this.font, this.title, panelX + PANEL_W / 2, panelY + 8, 0xFFFFFFFF);
		g.text(this.font, Component.translatable("allymod.alliance.link.hint").withStyle(ChatFormatting.GRAY),
				panelX + 10, panelY + 28, 0xFFAAAAAA);
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreenAndShow(parent);
	}

	private void submit() {
		String url = urlBox.getValue().trim();
		if (url.isEmpty()) return;
		parent.addLink(url);
		onClose();
	}
}
