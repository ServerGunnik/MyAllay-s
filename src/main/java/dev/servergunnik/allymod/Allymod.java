package dev.servergunnik.allymod;

import java.io.IOException;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.Identifier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import dev.servergunnik.allymod.cache.RelationCache;
import dev.servergunnik.allymod.command.Commands;
import dev.servergunnik.allymod.data.AllianceStore;
import dev.servergunnik.allymod.data.RelationStore;
import dev.servergunnik.allymod.input.AllymodKeybinds;

public class Allymod implements ClientModInitializer {
	public static final String MOD_ID = "allymod";
	public static final Logger LOGGER = LoggerFactory.getLogger("MyAllay's");

	private static RelationStore store;
	private static RelationCache cache;
	private static AllianceStore alliance;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static RelationStore relations() {
		return store;
	}

	public static RelationCache cache() {
		return cache;
	}

	public static AllianceStore alliance() {
		return alliance;
	}

	/**
	 * Wczytuje (ponownie) wszystkie configi sojuszu z config/snz-sojusz/
	 * i loguje pominiete wpisy oraz uszkodzone pliki.
	 */
	public static AllianceStore.ReloadResult reloadAlliance() throws IOException {
		try {
			AllianceStore.ReloadResult result = alliance.reload();
			for (AllianceStore.ConfigInfo info : alliance.configs()) {
				for (String warning : info.warnings()) {
					LOGGER.warn("{}: {}", info.fileName(), warning);
				}
				if (!info.ok()) {
					LOGGER.error("Nie udalo sie wczytac {}: {} — zostaje poprzednia wersja ({} graczy)",
							info.fileName(), info.error(), info.players());
				}
			}
			LOGGER.info("Sojusz wczytany — {} graczy z {} configow w {} (bledne pliki: {}, pominiete wpisy: {})",
					result.players(), result.configs(), alliance.dir(), result.failed().size(), result.warnings());
			return result;
		} catch (IOException e) {
			LOGGER.error("Nie udalo sie odczytac folderu {} — zostaje poprzednia lista ({} graczy)",
					alliance.dir(), alliance.size(), e);
			throw e;
		}
	}

	@Override
	public void onInitializeClient() {
		Path file = FabricLoader.getInstance().getConfigDir()
				.resolve(MOD_ID)
				.resolve("relations.json");
		store = new RelationStore(file);
		try {
			store.load();
			LOGGER.info("MyAllay's loaded — {} relacji z {}", store.size(), file);
		} catch (IOException e) {
			LOGGER.error("Nie udalo sie wczytac {} — start z pusta lista", file, e);
		}

		alliance = new AllianceStore(FabricLoader.getInstance().getConfigDir().resolve("snz-sojusz"));
		try {
			reloadAlliance();
		} catch (IOException e) {
			// juz zalogowane w reloadAlliance — gra startuje bez configow sojuszu
		}

		cache = new RelationCache(store);
		cache.rebuild();
		cache.registerEvents();
		LOGGER.info("Cache MyAllay's zbudowany — {} wpisow, event hooki (JOIN/ENTITY_LOAD/DISCONNECT) zarejestrowane", cache.size());

		Commands.register();
		LOGGER.info("Komendy zarejestrowane: /ally, /enemy, /allymod, /snzsojusz");

		AllymodKeybinds.register();
	}
}
