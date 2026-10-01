package dev.servergunnik.allymod.cache;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.PlayerEntity;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import dev.servergunnik.allymod.Allymod;
import dev.servergunnik.allymod.data.Relation;
import dev.servergunnik.allymod.data.RelationKind;
import dev.servergunnik.allymod.data.RelationStore;

public final class RelationCache {
	public static final int ARGB_ALLY  = 0xFF33DD33;
	public static final int ARGB_ENEMY = 0xFFDD3333;

	public record RenderStatus(RelationKind kind, int argb) {}

	private static final RenderStatus ALLY  = new RenderStatus(RelationKind.ALLY,  ARGB_ALLY);
	private static final RenderStatus ENEMY = new RenderStatus(RelationKind.ENEMY, ARGB_ENEMY);

	private final RelationStore store;
	private final Map<UUID, RenderStatus> statusByUuid = new ConcurrentHashMap<>();
	private volatile boolean dirty = false;

	public RelationCache(RelationStore store) {
		this.store = store;
	}

	public void rebuild() {
		statusByUuid.clear();
		for (Relation r : store.all()) refresh(r.uuid(), r.kind());
	}

	public void refresh(UUID uuid, RelationKind kind) {
		if (kind == null) {
			statusByUuid.remove(uuid);
		} else {
			statusByUuid.put(uuid, kind == RelationKind.ALLY ? ALLY : ENEMY);
		}
	}

	public RenderStatus statusOf(UUID uuid) {
		return statusByUuid.get(uuid);
	}

	public int size() {
		return statusByUuid.size();
	}

	public RelationStore store() {
		return store;
	}

	public void markDirty() {
		dirty = true;
	}

	public void saveIfDirty() {
		if (!dirty) return;
		try {
			store.save();
			dirty = false;
		} catch (IOException e) {
			Allymod.LOGGER.error("Nie udalo sie zapisac {}", store.file(), e);
		}
	}

	public void registerEvents() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> saveIfDirty());

		ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (!(entity instanceof PlayerEntity player)) return;
			UUID uuid = player.getUuid();
			Relation existing = store.get(uuid).orElse(null);
			if (existing == null) return;
			String currentName = player.getName().getString();
			if (!Objects.equals(existing.lastKnownName(), currentName)) {
				store.put(uuid, currentName, existing.kind());
				markDirty();
			}
		});
	}
}
