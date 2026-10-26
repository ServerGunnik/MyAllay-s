package dev.servergunnik.allymod.data;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Configi sojuszu — dowolna liczba plikow *.json w jednym folderze
 * (config/snz-sojusz/). Pliki dodaje sie z GUI moda; wszystkie sa scalane
 * w jedna mape nick -> wpis. Gdy gracz jest w kilku configach, wygrywa
 * config pierwszy alfabetycznie.
 */
public final class AllianceStore {

	/** Stan jednego configu do pokazania w GUI. error != null = ostatni odczyt sie nie udal. */
	public record ConfigInfo(String fileName, int players, List<String> warnings, String error) {
		public boolean ok() {
			return error == null;
		}
	}

	public record ReloadResult(int players, int configs, List<ConfigInfo> failed, int warnings) {}

	private final Path dir;
	// Podmieniane w calosci po przeladowaniu — render thread zawsze widzi spojna mape.
	private volatile Map<String, AllianceEntry> byNick = Map.of();
	private volatile List<ConfigInfo> configs = List.of();
	// Ostatnia udana wersja kazdego pliku — uszkodzony plik nie kasuje graczy z poprzedniego odczytu.
	private final Map<String, AllianceFileParser.Parsed> lastGood = new HashMap<>();

	public AllianceStore(Path dir) {
		this.dir = dir;
	}

	public Path dir() {
		return dir;
	}

	/**
	 * Wczytuje ponownie wszystkie configi z folderu (tworzy go, jesli go nie ma).
	 * Rzuca IOException tylko, gdy nie da sie odczytac samego folderu — wtedy
	 * zostaje poprzedni stan.
	 */
	public synchronized ReloadResult reload() throws IOException {
		Files.createDirectories(dir);
		List<Path> files = listConfigFiles();

		Map<String, AllianceEntry> merged = new HashMap<>();
		Map<String, String> ownerByNick = new HashMap<>();
		List<ConfigInfo> infos = new ArrayList<>();
		List<ConfigInfo> failed = new ArrayList<>();
		Map<String, AllianceFileParser.Parsed> stillPresent = new HashMap<>();
		int warningCount = 0;

		for (Path file : files) {
			String name = file.getFileName().toString();
			AllianceFileParser.Parsed parsed;
			String error = null;
			try {
				parsed = AllianceFileParser.parse(file);
			} catch (IOException e) {
				error = e.getMessage();
				parsed = lastGood.get(name);
			}
			if (parsed != null) stillPresent.put(name, parsed);

			List<String> warnings = new ArrayList<>();
			int players = 0;
			if (parsed != null) {
				warnings.addAll(parsed.warnings());
				for (AllianceEntry entry : parsed.entries()) {
					String key = entry.nick().toLowerCase(Locale.ROOT);
					String owner = ownerByNick.putIfAbsent(key, name);
					if (owner != null) {
						warnings.add("gracz '" + entry.nick() + "' jest tez w " + owner + " — uzywam wpisu z " + owner);
						continue;
					}
					merged.put(key, entry);
					players++;
				}
			}
			ConfigInfo info = new ConfigInfo(name, players, List.copyOf(warnings), error);
			infos.add(info);
			if (!info.ok()) failed.add(info);
			warningCount += warnings.size();
		}

		lastGood.clear();
		lastGood.putAll(stillPresent);
		byNick = Map.copyOf(merged);
		configs = List.copyOf(infos);
		return new ReloadResult(merged.size(), infos.size(), List.copyOf(failed), warningCount);
	}

	private List<Path> listConfigFiles() throws IOException {
		List<Path> files = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
			for (Path p : stream) {
				if (Files.isRegularFile(p) && isJsonName(p.getFileName().toString())) files.add(p);
			}
		}
		files.sort(Comparator.comparing(p -> p.getFileName().toString(), String.CASE_INSENSITIVE_ORDER));
		return files;
	}

	private static boolean isJsonName(String name) {
		return name.toLowerCase(Locale.ROOT).endsWith(".json");
	}

	/**
	 * Kopiuje plik do folderu configow. Plik o tej samej nazwie jest podmieniany
	 * (tak sie aktualizuje config z bota). Uszkodzony plik jest odrzucany
	 * (IOException) i nic nie jest kopiowane. Po dodaniu trzeba wywolac {@link #reload()}.
	 *
	 * @return nazwa pliku w folderze configow
	 */
	public synchronized String addConfig(Path source) throws IOException {
		if (!Files.isRegularFile(source)) {
			throw new IOException(source.getFileName() + " nie jest plikiem");
		}
		AllianceFileParser.parse(source);

		String name = source.getFileName().toString();
		if (!isJsonName(name)) name += ".json";
		Files.createDirectories(dir);
		Path target = dir.resolve(name);
		if (!(Files.exists(target) && Files.isSameFile(source, target))) {
			Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
		return name;
	}

	/** Usuwa config z folderu. Po usunieciu trzeba wywolac {@link #reload()}. */
	public synchronized boolean removeConfig(String fileName) throws IOException {
		Path target = dir.resolve(fileName).normalize();
		if (!dir.normalize().equals(target.getParent()) || !isJsonName(fileName)) {
			throw new IOException("Niepoprawna nazwa configu: " + fileName);
		}
		return Files.deleteIfExists(target);
	}

	// Hot path — wolane co klatke dla kazdego widocznego gracza. Null = gracz spoza configow.
	public AllianceEntry get(String nick) {
		Map<String, AllianceEntry> map = byNick;
		if (map.isEmpty() || nick == null) return null;
		return map.get(nick.toLowerCase(Locale.ROOT));
	}

	public List<ConfigInfo> configs() {
		return configs;
	}

	public int size() {
		return byNick.size();
	}
}
