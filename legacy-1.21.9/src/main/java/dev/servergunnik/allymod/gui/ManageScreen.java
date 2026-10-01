package dev.servergunnik.allymod.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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

	private TextFieldWidget manualNick;

	public ManageScreen() {
		super(Text.translatable("allymod.gui.title"));
	}

	@Override
	public boolean shouldPause() {
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

		List<PlayerListEntry> online = onlinePlayersSorted();
		List<Relation> relations = allRelationsSorted();

		for (int i = 0; i < Math.min(online.size(), MAX_ROWS); i++) {
			PlayerListEntry info = online.get(i);
			int y = firstRowY + i * ROW_H;
			String name = info.getProfile().name();
			UUID uuid = info.getProfile().id();
			addDrawableChild(ButtonWidget.builder(
					Text.literal("+A").formatted(Formatting.GREEN),
					b -> assign(uuid, name, RelationKind.ALLY)
			).dimensions(leftColX + colW - 56, y, 26, 20).build());
			addDrawableChild(ButtonWidget.builder(
					Text.literal("+E").formatted(Formatting.RED),
					b -> assign(uuid, name, RelationKind.ENEMY)
			).dimensions(leftColX + colW - 28, y, 26, 20).build());
		}

		for (int i = 0; i < Math.min(relations.size(), MAX_ROWS); i++) {
			Relation r = relations.get(i);
			int y = firstRowY + i * ROW_H;
			addDrawableChild(ButtonWidget.builder(
					Text.literal("X").formatted(Formatting.GRAY),
					b -> unassign(r.uuid())
			).dimensions(rightColX + colW - 20, y, 18, 20).build());
		}

		int manualY = panelY + PANEL_H - 55;
		this.manualNick = new TextFieldWidget(this.textRenderer, panelX + 10, manualY, 150, 20,
				Text.translatable("allymod.gui.manual_add_placeholder"));
		this.manualNick.setMaxLength(16);
		addDrawableChild(manualNick);

		addDrawableChild(ButtonWidget.builder(
				Text.translatable("allymod.gui.add_ally").formatted(Formatting.GREEN),
				b -> manualAdd(RelationKind.ALLY)
		).dimensions(panelX + 165, manualY, 80, 20).build());
		addDrawableChild(ButtonWidget.builder(
				Text.translatable("allymod.gui.add_enemy").formatted(Formatting.RED),
				b -> manualAdd(RelationKind.ENEMY)
		).dimensions(panelX + 250, manualY, 80, 20).build());

		addDrawableChild(ButtonWidget.builder(
				Text.translatable("allymod.gui.close"),
				b -> this.close()
		).dimensions(panelX + PANEL_W - 100, panelY + PANEL_H - 25, 90, 20).build());
	}

	@Override
	public void render(DrawContext g, int mouseX, int mouseY, float delta) {
		g.fill(0, 0, this.width, this.height, TINT);
		super.render(g, mouseX, mouseY, delta);

		int panelX = (this.width - PANEL_W) / 2;
		int panelY = (this.height - PANEL_H) / 2;
		int leftColX = panelX + 10;
		int rightColX = panelX + PANEL_W / 2 + 5;
		int colW = PANEL_W / 2 - 15;
		int firstRowY = panelY + 40;

		TextRenderer font = this.textRenderer;

		g.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xE01a1a1a);

		g.drawCenteredTextWithShadow(font, this.title, panelX + PANEL_W / 2, panelY + 8, 0xFFFFFFFF);

		RelationStore store = Allymod.relations();
		int allies = store.byKind(RelationKind.ALLY).size();
		int enemies = store.byKind(RelationKind.ENEMY).size();
		g.drawCenteredTextWithShadow(font,
				Text.translatable("allymod.gui.stats", allies, enemies).formatted(Formatting.GRAY),
				panelX + PANEL_W / 2, panelY + 22, 0xFFAAAAAA);

		g.drawTextWithShadow(font, Text.translatable("allymod.gui.online_players").formatted(Formatting.YELLOW),
				leftColX, firstRowY - 12, 0xFFFFFFFF);
		g.drawTextWithShadow(font, Text.translatable("allymod.gui.relations").formatted(Formatting.YELLOW),
				rightColX, firstRowY - 12, 0xFFFFFFFF);

		List<PlayerListEntry> online = onlinePlayersSorted();
		if (online.isEmpty()) {
			g.drawTextWithShadow(font, Text.translatable("allymod.gui.list_empty").formatted(Formatting.DARK_GRAY),
					leftColX, firstRowY, 0xFF666666);
		}
		for (int i = 0; i < Math.min(online.size(), MAX_ROWS); i++) {
			PlayerListEntry info = online.get(i);
			int y = firstRowY + i * ROW_H + 6;
			String name = info.getProfile().name();
			g.drawTextWithShadow(font, truncate(font, name, colW - 60), leftColX, y, 0xFFEEEEEE);
		}

		List<Relation> relations = allRelationsSorted();
		if (relations.isEmpty()) {
			g.drawTextWithShadow(font, Text.translatable("allymod.gui.list_empty").formatted(Formatting.DARK_GRAY),
					rightColX, firstRowY, 0xFF666666);
		}
		for (int i = 0; i < Math.min(relations.size(), MAX_ROWS); i++) {
			Relation r = relations.get(i);
			int y = firstRowY + i * ROW_H + 6;
			int color = r.kind() == RelationKind.ALLY ? 0xFF33DD33 : 0xFFDD3333;
			String tag = r.kind() == RelationKind.ALLY ? "[A] " : "[E] ";
			g.drawTextWithShadow(font, truncate(font, tag + r.lastKnownName(), colW - 25), rightColX, y, color);
		}
	}

	private String truncate(TextRenderer font, String s, int maxWidth) {
		if (font.getWidth(s) <= maxWidth) return s;
		return font.trimToWidth(s, maxWidth - font.getWidth("...")) + "...";
	}

	private List<PlayerListEntry> onlinePlayersSorted() {
		ClientPlayNetworkHandler conn = MinecraftClient.getInstance().getNetworkHandler();
		if (conn == null) return List.of();
		List<PlayerListEntry> list = new ArrayList<>(conn.getPlayerList());
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
		this.clearAndInit();
	}

	private void unassign(UUID uuid) {
		RelationStore store = Allymod.relations();
		RelationCache cache = Allymod.cache();
		store.remove(uuid);
		cache.refresh(uuid, null);
		trySave(store);
		this.clearAndInit();
	}

	private void manualAdd(RelationKind kind) {
		String nick = manualNick.getText().trim();
		if (nick.isEmpty()) return;
		ClientPlayNetworkHandler conn = MinecraftClient.getInstance().getNetworkHandler();
		if (conn == null) return;
		PlayerListEntry info = conn.getCaseInsensitivePlayerInfo(nick);
		if (info == null) return;
		UUID uuid = info.getProfile().id();
		String realName = info.getProfile().name();
		assign(uuid, realName, kind);
		manualNick.setText("");
	}

	private void trySave(RelationStore store) {
		try {
			store.save();
		} catch (IOException e) {
			Allymod.LOGGER.error("Zapis relations.json nieudany", e);
		}
	}
}
