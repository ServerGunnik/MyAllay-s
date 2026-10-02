package dev.servergunnik.allymod.gui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import dev.servergunnik.allymod.Allymod;
import dev.servergunnik.allymod.data.AllianceLinks;
import dev.servergunnik.allymod.data.AllianceStore;
import dev.servergunnik.allymod.sync.AllianceUpdater;

/**
 * Lista configow sojuszu (config/snz-sojusz/*.json): dodawanie z pliku albo
 * z linku, usuwanie, przeladowanie. Pliki mozna tez przeciagnac na okno gry.
 */
public final class AllianceConfigsScreen extends Screen {
	private static final int PANEL_W = 340;
	private static final int PANEL_H = 240;
	private static final int MAX_ROWS = 6;
	private static final int ROW_H = 22;
	private static final int TINT = 0xB0000000;
	private static final int COLOR_OK = 0xFF33DD33;
	private static final int COLOR_WARN = 0xFFDDBB33;
	private static final int COLOR_ERROR = 0xFFDD3333;
	private static final int COLOR_INFO = 0xFFAAAAAA;
	private static final int COLOR_LINK = 0xFF55CCEE;

	private final Screen parent;
	private int page = 0;
	private Component status = null;
	private int statusColor = COLOR_INFO;
	private volatile boolean pickerOpen = false;

	public AllianceConfigsScreen(Screen parent) {
		super(Component.translatable("allymod.alliance.gui.title"));
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
		int firstRowY = panelY + 40;

		List<AllianceStore.ConfigInfo> configs = Allymod.alliance().configs();
		int pages = pageCount(configs.size());
		page = Math.min(page, pages - 1);

		for (int i = 0; i < MAX_ROWS; i++) {
			int index = page * MAX_ROWS + i;
			if (index >= configs.size()) break;
			String fileName = configs.get(index).fileName();
			addRenderableWidget(Button.builder(
					Component.literal("X").withStyle(ChatFormatting.GRAY),
					b -> confirmRemove(fileName)
			).bounds(panelX + PANEL_W - 28, firstRowY + i * ROW_H, 18, 20).build());
		}

		if (pages > 1) {
			addRenderableWidget(Button.builder(Component.literal("<"), b -> {
				page = Math.max(0, page - 1);
				rebuildWidgets();
			}).bounds(panelX + PANEL_W - 54, panelY + 16, 20, 18).build());
			addRenderableWidget(Button.builder(Component.literal(">"), b -> {
				page = Math.min(pages - 1, page + 1);
				rebuildWidgets();
			}).bounds(panelX + PANEL_W - 30, panelY + 16, 20, 18).build());
		}

		int buttonsY = panelY + PANEL_H - 25;
		addRenderableWidget(Button.builder(
				Component.translatable("allymod.alliance.gui.add").withStyle(ChatFormatting.GREEN),
				b -> openFilePicker()
		).bounds(panelX + 10, buttonsY, 70, 20).build());
		addRenderableWidget(Button.builder(
				Component.translatable("allymod.alliance.gui.add_link").withStyle(ChatFormatting.AQUA),
				b -> Minecraft.getInstance().setScreenAndShow(new AllianceLinkScreen(this))
		).bounds(panelX + 85, buttonsY, 60, 20).build());
		addRenderableWidget(Button.builder(
				Component.translatable("allymod.alliance.gui.reload"),
				b -> reload()
		).bounds(panelX + 150, buttonsY, 60, 20).build());
		addRenderableWidget(Button.builder(
				Component.translatable("allymod.alliance.gui.folder"),
				b -> openFolder()
		).bounds(panelX + 215, buttonsY, 50, 20).build());
		addRenderableWidget(Button.builder(
				Component.translatable("allymod.alliance.gui.back"),
				b -> this.onClose()
		).bounds(panelX + 270, buttonsY, 60, 20).build());
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
		int firstRowY = panelY + 40;
		Font font = this.font;
		AllianceStore alliance = Allymod.alliance();
		AllianceLinks links = Allymod.allianceLinks();
		List<AllianceStore.ConfigInfo> configs = alliance.configs();

		g.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xE01a1a1a);
		g.centeredText(font, this.title, panelX + PANEL_W / 2, panelY + 8, 0xFFFFFFFF);
		g.centeredText(font,
				Component.translatable("allymod.alliance.gui.summary", alliance.size(), configs.size())
						.withStyle(ChatFormatting.GRAY),
				panelX + PANEL_W / 2, panelY + 22, COLOR_INFO);

		int pages = pageCount(configs.size());
		if (pages > 1) {
			g.text(font, (page + 1) + "/" + pages, panelX + PANEL_W - 80, panelY + 21, COLOR_INFO);
		}

