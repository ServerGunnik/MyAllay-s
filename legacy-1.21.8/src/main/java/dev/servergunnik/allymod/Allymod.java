package dev.servergunnik.allymod;

import java.io.IOException;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.util.Identifier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import dev.servergunnik.allymod.cache.RelationCache;
import dev.servergunnik.allymod.command.Commands;
import dev.servergunnik.allymod.data.RelationStore;
import dev.servergunnik.allymod.input.AllymodKeybinds;

public class Allymod implements ClientModInitializer {
	public static final String MOD_ID = "allymod";
	public static final Logger LOGGER = LoggerFactory.getLogger("MyAllay's");

	private static RelationStore store;
	private static RelationCache cache;

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	public static RelationStore relations() {
		return store;
	}

	public static RelationCache cache() {
		return cache;
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

		cache = new RelationCache(store);
		cache.rebuild();
		cache.registerEvents();
		LOGGER.info("Cache MyAllay's zbudowany — {} wpisow, event hooki zarejestrowane", cache.size());

		Commands.register();
		LOGGER.info("Komendy zarejestrowane: /ally, /enemy, /allymod");

		AllymodKeybinds.register();
	}
}
