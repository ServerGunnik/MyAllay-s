package dev.servergunnik.allymod.data;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * Parser jednego pliku sojuszu w formacie bota Discord:
 * [ { "nick": ..., "allay": true/false, "status": "Król|Zastępca|Członek", "kingdom": ... }, ... ]
 */
public final class AllianceFileParser {
	public static final Pattern NICK_PATTERN = Pattern.compile("[A-Za-z0-9_]{3,16}");
	public static final int MAX_KINGDOM_LENGTH = 24;

	/** Poprawne wpisy w kolejnosci z pliku (bez duplikatow) + opisy pominietych. */
	public record Parsed(List<AllianceEntry> entries, List<String> warnings) {}

	private AllianceFileParser() {}

	/**
	 * Rzuca IOException, gdy pliku nie da sie uzyc w calosci (zle kodowanie,
	 * uszkodzony JSON, korzen inny niz tablica). Bledne pojedyncze wpisy
	 * sa pomijane i opisane w {@link Parsed#warnings()}.
	 */
	public static Parsed parse(Path file) throws IOException {
		String name = file.getFileName().toString();
		String content;
		try {
			content = Files.readString(file, StandardCharsets.UTF_8);
		} catch (CharacterCodingException e) {
			throw new IOException(name + " nie jest zapisany w UTF-8", e);
		}
		if (!content.isEmpty() && content.charAt(0) == '﻿') content = content.substring(1);

		JsonElement root;
		try {
			root = JsonParser.parseString(content);
		} catch (JsonParseException e) {
			throw new IOException(name + " jest uszkodzony: " + e.getMessage(), e);
		}
		if (!root.isJsonArray()) {
			throw new IOException(name + " jest uszkodzony: oczekiwano tablicy JSON [ ... ]");
		}

		List<String> warnings = new ArrayList<>();
		List<AllianceEntry> entries = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		JsonArray array = root.getAsJsonArray();
		for (int i = 0; i < array.size(); i++) {
			AllianceEntry entry = parseEntry(i, array.get(i), warnings);
			if (entry == null) continue;
			if (!seen.add(entry.nick().toLowerCase(Locale.ROOT))) {
				warnings.add("wpis #" + i + ": gracz '" + entry.nick() + "' wystepuje drugi raz — pomijam duplikat");
				continue;
			}
			entries.add(entry);
		}
		return new Parsed(List.copyOf(entries), List.copyOf(warnings));
	}

	private static AllianceEntry parseEntry(int index, JsonElement element, List<String> warnings) {
		String where = "wpis #" + index;
		if (!element.isJsonObject()) {
			warnings.add(where + ": to nie jest obiekt JSON — pomijam");
			return null;
		}
		JsonObject obj = element.getAsJsonObject();

		String nick = stringField(obj, "nick");
		if (nick == null) {
			warnings.add(where + ": brak pola 'nick' (albo nie jest tekstem) — pomijam");
			return null;
		}
		nick = nick.trim();
		where += " (" + nick + ")";
		if (!NICK_PATTERN.matcher(nick).matches()) {
			warnings.add(where + ": niepoprawny nick (3-16 znakow a-z A-Z 0-9 _) — pomijam");
			return null;
		}

		JsonElement allayEl = obj.get("allay");
		if (!(allayEl instanceof JsonPrimitive p && p.isBoolean())) {
			String hint = allayEl == null && obj.has("ally") ? " (w pliku jest 'ally' — poprawny klucz to 'allay')" : "";
			warnings.add(where + ": pole 'allay' musi byc true/false" + hint + " — pomijam");
			return null;
		}
		boolean allay = allayEl.getAsBoolean();

		String kingdom = cleanKingdom(stringField(obj, "kingdom"));
		if (kingdom == null || kingdom.isEmpty()) {
			warnings.add(where + ": brak pola 'kingdom' (albo jest puste) — pomijam");
			return null;
		}

		String rawStatus = stringField(obj, "status");
		AllianceStatus status = AllianceStatus.fromDisplayName(rawStatus);
		if (status == null) {
			warnings.add(where + ": nieznany status '" + rawStatus + "' — traktuje jak 'Członek'");
			status = AllianceStatus.MEMBER;
		}

		return new AllianceEntry(nick, allay, status, kingdom);
	}

	/**
	 * Usuwa kody formatowania Minecrafta (§ + znak) i znaki sterujace, scala
	 * spacje i przycina zbyt dlugie nazwy — inaczej nazwa z bota moglaby
	 * zmienic kolor/styl nametagu albo rozciagnac liste TAB.
	 */
	static String cleanKingdom(String raw) {
		if (raw == null) return null;
		StringBuilder sb = new StringBuilder(raw.length());
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (c == '\u00A7') {
				i++; // pomin tez znak kodu
				continue;
			}
			sb.append(Character.isISOControl(c) || Character.isWhitespace(c) ? ' ' : c);
		}
		String cleaned = sb.toString().trim().replaceAll(" {2,}", " ");
		if (cleaned.codePointCount(0, cleaned.length()) > MAX_KINGDOM_LENGTH) {
			int end = cleaned.offsetByCodePoints(0, MAX_KINGDOM_LENGTH - 1);
			cleaned = cleaned.substring(0, end).trim() + "…";
		}
		return cleaned;
	}

	private static String stringField(JsonObject obj, String name) {
		JsonElement el = obj.get(name);
		if (el instanceof JsonPrimitive p && p.isString()) return p.getAsString();
		return null;
	}
}