		if (configs.isEmpty()) {
			g.text(font, Component.translatable("allymod.alliance.gui.empty").withStyle(ChatFormatting.DARK_GRAY),
					panelX + 10, firstRowY + 6, 0xFF666666);
		}
		for (int i = 0; i < MAX_ROWS; i++) {
			int index = page * MAX_ROWS + i;
			if (index >= configs.size()) break;
			AllianceStore.ConfigInfo info = configs.get(index);
			int y = firstRowY + i * ROW_H + 6;

			boolean isLink = links.find(info.fileName()) != null;
			AllianceLinks.LinkStatus linkStatus = isLink ? links.status(info.fileName()) : null;
			boolean linkFailed = linkStatus != null && linkStatus.error() != null;

			Component right;
			if (!info.ok()) {
				right = Component.translatable("allymod.alliance.gui.error");
			} else if (isLink) {
				right = Component.translatable(linkFailed ? "allymod.alliance.gui.players_link_failed"
						: "allymod.alliance.gui.players_link", info.players());
			} else {
				right = Component.translatable("allymod.alliance.gui.players", info.players());
			}
			int rightColor = !info.ok() ? COLOR_ERROR
					: linkFailed || !info.warnings().isEmpty() ? COLOR_WARN : COLOR_INFO;
			int rightX = panelX + PANEL_W - 34 - font.width(right);
			g.text(font, right, rightX, y, rightColor);

			int nameColor = !info.ok() ? COLOR_ERROR : isLink ? COLOR_LINK : 0xFFEEEEEE;
			g.text(font, truncate(font, info.fileName(), rightX - panelX - 20), panelX + 10, y, nameColor);
		}

