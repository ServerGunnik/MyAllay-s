package dev.servergunnik.allymod.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RelationStoreTest {

	@Test
	void savesAndReloadsSingleRelation(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("relations.json");
		UUID uuid = UUID.randomUUID();

		RelationStore a = new RelationStore(file);
		a.load();
		a.put(uuid, "Notch", RelationKind.ALLY);
		a.save();

		assertTrue(Files.exists(file));
		assertTrue(Files.readString(file).contains("Notch"));

		RelationStore b = new RelationStore(file);
		b.load();
		assertTrue(b.isAlly(uuid));
		assertEquals("Notch", b.get(uuid).orElseThrow().lastKnownName());
	}

	@Test
	void removeDropsRelation(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("relations.json");
		UUID uuid = UUID.randomUUID();

		RelationStore s = new RelationStore(file);
		s.load();
		s.put(uuid, "Steve", RelationKind.ENEMY);
		s.save();

		assertTrue(s.remove(uuid));
		s.save();

		RelationStore reloaded = new RelationStore(file);
		reloaded.load();
		assertFalse(reloaded.get(uuid).isPresent());
	}

	@Test
	void loadFromMissingFileIsEmpty(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("nie-ma-mnie.json");
		RelationStore s = new RelationStore(file);
		s.load();
		assertEquals(0, s.size());
	}

	@Test
	void byKindFiltersAndSortsAlphabetically(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("relations.json");
		RelationStore s = new RelationStore(file);
		s.load();
		s.put(UUID.randomUUID(), "zeta",   RelationKind.ALLY);
		s.put(UUID.randomUUID(), "Alfa",   RelationKind.ALLY);
		s.put(UUID.randomUUID(), "wrog1",  RelationKind.ENEMY);

		var allies = s.byKind(RelationKind.ALLY);
		assertEquals(2, allies.size());
		assertEquals("Alfa", allies.get(0).lastKnownName());
		assertEquals("zeta", allies.get(1).lastKnownName());
		assertEquals(1, s.byKind(RelationKind.ENEMY).size());
	}

	@Test
	void putOverwritesExistingAndUpdatesTimestamp(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("relations.json");
		UUID uuid = UUID.randomUUID();
		RelationStore s = new RelationStore(file);
		s.load();

		Relation first = s.put(uuid, "Nick1", RelationKind.ALLY);
		// odczekaj klatke wall-clocka zeby zmienil sie updatedAt
		try { Thread.sleep(2); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
		Relation second = s.put(uuid, "Nick2", RelationKind.ENEMY);

		assertEquals(1, s.size());
		assertEquals("Nick2", s.get(uuid).orElseThrow().lastKnownName());
		assertEquals(RelationKind.ENEMY, s.get(uuid).orElseThrow().kind());
		assertNotEquals(first.updatedAtMillis(), second.updatedAtMillis());
	}

	@Test
	void corruptedJsonThrowsIoException(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("relations.json");
		Files.writeString(file, "{ to nie jest json ");

		RelationStore s = new RelationStore(file);
		try {
			s.load();
			assertTrue(false, "Powinien poleciec IOException");
		} catch (IOException expected) {
			assertTrue(expected.getMessage().contains("uszkodzony"));
		}
	}
}
