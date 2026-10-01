package dev.servergunnik.allymod.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AllianceStoreTest {
	private static final String SAMPLE = """
			[
			  { "nick": "KrolPolski", "allay": true,  "status": "Król",     "kingdom": "Polska" },
			  { "nick": "Zastepca1",  "allay": true,  "status": "Zastępca", "kingdom": "Polska" },
			  { "nick": "StaryGracz", "allay": true,  "status": "Członek",  "kingdom": "Polska" },
			  { "nick": "KrolNiemiec","allay": false, "status": "Król",     "kingdom": "Niemcy" }
			]
			""";

	@Test
	void loadsSampleWithPolishCharacters(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("snz-sojusz.json");
		Files.writeString(file, SAMPLE, StandardCharsets.UTF_8);

		AllianceStore s = new AllianceStore(file);
		AllianceStore.LoadResult result = s.load();

		assertEquals(4, result.loaded());
		assertTrue(result.warnings().isEmpty());
		AllianceEntry krol = s.get("KrolPolski");
		assertEquals(AllianceStatus.KING, krol.status());
		assertEquals("[Polska • Król] ", krol.label());
		assertEquals(AllianceStatus.DEPUTY, s.get("Zastepca1").status());
		assertEquals("[Polska • Członek] ", s.get("StaryGracz").label());
		assertFalse(s.get("KrolNiemiec").allay());
	}

	@Test
	void lookupIgnoresCase(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("snz-sojusz.json");
		Files.writeString(file, SAMPLE, StandardCharsets.UTF_8);
		AllianceStore s = new AllianceStore(file);
		s.load();

		assertEquals("KrolPolski", s.get("krolpolski").nick());
		assertEquals("KrolPolski", s.get("KROLPOLSKI").nick());
		assertNull(s.get("Notch"));
	}

	@Test
	void missingFileIsCreatedAsEmptyArray(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("snz-sojusz.json");
		AllianceStore s = new AllianceStore(file);

		assertEquals(0, s.load().loaded());
		assertTrue(Files.exists(file));
		assertEquals("[]", Files.readString(file).trim());
	}

	@Test
	void corruptedJsonKeepsPreviousList(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("snz-sojusz.json");
		Files.writeString(file, SAMPLE, StandardCharsets.UTF_8);
		AllianceStore s = new AllianceStore(file);
		s.load();

		Files.writeString(file, "[ { \"nick\": \"Zepsuty\", ", StandardCharsets.UTF_8);
		IOException e = assertThrows(IOException.class, s::load);
		assertTrue(e.getMessage().contains("uszkodzony"));
		assertEquals(4, s.size());
		assertEquals("Polska", s.get("KrolPolski").kingdom());
	}

	@Test
	void rootThatIsNotArrayIsRejected(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("snz-sojusz.json");
		Files.writeString(file, "{ \"nick\": \"KrolPolski\" }", StandardCharsets.UTF_8);
		AllianceStore s = new AllianceStore(file);

		assertThrows(IOException.class, s::load);
		assertEquals(0, s.size());
	}

	@Test
	void invalidEntriesAreSkippedRestIsLoaded(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("snz-sojusz.json");
		Files.writeString(file, """
				[
				  { "nick": "Dobry_1", "allay": true, "status": "Król", "kingdom": "Nowa Kraina" },
				  { "allay": true, "status": "Król", "kingdom": "Polska" },
				  { "nick": "ZlyTyp", "allay": "true", "status": "Król", "kingdom": "Polska" },
				  { "nick": "Literowka", "ally": true, "status": "Król", "kingdom": "Polska" },
				  { "nick": "a", "allay": true, "status": "Król", "kingdom": "Polska" },
				  { "nick": "Zly-Nick", "allay": true, "status": "Król", "kingdom": "Polska" },
				  { "nick": "BezPanstwa", "allay": true, "status": "Król" },
				  42,
				  { "nick": "dobry_1", "allay": false, "status": "Członek", "kingdom": "Duplikat" },
				  { "nick": "Nieznany", "allay": false, "status": "Cesarz", "kingdom": "Niemcy" }
				]
				""", StandardCharsets.UTF_8);
		AllianceStore s = new AllianceStore(file);
		AllianceStore.LoadResult result = s.load();

		assertEquals(2, result.loaded());
		assertEquals(9, result.warnings().size());
		assertEquals("Nowa Kraina", s.get("DOBRY_1").kingdom());
		assertEquals(AllianceStatus.MEMBER, s.get("Nieznany").status());
		assertTrue(result.warnings().stream().anyMatch(w -> w.contains("'ally'")));
	}

	@Test
	void utf8BomIsAccepted(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("snz-sojusz.json");
		Files.writeString(file, "﻿" + SAMPLE, StandardCharsets.UTF_8);
		AllianceStore s = new AllianceStore(file);

		assertEquals(4, s.load().loaded());
		assertEquals(AllianceStatus.MEMBER, s.get("StaryGracz").status());
	}

	@Test
	void nonUtf8FileIsRejected(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("snz-sojusz.json");
		Files.write(file, SAMPLE.getBytes(java.nio.charset.Charset.forName("windows-1250")));
		AllianceStore s = new AllianceStore(file);

		IOException e = assertThrows(IOException.class, s::load);
		assertTrue(e.getMessage().contains("UTF-8"));
	}
}
