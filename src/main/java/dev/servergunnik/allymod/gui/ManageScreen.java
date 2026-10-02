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
	private static final int MAX_ROWS = 5;
	private static final int ROW_H = 22;
	private static final int TINT = 0xB0000000;
	private static final int COLOR_OK = 0xFF33DD33;
	private static final int COLOR_ERROR = 0xFFDD3333;

	private EditBox manualNick;
	private int onlinePage = 0;
	private int relationsPage = 0;
	private Component status = null;
	private int statusColor = COLOR_OK;

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
		int firstRowY = panelY + 46;

		List<PlayerInfo> online = onlinePlayersSorted();
		List<Relation> relations = allRelationsSorted();
		onlinePage = clampPage(onlinePage, online.size());
		relationsPage = clampPage(relationsPage, relations.size());

		for (int i = 0; i < MAX_ROWS; i++) {
			int index = onlinePage * MAX_ROWS + i;
			if (index >= online.size()) break;
			PlayerInfo info = online.get(index);
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

		for (int i = 0; i < MAX_ROWS; i++) {
			int index = relationsPage * MAX_ROWS + i;
			if (index >= relations.size()) break;
			Relation r = relations.get(index);
			int y = firstRowY + i * ROW_H;
			addRenderableWidget(Button.builder(
					Component.literal("X").withStyle(ChatFormatting.GRAY),
					b -> unassign(r)
			).bounds(rightColX + colW - 20, y, 18, 20).build());
		}

		// strony list (tylko gdy sie nie mieszcza)
		int pagerY = firstRowY - 15;
		if (pageCount(online.size()) > 1) {
			addPager(leftColX + colW - 30, pagerY, -1, () -> onlinePage--);
			addPager(leftColX + colW - 14, pagerY, 1, () -> onlinePage++);
		}
		if (pageCount(relations.size()) > 1) {
			addPager(rightColX + colW - 30, pagerY, -1, () -> relationsPage--);
			addPager(rightColX + colW - 14, pagerY, 1, () -> relationsPage++);
		}

		// manual add row
		int manualY = panelY + PANEL_H - 55;
		String typedNick = manualNick != null ? manualNick.getValue() : "";
		this.manualNick = new EditBox(this.font, panelX + 10, manualY, 150, 20,
				Component.translatable("allymod.gui.manual_add_placeholder"));
		this.manualNick.setHint(Component.translatable("allymod.gui.manual_add_placeholder").withStyle(ChatFormatting.DARK_GRAY));
		this.manualNick.setMaxLength(16);
		this.manualNick.setValue(typedNick);
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
		int firstRowY = panelY + 46;

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
				panelX + PANEL_W / 2, panelY + 20, 0xFFAAAAAA);

		// section headers
		g.text(font, Component.translatable("allymod.gui.online_players").withStyle(ChatFormatting.YELLOW),
				leftColX, firstRowY - 12, 0xFFFFFFFF);
		g.text(font, Component.translatable("allymod.gui.relations").withStyle(ChatFormatting.YELLOW),
				rightColX, firstRowY - 12, 0xFFFFFFFF);

		// rows: online player names
		List<PlayerInfo> online = onlinePlayersSorted();
		drawPageLabel(g, font, onlinePage, online.size(), leftColX + colW - 34, firstRowY - 12);
		if (online.isEmpty()) {
			g.text(font, Component.translatable("allymod.gui.list_empty").withStyle(ChatFormatting.DARK_GRAY),
					leftColX, firstRowY, 0xFF666666);
		}
		for (int i = 0; i < MAX_ROWS; i++) {
			int index = onlinePage * MAX_ROWS + i;
			if (index >= online.size()) break;
			int y = firstRowY + i * ROW_H + 6;
			String name = online.get(index).getProfile().name();
			g.text(font, truncate(font, name, colW - 60), leftColX, y, 0xFFEEEEEE);
		}

		// rows: relations
		List<Relation> relations = allRelationsSorted();
		drawPageLabel(g, font, relationsPage, relations.size(), rightColX + colW - 34, firstRowY - 12);
		if (relations.isEmpty()) {
			g.text(font, Component.translatable("allymod.gui.list_empty").withStyle(ChatFormatting.DARK_GRAY),
					rightColX, firstRowY, 0xFF666666);
		}
		for (int i = 0; i < MAX_ROWS; i++) {
			int index = relationsPage * MAX_ROWS + i;
			if (index >= relations.size()) break;
			Relation r = relations.get(index);
			int y = firstRowY + i * ROW_H + 6;
			int color = r.kind() == RelationKind.ALLY ? 0xFF33DD33 : 0xFFDD3333;
			String tag = r.kind() == RelationKind.ALLY ? "[A] " : "[E] ";
			g.text(font, truncate(font, tag + r.lastKnownName(), colW - 25), rightColX, y, color);
		}

		// wynik ostatniej akcji (dodanie / usuniecie / blad)
		if (status != null) {
			g.text(font, truncate(font, status.getString(), PANEL_W - 20), panelX + 10,
					panelY + PANEL_H - 66, statusColor);
		}
	}

	private void drawPageLabel(GuiGraphicsExtractor g, Font font, int page, int size, int rightX, int y) {
		int pages = pageCount(size);
		if (pages <= 1) return;
		String label = (page + 1) + "/" + pages;
		g.text(font, label, rightX - font.width(label), y, 0xFFAAAAAA);
	}

	private void addPager(int x, int y, int direction, Runnable move) {
		addRenderableWidget(Button.builder(Component.literal(direction < 0 ? "<" : ">"), b -> {
			move.run();
			rebuildWidgets();
		}).bounds(x, y, 14, 12).build());
	}

	private static int pageCount(int size) {
		return Math.max(1, (size + MAX_ROWS - 1) / MAX_ROWS);
	}

	private static int clampPage(int page, int size) {
		return Math.max(0, Math.min(page, pageCount(size) - 1));
	}

	private void setStatus(Component message, int color) {
		this.status = message;
		this.statusColor = color;
	}

	private String truncate(Font font, String s, int maxWidth) {
		if (font.width(s) <= maxWidth) return s;
		return font.plainSubstrByWidth(s, maxWidth - font.width("...")) + "...";
	}

	private List<PlayerInfo> onlinePlayersSorted() {
		ClientPacketListener conn = Minecraft.getInstance().getConnection();
		if (conn == null) return List.of();
		Minecraft mc = Minecraft.getInstance();
		UUID self = mc.player != null ? mc.player.getUUID() : null;
		List<PlayerInfo> list = new ArrayList<>();
		for (PlayerInfo info : conn.getOnlinePlayers()) {
			if (!info.getProfile().id().equals(self)) list.add(info);
		}
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
		if (trySave(store)) {
			String key = kind == RelationKind.ALLY ? "allymod.command.add.ally" : "allymod.command.add.enemy";
			setStatus(Component.translatable(key, name), kind == RelationKind.ALLY ? COLOR_OK : COLOR_ERROR);
		}
		this.rebuildWidgets();
	}

	private void unassign(Relation r) {
		RelationStore store = Allymod.relations();
		RelationCache cache = Allymod.cache();
		store.remove(r.uuid());
		cache.refresh(r.uuid(), null);
		if (trySave(store)) {
			String key = r.kind() == RelationKind.ALLY ? "allymod.command.remove.ally" : "allymod.command.remove.enemy";
			setStatus(Component.translatable(key, r.lastKnownName()), 0xFFAAAAAA);
		}
		this.rebuildWidgets();
	}

	private void manualAdd(RelationKind kind) {
		String nick = manualNick.getValue().trim();
		if (nick.isEmpty()) return;
		ClientPacketListener conn = Minecraft.getInstance().getConnection();
		if (conn == null) {
			setStatus(Component.translatable("allymod.command.error.not_on_server"), COLOR_ERROR);
			return;
		}
		PlayerInfo info = conn.getPlayerInfoIgnoreCase(nick);
		if (info == null) {
			setStatus(Component.translatable("allymod.command.error.player_not_online", nick), COLOR_ERROR);
			return;
		}
		UUID uuid = info.getProfile().id();
		String realName = info.getProfile().name();
		assign(uuid, realName, kind);
		manualNick.setValue("");
	}

	private boolean trySave(RelationStore store) {
		try {
			store.save();
			return true;
		} catch (IOException e) {
			Allymod.LOGGER.error("Zapis relations.json nieudany", e);
			setStatus(Component.translatable("allymod.command.error.save_failed", e.getMessage()), COLOR_ERROR);
			return false;
		}
	}
}
