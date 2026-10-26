package dev.servergunnik.allymod.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import dev.servergunnik.allymod.Allymod;
import dev.servergunnik.allymod.cache.RelationCache;
import dev.servergunnik.allymod.data.Relation;
import dev.servergunnik.allymod.data.RelationKind;
import dev.servergunnik.allymod.data.RelationStore;

public final class ManageScreen extends Screen {
	private static final int PANEL_W = 340;
	private static final int PANEL_H = 220;
	private static final int MAX_ROWS = 6;
	private static final int ROW_H = 22;
	private static final int TINT = 0xB0000000;

	private EditBox manualNick;

	public ManageScreen() {
		super(Component.translatable("allymod.gui.title"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		int panelX = (this.width - PANEL_W) / 2;
		int panelY = (this.height - PANEL_H) / 2;

		int leftColX = panelX + 10;
		int rightColX = panelX + PANEL_W / 2 + 5;
		int colW = PANEL_W / 2 - 15;
		int firstRowY = panelY + 40;

		List<PlayerInfo> online = onlinePlayersSorted();
		List<Relation> relations = allRelationsSorted();

		for (int i = 0; i < Math.min(online.size(), MAX_ROWS); i++) {
			PlayerInfo info = online.get(i);
			int y = firstRowY + i * ROW_H;
			String name = info.getProfile().name();
			UUID uuid = info.getProfile().id();
			// przyciski +A / +E po prawej stronie wiersza
			addRenderableWidget(Button.builder(
					Component.literal("+A").withStyle(ChatFormatting.GREEN),
					b -> assign(uuid, name, RelationKind.ALLY)
			).bounds(leftColX + colW - 56, y, 26, 20).build());
			addRenderableWidget(Button.builder(
					Component.literal("+E").withStyle(ChatFormatting.RED),
					b -> assign(uuid, name, RelationKind.ENEMY)
			).bounds(leftColX + colW - 28, y, 26, 20).build());
		}

		for (int i = 0; i < Math.min(relations.size(), MAX_ROWS); i++) {
			Relation r = relations.get(i);
			int y = firstRowY + i * ROW_H;
			addRenderableWidget(Button.builder(
					Component.literal("X").withStyle(ChatFormatting.GRAY),
					b -> unassign(r.uuid())
			).bounds(rightColX + colW - 20, y, 18, 20).build());
		}

		// manual add row
		int manualY = panelY + PANEL_H - 55;
		this.manualNick = new EditBox(this.font, panelX + 10, manualY, 150, 20,
				Component.translatable("allymod.gui.manual_add_placeholder"));
		this.manualNick.setHint(Component.translatable("allymod.gui.manual_add_placeholder").withStyle(ChatFormatting.DARK_GRAY));
		this.manualNick.setMaxLength(16);
		addRenderableWidget(manualNick);

		addRenderableWidget(Button.builder(
				Component.translatable("allymod.gui.add_ally").withStyle(ChatFormatting.GREEN),
				b -> manualAdd(RelationKind.ALLY)
		).bounds(panelX + 165, manualY, 80, 20).build());
		addRenderableWidget(Button.builder(
				Component.translatable("allymod.gui.add_enemy").withStyle(ChatFormatting.RED),
				b -> manualAdd(RelationKind.ENEMY)
		).bounds(panelX + 250, manualY, 80, 20).build());

		// configi sojuszu (snz-sojusz)
		addRenderableWidget(Button.builder(
				Component.translatable("allymod.gui.alliances").withStyle(ChatFormatting.AQUA),
				b -> Minecraft.getInstance().setScreenAndShow(new AllianceConfigsScreen(this))
		).bounds(panelX + 10, panelY + PANEL_H - 25, 90, 20).build());

		// close
		addRenderableWidget(Button.builder(
				Component.translatable("allymod.gui.close"),
				b -> this.onClose()
		).bounds(panelX + PANEL_W - 100, panelY + PANEL_H - 25, 90, 20).build());
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
		int leftColX = panelX + 10;
		int rightColX = panelX + PANEL_W / 2 + 5;
		int colW = PANEL_W / 2 - 15;
		int firstRowY = panelY + 40;

		Font font = this.font;

		// panel background
		g.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xE01a1a1a);

		// title
		g.centeredText(font, this.title, panelX + PANEL_W / 2, panelY + 8, 0xFFFFFFFF);

		// stats
		RelationStore store = Allymod.relations();
		int allies = store.byKind(RelationKind.ALLY).size();
		int enemies = store.byKind(RelationKind.ENEMY).size();
		g.centeredText(font,
				Component.translatable("allymod.gui.stats", allies, enemies).withStyle(ChatFormatting.GRAY),
				panelX + PANEL_W / 2, panelY + 22, 0xFFAAAAAA);

		// section headers
		g.text(font, Component.translatable("allymod.gui.online_players").withStyle(ChatFormatting.YELLOW),
				leftColX, firstRowY - 12, 0xFFFFFFFF);
		g.text(font, Component.translatable("allymod.gui.relations").withStyle(ChatFormatting.YELLOW),
				rightColX, firstRowY - 12, 0xFFFFFFFF);

		// rows: online player names
		List<PlayerInfo> online = onlinePlayersSorted();
		if (online.isEmpty()) {
			g.text(font, Component.translatable("allymod.gui.list_empty").withStyle(ChatFormatting.DARK_GRAY),
					leftColX, firstRowY, 0xFF666666);
		}
		for (int i = 0; i < Math.min(online.size(), MAX_ROWS); i++) {
			PlayerInfo info = online.get(i);
			int y = firstRowY + i * ROW_H + 6;
			String name = info.getProfile().name();
			g.text(font, truncate(font, name, colW - 60), leftColX, y, 0xFFEEEEEE);
		}

		// rows: relations
		List<Relation> relations = allRelationsSorted();
		if (relations.isEmpty()) {
			g.text(font, Component.translatable("allymod.gui.list_empty").withStyle(ChatFormatting.DARK_GRAY),
					rightColX, firstRowY, 0xFF666666);
		}
		for (int i = 0; i < Math.min(relations.size(), MAX_ROWS); i++) {
			Relation r = relations.get(i);
			int y = firstRowY + i * ROW_H + 6;
			int color = r.kind() == RelationKind.ALLY ? 0xFF33DD33 : 0xFFDD3333;
			String tag = r.kind() == RelationKind.ALLY ? "[A] " : "[E] ";
			g.text(font, truncate(font, tag + r.lastKnownName(), colW - 25), rightColX, y, color);
		}
	}

	private String truncate(Font font, String s, int maxWidth) {
		if (font.width(s) <= maxWidth) return s;
		return font.plainSubstrByWidth(s, maxWidth - font.width("...")) + "...";
	}

	private List<PlayerInfo> onlinePlayersSorted() {
		ClientPacketListener conn = Minecraft.getInstance().getConnection();
		if (conn == null) return List.of();
		List<PlayerInfo> list = new ArrayList<>(conn.getOnlinePlayers());
		list.sort((a, b) -> a.getProfile().name().compareToIgnoreCase(b.getProfile().name()));
		return list;
	}

	private List<Relation> allRelationsSorted() {
		List<Relation> list = new ArrayList<>(Allymod.relations().all());
		list.sort((a, b) -> a.lastKnownName().compareToIgnoreCase(b.lastKnownName()));
		return list;
	}

	private void assign(UUID uuid, String name, RelationKind kind) {
		RelationStore store = Allymod.relations();
		RelationCache cache = Allymod.cache();
		store.put(uuid, name, kind);
		cache.refresh(uuid, kind);
		trySave(store);
		this.rebuildWidgets();
	}

	private void unassign(UUID uuid) {
		RelationStore store = Allymod.relations();
		RelationCache cache = Allymod.cache();
		store.remove(uuid);
		cache.refresh(uuid, null);
		trySave(store);
		this.rebuildWidgets();
	}

	private void manualAdd(RelationKind kind) {
		String nick = manualNick.getValue().trim();
		if (nick.isEmpty()) return;
		ClientPacketListener conn = Minecraft.getInstance().getConnection();
		if (conn == null) return;
		PlayerInfo info = conn.getPlayerInfoIgnoreCase(nick);
		if (info == null) return;
		UUID uuid = info.getProfile().id();
		String realName = info.getProfile().name();
		assign(uuid, realName, kind);
		manualNick.setValue("");
	}

	private void trySave(RelationStore store) {
		try {
			store.save();
		} catch (IOException e) {
			Allymod.LOGGER.error("Zapis relations.json nieudany", e);
		}
	}
}
