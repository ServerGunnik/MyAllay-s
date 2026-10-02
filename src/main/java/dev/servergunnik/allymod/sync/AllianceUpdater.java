package dev.servergunnik.allymod.sync;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import dev.servergunnik.allymod.Allymod;
import dev.servergunnik.allymod.data.AllianceLinks;

/**
 * Pobieranie configow sojuszu z linkow w tle: kilka sekund po starcie gry,
 * potem co 10 minut i na zadanie (GUI / komenda). Siec nigdy nie blokuje
 * render threada — po pobraniu przeladowanie idzie na watek gry.
 */
public final class AllianceUpdater {
	private static final long FIRST_CHECK_TICKS = 20L * 5;
	private static final long INTERVAL_TICKS = 20L * 60 * 10;

	/** Wynik pobrania; errors = "plik: blad". Dla addLink link != null po sukcesie. */
	public record Summary(int updated, int unchanged, List<String> errors, AllianceLinks.Link link) {}

	private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "MyAllay's alliance download");
		t.setDaemon(true);
		return t;
	});
	private static final AtomicBoolean periodicRunning = new AtomicBoolean(false);
	private static long ticksUntilCheck = FIRST_CHECK_TICKS;

	private AllianceUpdater() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (--ticksUntilCheck > 0) return;
			ticksUntilCheck = INTERVAL_TICKS;
			if (Allymod.allianceLinks().all().isEmpty()) return;
			if (!periodicRunning.compareAndSet(false, true)) return;
			refreshAll(summary -> periodicRunning.set(false));
		});
	}

	/** Pobiera wszystkie linki. onDone dostaje wynik na watku gry. */
	public static void refreshAll(Consumer<Summary> onDone) {
		List<AllianceLinks.Link> links = Allymod.allianceLinks().all();
		EXECUTOR.execute(() -> {
			int updated = 0;
			int unchanged = 0;
			List<String> errors = new ArrayList<>();
			for (AllianceLinks.Link link : links) {
				try {
					if (Allymod.allianceLinks().fetch(link) == AllianceLinks.FetchOutcome.UPDATED) {
						updated++;
						Allymod.LOGGER.info("Config sojuszu {} zaktualizowany z {}", link.file(), link.url());
					} else {
						unchanged++;
					}
				} catch (IOException e) {
					Allymod.LOGGER.warn("Nie udalo sie pobrac {} z {}: {} — zostaje poprzednia wersja",
							link.file(), link.url(), e.getMessage());
					errors.add(link.file() + ": " + e.getMessage());
				}
			}
			finish(new Summary(updated, unchanged, List.copyOf(errors), null), onDone);
		});
	}

	/** Dodaje link (sprawdza i pobiera config). onDone dostaje wynik na watku gry. */
	public static void addLink(String url, Consumer<Summary> onDone) {
		EXECUTOR.execute(() -> {
			Summary summary;
			try {
				AllianceLinks.Link link = Allymod.allianceLinks().addAndFetch(url);
				Allymod.LOGGER.info("Dodano config sojuszu z linku {} -> {}", link.url(), link.file());
				summary = new Summary(1, 0, List.of(), link);
			} catch (IOException e) {
				Allymod.LOGGER.warn("Nie dodano configu z linku {}: {}", url, e.getMessage());
				summary = new Summary(0, 0, List.of(e.getMessage()), null);
			}
			finish(summary, onDone);
		});
	}

	private static void finish(Summary summary, Consumer<Summary> onDone) {
		Minecraft.getInstance().execute(() -> {
			if (summary.updated() > 0) {
				try {
					Allymod.reloadAlliance();
				} catch (IOException e) {
					// zalogowane w reloadAlliance
				}
			}
			if (onDone != null) onDone.accept(summary);
		});
	}
}