		if (status != null) {
			g.text(font, truncate(font, status.getString(), PANEL_W - 20), panelX + 10, panelY + PANEL_H - 56, statusColor);
		}
		g.text(font, Component.translatable("allymod.alliance.gui.drop_hint").withStyle(ChatFormatting.DARK_GRAY),
				panelX + 10, panelY + PANEL_H - 42, 0xFF777777);
	}

	// Przeciagniecie plikow z eksploratora na okno gry.
	@Override
	public void onFilesDrop(List<Path> paths) {
		importFiles(paths);
	}

	@Override
	public void onClose() {
		if (parent != null) {
			Minecraft.getInstance().setScreenAndShow(parent);
		} else {
			super.onClose();
		}
	}

	private void openFilePicker() {
		if (pickerOpen) return;
		pickerOpen = true;
		setStatus(Component.translatable("allymod.alliance.gui.picker_open"), COLOR_INFO);
		String title = Component.translatable("allymod.alliance.gui.picker_title").getString();
		String filter = Component.translatable("allymod.alliance.gui.picker_filter").getString();

		// Okno systemowe blokuje watek — trzymamy je poza render threadem, zeby gra nie zamarzla.
		Thread thread = new Thread(() -> {
			List<Path> picked;
			try {
				picked = JsonFilePicker.pickJsonFiles(title, filter);
			} catch (Throwable t) {
				Allymod.LOGGER.error("Okno wyboru pliku nie zadzialalo", t);
				picked = null;
			}
			List<Path> result = picked;
			Minecraft.getInstance().execute(() -> {
				pickerOpen = false;
				if (result == null) {
					setStatus(Component.translatable("allymod.alliance.gui.picker_failed"), COLOR_ERROR);
				} else if (result.isEmpty()) {
					setStatus(Component.translatable("allymod.alliance.gui.picker_cancelled"), COLOR_INFO);
				} else {
					importFiles(result);
				}
			});
		}, "MyAllay's file picker");
		thread.setDaemon(true);
		thread.start();
	}

	/**
	 * Gdy ktorys plik ma nazwe istniejacego configu (bot zwykle nazywa wszystkie
	 * tak samo), pyta: podmienic stary config czy dodac jako nowy.
	 */
	private void importFiles(List<Path> paths) {
		AllianceStore alliance = Allymod.alliance();
		List<String> conflicts = new ArrayList<>();
		for (Path path : paths) {
			String name = AllianceStore.configNameFor(path);
			if (alliance.hasConfig(name)) conflicts.add(name);
		}
		if (conflicts.isEmpty()) {
			copyFiles(paths, false);
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		mc.setScreenAndShow(new ConfirmScreen(replace -> {
			mc.setScreenAndShow(this);
			copyFiles(paths, replace);
		},
				Component.translatable("allymod.alliance.gui.conflict_title"),
				Component.translatable("allymod.alliance.gui.conflict_message", String.join(", ", conflicts)),
				Component.translatable("allymod.alliance.gui.conflict_replace"),
				Component.translatable("allymod.alliance.gui.conflict_add_new")));
	}

	private void copyFiles(List<Path> paths, boolean replaceExisting) {
		AllianceStore alliance = Allymod.alliance();
		int added = 0;
		List<String> errors = new ArrayList<>();
		for (Path path : paths) {
			try {
				String name = alliance.addConfig(path, replaceExisting);
				Allymod.LOGGER.info("Dodano config sojuszu {} (z {})", name, path);
				added++;
			} catch (IOException e) {
				Allymod.LOGGER.warn("Nie dodano configu sojuszu {}: {}", path, e.getMessage());
				errors.add(e.getMessage());
			}
		}
		if (added > 0) reloadQuietly();

		if (errors.isEmpty()) {
			setStatus(Component.translatable("allymod.alliance.gui.added", added, alliance.size()), COLOR_OK);
		} else {
			setStatus(Component.translatable("allymod.alliance.gui.add_failed", errors.size(), errors.get(0)), COLOR_ERROR);
		}
		rebuildWidgets();
	}

	/** Wolane z {@link AllianceLinkScreen} po wpisaniu linku. */
	void addLink(String url) {
		setStatus(Component.translatable("allymod.alliance.gui.link_downloading"), COLOR_INFO);
		AllianceUpdater.addLink(url, summary -> {
			if (summary.link() == null) {
				setStatus(Component.translatable("allymod.alliance.gui.link_failed", summary.errors().get(0)), COLOR_ERROR);
			} else if (AllianceLinks.isExpiringDiscordLink(summary.link().url())) {
				setStatus(Component.translatable("allymod.alliance.gui.link_added_discord", summary.link().file()), COLOR_WARN);
			} else {
				setStatus(Component.translatable("allymod.alliance.gui.link_added", summary.link().file(),
						Allymod.alliance().size()), COLOR_OK);
			}
			rebuildWidgets();
		});
	}

	private void reload() {
		if (Allymod.allianceLinks().all().isEmpty()) {
			showReloadResult(reloadQuietly(), List.of());
			rebuildWidgets();
			return;
		}
		// najpierw pobierz nowe wersje z linkow, potem przeladuj wszystko
		setStatus(Component.translatable("allymod.alliance.gui.link_downloading"), COLOR_INFO);
		AllianceUpdater.refreshAll(summary -> {
			showReloadResult(reloadQuietly(), summary.errors());
			rebuildWidgets();
		});
	}

	private void showReloadResult(AllianceStore.ReloadResult result, List<String> linkErrors) {
		if (result == null) return;
		if (!result.failed().isEmpty()) {
			AllianceStore.ConfigInfo first = result.failed().get(0);
			setStatus(Component.translatable("allymod.alliance.gui.reload_failed", first.fileName(), first.error()),
					COLOR_ERROR);
		} else if (!linkErrors.isEmpty()) {
			setStatus(Component.translatable("allymod.alliance.gui.link_refresh_failed", linkErrors.get(0)), COLOR_WARN);
		} else {
			setStatus(Component.translatable("allymod.alliance.gui.reloaded", result.players(), result.configs()),
					COLOR_OK);
		}
	}

	private AllianceStore.ReloadResult reloadQuietly() {
		try {
			return Allymod.reloadAlliance();
		} catch (IOException e) {
			setStatus(Component.translatable("allymod.command.sojusz.load_failed", e.getMessage(), Allymod.alliance().size()),
					COLOR_ERROR);
			return null;
		}
	}

	private void confirmRemove(String fileName) {
		Minecraft mc = Minecraft.getInstance();
		mc.setScreenAndShow(new ConfirmScreen(confirmed -> {
			if (confirmed) removeConfig(fileName);
			mc.setScreenAndShow(this);
		},
				Component.translatable("allymod.alliance.gui.remove_title"),
				Component.translatable("allymod.alliance.gui.remove_message", fileName)));
	}

	private void removeConfig(String fileName) {
		try {
			// najpierw link — inaczej nastepne odswiezenie pobraloby config z powrotem
			Allymod.allianceLinks().remove(fileName);
			Allymod.alliance().removeConfig(fileName);
			Allymod.LOGGER.info("Usunieto config sojuszu {}", fileName);
		} catch (IOException e) {
			Allymod.LOGGER.error("Nie udalo sie usunac configu {}", fileName, e);
			setStatus(Component.translatable("allymod.alliance.gui.remove_failed", fileName, e.getMessage()), COLOR_ERROR);
			return;
		}
		reloadQuietly();
		setStatus(Component.translatable("allymod.alliance.gui.removed", fileName), COLOR_INFO);
	}

	private void openFolder() {
		AllianceStore alliance = Allymod.alliance();
		try {
			Files.createDirectories(alliance.dir());
		} catch (IOException e) {
			Allymod.LOGGER.error("Nie udalo sie utworzyc {}", alliance.dir(), e);
		}
		Util.getPlatform().openPath(alliance.dir());
	}

	private void setStatus(Component message, int color) {
		this.status = message;
		this.statusColor = color;
	}

	private static int pageCount(int configs) {
		return Math.max(1, (configs + MAX_ROWS - 1) / MAX_ROWS);
	}

	private String truncate(Font font, String s, int maxWidth) {
		if (font.width(s) <= maxWidth) return s;
		return font.plainSubstrByWidth(s, maxWidth - font.width("...")) + "...";
	}
}
