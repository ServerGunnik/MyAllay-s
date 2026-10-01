package dev.servergunnik.allymod.data;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * Lista graczy sojuszu z config/snz-sojusz.json. Plik generuje bot Discord —
 * mod go tylko czyta (jedyny zapis to pusty "[]", gdy pliku nie ma).
 */
public final class AllianceStore {
	public static final Pattern NICK_PATTERN = Pattern.compile("[A-Za-z0-9_]{3,16}");

	public record LoadResult(int loaded, List<String> warnings) {}

	private final Path file;
	// Podmieniana w calosci po udanym wczytaniu — render thread zawsze widzi spojna mape.
	private volatile Map<String, AllianceEntry> byNick = Map.of();

	public AllianceStore(Path file) {
		this.file = file;
	}

	public Path file() {
		return file;
	}

	/**
	 * Wczytuje plik. Przy uszkodzonym pliku rzuca IOException i zostawia
	 * poprzednio wczytana liste. Bledne pojedyncze wpisy sa pomijane
	 * i opisane w {@link LoadResult#warnings()}.
	 */
	public synchronized LoadResult load() throws IOException {
		if (!Files.exists(file)) {
			Path parent = file.getParent();
			if (parent != null) Files.createDirectories(parent);
			Files.writeString(file, "[]\n", StandardCharsets.UTF_8);
			byNick = Map.of();
			return new LoadResult(0, List.of());
		}

		String content;
		try {
			content = Files.readString(file, StandardCharsets.UTF_8);
		} catch (CharacterCodingException e) {
			throw new IOException(file.getFileName() + " nie jest zapisany w UTF-8", e);
		}
		if (!content.isEmpty() && content.charAt(0) == '﻿') content = content.substring(1);

		JsonElement root;
		try {
			root = JsonParser.parseString(content);
		} catch (JsonParseException e) {
			throw new IOException(file.getFileName() + " jest uszkodzony: " + e.getMessage(), e);
		}
		if (!root.isJsonArray()) {
			throw new IOException(file.getFileName() + " jest uszkodzony: oczekiwano tablicy JSON [ ... ]");
		}

		List<String> warnings = new ArrayList<>();
		Map<String, AllianceEntry> parsed = new HashMap<>();
		JsonArray array = root.getAsJsonArray();
		for (int i = 0; i < array.size(); i++) {
			AllianceEntry entry = parseEntry(i, array.get(i), warnings);
			if (entry == null) continue;
			String key = entry.nick().toLowerCase(Locale.ROOT);
			if (parsed.containsKey(key)) {
				warnings.add("wpis #" + i + ": gracz '" + entry.nick() + "' wystepuje drugi raz — pomijam duplikat");
				continue;
			}
			parsed.put(key, entry);
		}

		byNick = Map.copyOf(parsed);
		return new LoadResult(parsed.size(), List.copyOf(warnings));
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

		String kingdom = stringField(obj, "kingdom");
		if (kingdom == null || kingdom.isBlank()) {
			warnings.add(where + ": brak pola 'kingdom' (albo jest puste) — pomijam");
			return null;
		}

		String rawStatus = stringField(obj, "status");
		AllianceStatus status = AllianceStatus.fromDisplayName(rawStatus);
		if (status == null) {
			warnings.add(where + ": nieznany status '" + rawStatus + "' — traktuje jak 'Członek'");
			status = AllianceStatus.MEMBER;
		}

		return new AllianceEntry(nick, allay, status, kingdom.trim());
	}

	private static String stringField(JsonObject obj, String name) {
		JsonElement el = obj.get(name);
		if (el instanceof JsonPrimitive p && p.isString()) return p.getAsString();
		return null;
	}

	// Hot path — wolane co klatke dla kazdego widocznego gracza. Null = gracz spoza pliku.
	public AllianceEntry get(String nick) {
		Map<String, AllianceEntry> map = byNick;
		if (map.isEmpty() || nick == null) return null;
		return map.get(nick.toLowerCase(Locale.ROOT));
	}

	public List<AllianceEntry> all() {
		List<AllianceEntry> list = new ArrayList<>(byNick.values());
		list.sort(Comparator.comparing(AllianceEntry::nick, String.CASE_INSENSITIVE_ORDER));
		return list;
	}

	public int size() {
		return byNick.size();
	}
}
