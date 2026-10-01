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
	 * Wczytuje (ponownie) config/snz-sojusz.json i loguje pominiete wpisy.
	 * Przy uszkodzonym pliku rzuca IOException — poprzednia lista zostaje.
	 */
	public static AllianceStore.LoadResult reloadAlliance() throws IOException {
		try {
			AllianceStore.LoadResult result = alliance.load();
			for (String warning : result.warnings()) {
				LOGGER.warn("{}: {}", alliance.file().getFileName(), warning);
			}
			LOGGER.info("Sojusz wczytany — {} graczy z {} (pominieto {})",
					result.loaded(), alliance.file(), result.warnings().size());
			return result;
		} catch (IOException e) {
			LOGGER.error("Nie udalo sie wczytac {} — zostaje poprzednia lista ({} graczy)",
					alliance.file(), alliance.size(), e);
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

		alliance = new AllianceStore(FabricLoader.getInstance().getConfigDir().resolve("snz-sojusz.json"));
		try {
			reloadAlliance();
		} catch (IOException e) {
			// juz zalogowane w reloadAlliance — gra startuje z pusta lista sojuszu
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
