package dev.servergunnik.allymod.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.Charset;
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

	private static final String SECOND = """
			[
			  { "nick": "Wiking_7",   "allay": true,  "status": "Król",    "kingdom": "Norwegia" },
			  { "nick": "krolpolski", "allay": false, "status": "Członek", "kingdom": "Inne" }
			]
			""";

	private static Path write(Path dir, String name, String content) throws IOException {
		Files.createDirectories(dir);
		Path file = dir.resolve(name);
		Files.writeString(file, content, StandardCharsets.UTF_8);
		return file;
	}

	@Test
	void loadsSampleWithPolishCharacters(@TempDir Path tmp) throws IOException {
		Path dir = tmp.resolve("snz-sojusz");
		write(dir, "polska.json", SAMPLE);

		AllianceStore s = new AllianceStore(dir);
		AllianceStore.ReloadResult result = s.reload();

		assertEquals(4, result.players());
		assertEquals(1, result.configs());
		assertEquals(0, result.warnings());
		AllianceEntry krol = s.get("KrolPolski");
		assertEquals(AllianceStatus.KING, krol.status());
		assertEquals("[Polska • Król] ", krol.label());
		assertEquals(AllianceStatus.DEPUTY, s.get("Zastepca1").status());
		assertEquals("[Polska • Członek] ", s.get("StaryGracz").label());
		assertFalse(s.get("KrolNiemiec").allay());
	}

	@Test
	void lookupIgnoresCase(@TempDir Path tmp) throws IOException {
		Path dir = tmp.resolve("snz-sojusz");
		write(dir, "polska.json", SAMPLE);
		AllianceStore s = new AllianceStore(dir);
		s.reload();

		assertEquals("KrolPolski", s.get("krolpolski").nick());
		assertEquals("KrolPolski", s.get("KROLPOLSKI").nick());
		assertNull(s.get("Notch"));
	}

	@Test
	void missingFolderIsCreatedAndEmpty(@TempDir Path tmp) throws IOException {
		Path dir = tmp.resolve("snz-sojusz");
		AllianceStore s = new AllianceStore(dir);

		assertEquals(0, s.reload().players());
		assertTrue(Files.isDirectory(dir));
		assertTrue(s.configs().isEmpty());
	}

	@Test
	void multipleConfigsAreMergedFirstAlphabeticalWins(@TempDir Path tmp) throws IOException {
		Path dir = tmp.resolve("snz-sojusz");
		write(dir, "b-polska.json", SAMPLE);
		write(dir, "a-polnoc.json", SECOND);
		write(dir, "notatki.txt", "to nie jest config");
		AllianceStore s = new AllianceStore(dir);
		AllianceStore.ReloadResult result = s.reload();

		assertEquals(2, result.configs());
		assertEquals(5, result.players());
		assertEquals("a-polnoc.json", s.configs().get(0).fileName());
		assertEquals("Norwegia", s.get("wiking_7").kingdom());
		// KrolPolski jest w obu — wygrywa a-polnoc.json
		assertEquals("Inne", s.get("KrolPolski").kingdom());
		assertEquals(3, s.configs().get(1).players());
		assertEquals(1, s.configs().get(1).warnings().size());
	}

	@Test
	void corruptedConfigKeepsItsPreviousVersionOthersStillLoad(@TempDir Path tmp) throws IOException {
		Path dir = tmp.resolve("snz-sojusz");
		Path polska = write(dir, "polska.json", SAMPLE);
		AllianceStore s = new AllianceStore(dir);
		s.reload();

		Files.writeString(polska, "[ { \"nick\": \"Zepsuty\", ", StandardCharsets.UTF_8);
		write(dir, "polnoc.json", "[ { \"nick\": \"Wiking_7\", \"allay\": true, \"status\": \"Król\", \"kingdom\": \"Norwegia\" } ]");
		AllianceStore.ReloadResult result = s.reload();

		assertEquals(1, result.failed().size());
		assertEquals("polska.json", result.failed().get(0).fileName());
		assertTrue(result.failed().get(0).error().contains("uszkodzony"));
		assertEquals(5, s.size());
		assertEquals("Polska", s.get("KrolPolski").kingdom());
		assertEquals("Norwegia", s.get("Wiking_7").kingdom());
	}

	@Test
	void corruptedConfigWithoutPreviousVersionGivesNoPlayers(@TempDir Path tmp) throws IOException {
		Path dir = tmp.resolve("snz-sojusz");
		write(dir, "zly.json", "{ \"nick\": \"KrolPolski\" }");
		AllianceStore s = new AllianceStore(dir);
		AllianceStore.ReloadResult result = s.reload();

		assertEquals(0, s.size());
		assertEquals(1, result.failed().size());
		assertFalse(s.configs().get(0).ok());
	}

	@Test
	void addConfigCopiesFileAndReplacesSameName(@TempDir Path tmp) throws IOException {
		Path dir = tmp.resolve("snz-sojusz");
		Path downloads = tmp.resolve("Pobrane");
		AllianceStore s = new AllianceStore(dir);
		s.reload();

		assertEquals("sojusz.json", s.addConfig(write(downloads, "sojusz.json", SAMPLE)));
		s.reload();
		assertEquals(4, s.size());
		assertTrue(Files.exists(dir.resolve("sojusz.json")));

		// nowa wersja z bota pod ta sama nazwa podmienia stara
		s.addConfig(write(downloads, "sojusz.json", SECOND));
		s.reload();
		assertEquals(1, s.configs().size());
		assertEquals(2, s.size());
	}

	@Test
	void addConfigRejectsCorruptedFileAndCopiesNothing(@TempDir Path tmp) throws IOException {
		Path dir = tmp.resolve("snz-sojusz");
		AllianceStore s = new AllianceStore(dir);
		s.reload();

		Path bad = write(tmp.resolve("Pobrane"), "zly.json", "[ { ");
		assertThrows(IOException.class, () -> s.addConfig(bad));
		assertFalse(Files.exists(dir.resolve("zly.json")));
	}

	@Test
	void removeConfigDeletesFile(@TempDir Path tmp) throws IOException {
		Path dir = tmp.resolve("snz-sojusz");
		write(dir, "polska.json", SAMPLE);
		write(dir, "polnoc.json", SECOND);
		AllianceStore s = new AllianceStore(dir);
		s.reload();

		assertTrue(s.removeConfig("polska.json"));
		s.reload();
		assertFalse(Files.exists(dir.resolve("polska.json")));
		assertEquals(2, s.size());
		assertNull(s.get("Zastepca1"));
		assertThrows(IOException.class, () -> s.removeConfig("../cos.json"));
	}

	@Test
	void invalidEntriesAreSkippedRestIsLoaded(@TempDir Path tmp) throws IOException {
		Path file = write(tmp, "test.json", """
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
				""");
		AllianceFileParser.Parsed parsed = AllianceFileParser.parse(file);

		assertEquals(2, parsed.entries().size());
		assertEquals(9, parsed.warnings().size());
		assertEquals("Nowa Kraina", parsed.entries().get(0).kingdom());
		assertEquals(AllianceStatus.MEMBER, parsed.entries().get(1).status());
		assertTrue(parsed.warnings().stream().anyMatch(w -> w.contains("'ally'")));
	}

	@Test
	void utf8BomIsAccepted(@TempDir Path tmp) throws IOException {
		Path file = write(tmp, "bom.json", "﻿" + SAMPLE);
		assertEquals(4, AllianceFileParser.parse(file).entries().size());
	}

	@Test
	void nonUtf8FileIsRejected(@TempDir Path tmp) throws IOException {
		Path file = tmp.resolve("cp1250.json");
		Files.write(file, SAMPLE.getBytes(Charset.forName("windows-1250")));

		IOException e = assertThrows(IOException.class, () -> AllianceFileParser.parse(file));
		assertTrue(e.getMessage().contains("UTF-8"));
	}

	@Test
	void kingdomIsSanitized() {
		assertEquals("Polska", AllianceFileParser.cleanKingdom("§c§lPolska§r"));
		assertEquals("Nowa Kraina", AllianceFileParser.cleanKingdom("  Nowa \t\n Kraina "));
		assertEquals("Królestwo Bardzo Długie…", AllianceFileParser.cleanKingdom("Królestwo Bardzo Długiej Nazwy Państwa"));
		assertEquals("", AllianceFileParser.cleanKingdom("§a"));
	}
}
