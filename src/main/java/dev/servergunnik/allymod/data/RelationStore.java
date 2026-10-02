package dev.servergunnik.allymod.data;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

public final class RelationStore {
	private static final int SCHEMA_VERSION = 1;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path file;
	private final Map<UUID, Relation> byUuid = new ConcurrentHashMap<>();

	public RelationStore(Path file) {
		this.file = file;
	}

	public Path file() {
		return file;
	}

	/**
	 * Wczytuje plik. Przy uszkodzonym pliku rzuca IOException, zostawia
	 * poprzednio wczytana liste i kopiuje zepsuty plik do relations.json.broken
	 * — kolejny zapis nadpisze relations.json, wiec bez kopii dane by przepadly.
	 */
	public synchronized void load() throws IOException {
		if (!Files.exists(file)) {
			byUuid.clear();
			return;
		}

		Map<UUID, Relation> loaded = new HashMap<>();
		try (Reader reader = Files.newBufferedReader(file)) {
			FileFormat format = GSON.fromJson(reader, FileFormat.class);
			if (format != null && format.relations != null) {
				for (Relation r : format.relations) {
					if (r == null || r.uuid() == null || r.kind() == null) continue;
					loaded.put(r.uuid(), r);
				}
			}
		} catch (JsonParseException e) {
			Path backup = backupFile();
			Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
			throw new IOException("relations.json jest uszkodzony (kopia: " + backup.getFileName() + "): "
					+ e.getMessage(), e);
		}
		byUuid.clear();
		byUuid.putAll(loaded);
	}

	public Path backupFile() {
		return file.resolveSibling(file.getFileName() + ".broken");
	}

	public synchronized void save() throws IOException {
		Path parent = file.getParent();
		if (parent != null) Files.createDirectories(parent);

		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		FileFormat out = new FileFormat();
		out.schemaVersion = SCHEMA_VERSION;
		out.relations = new ArrayList<>(byUuid.values());
		out.relations.sort(Comparator.comparing(Relation::lastKnownName, String.CASE_INSENSITIVE_ORDER));

		try (Writer writer = Files.newBufferedWriter(tmp)) {
			GSON.toJson(out, writer);
		}
		Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
	}

	public Optional<Relation> get(UUID uuid) {
		return Optional.ofNullable(byUuid.get(uuid));
	}

	public boolean isAlly(UUID uuid) {
		Relation r = byUuid.get(uuid);
		return r != null && r.kind() == RelationKind.ALLY;
	}

	public boolean isEnemy(UUID uuid) {
		Relation r = byUuid.get(uuid);
		return r != null && r.kind() == RelationKind.ENEMY;
	}

	public Relation put(UUID uuid, String name, RelationKind kind) {
		Relation r = new Relation(uuid, name, kind, System.currentTimeMillis());
		byUuid.put(uuid, r);
		return r;
	}

	public boolean remove(UUID uuid) {
		return byUuid.remove(uuid) != null;
	}

	public List<Relation> all() {
		return List.copyOf(byUuid.values());
	}

	public List<Relation> byKind(RelationKind kind) {
		return byUuid.values().stream()
				.filter(r -> r.kind() == kind)
				.sorted(Comparator.comparing(Relation::lastKnownName, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	public int size() {
		return byUuid.size();
	}

	private static final class FileFormat {
		int schemaVersion;
		List<Relation> relations;
	}
}
