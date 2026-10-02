package dev.servergunnik.allymod.data;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

/**
 * Configi sojuszu pobierane z linku (np. stały link wystawiony przez bota).
 * Lista linkow jest zapisana w osobnym pliku; kazdy link ma swoj plik
 * w folderze configow, aktualizowany przy kazdym pobraniu. Gdy pobranie sie
 * nie uda, zostaje ostatnia pobrana wersja.
 */
public final class AllianceLinks {
	public static final int MAX_BYTES = 2 * 1024 * 1024;
	private static final int CONNECT_TIMEOUT_MS = 10_000;
	private static final int READ_TIMEOUT_MS = 20_000;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public record Link(String url, String file) {}

	/** Wynik ostatniej proby pobrania (tylko w pamieci). error == null = OK. */
	public record LinkStatus(long checkedAtMillis, String error) {}

	public enum FetchOutcome { UPDATED, UNCHANGED }

	private final Path linksFile;
	private final AllianceStore store;
	private final List<Link> links = new ArrayList<>();
	private final Map<String, LinkStatus> statusByFile = new ConcurrentHashMap<>();

	public AllianceLinks(Path linksFile, AllianceStore store) {
		this.linksFile = linksFile;
		this.store = store;
	}

	public Path file() {
		return linksFile;
	}

	public synchronized void load() throws IOException {
		links.clear();
		if (!Files.exists(linksFile)) return;
		try (Reader reader = Files.newBufferedReader(linksFile, StandardCharsets.UTF_8)) {
			List<Link> loaded = GSON.fromJson(reader, new TypeToken<List<Link>>() {}.getType());
			if (loaded == null) return;
			for (Link link : loaded) {
				if (link == null || link.url() == null || link.file() == null) continue;
				if (!isSafeFileName(link.file())) continue;
				links.add(link);
			}
		} catch (JsonParseException e) {
			Path backup = linksFile.resolveSibling(linksFile.getFileName() + ".broken");
			Files.copy(linksFile, backup, StandardCopyOption.REPLACE_EXISTING);
			throw new IOException(linksFile.getFileName() + " jest uszkodzony (kopia: " + backup.getFileName() + "): "
					+ e.getMessage(), e);
		}
	}

	private void save() throws IOException {
		Path parent = linksFile.getParent();
		if (parent != null) Files.createDirectories(parent);
		Path tmp = linksFile.resolveSibling(linksFile.getFileName() + ".tmp");
		try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
			GSON.toJson(links, writer);
		}
		Files.move(tmp, linksFile, StandardCopyOption.REPLACE_EXISTING);
	}

	public synchronized List<Link> all() {
		return List.copyOf(links);
	}

	public synchronized Link find(String fileName) {
		for (Link link : links) {
			if (link.file().equalsIgnoreCase(fileName)) return link;
		}
		return null;
	}

	public LinkStatus status(String fileName) {
		return statusByFile.get(fileName.toLowerCase(Locale.ROOT));
	}

	/**
	 * Sprawdza link, pobiera config i dopiero gdy jest poprawny zapisuje link
	 * na liscie. Blokuje (siec) — wolac poza render threadem. Po sukcesie
	 * trzeba przeladowac {@link AllianceStore}.
	 */
	public Link addAndFetch(String rawUrl) throws IOException {
		URI uri = parseUrl(rawUrl);
		String url = uri.toString();
		Link link;
		synchronized (this) {
			for (Link existing : links) {
				if (existing.url().equals(url)) {
					fetch(existing);
					return existing;
				}
			}
			link = new Link(url, uniqueFileName(suggestFileName(uri)));
		}
		fetch(link);
		synchronized (this) {
			links.add(link);
			save();
		}
		return link;
	}

	/** Usuwa link (plik configu usuwa {@link AllianceStore#removeConfig}). */
	public synchronized boolean remove(String fileName) throws IOException {
		boolean removed = links.removeIf(l -> l.file().equalsIgnoreCase(fileName));
		if (removed) {
			statusByFile.remove(fileName.toLowerCase(Locale.ROOT));
			save();
		}
		return removed;
	}

	/**
	 * Pobiera config z linku i podmienia plik, jesli sie zmienil. Niepoprawny
	 * config (uszkodzony JSON itp.) jest odrzucany, a stary plik zostaje.
	 */
	public FetchOutcome fetch(Link link) throws IOException {
		String key = link.file().toLowerCase(Locale.ROOT);
		try {
			FetchOutcome outcome = download(link);
			statusByFile.put(key, new LinkStatus(System.currentTimeMillis(), null));
			return outcome;
		} catch (IOException e) {
			statusByFile.put(key, new LinkStatus(System.currentTimeMillis(), e.getMessage()));
			throw e;
		}
	}

	private FetchOutcome download(Link link) throws IOException {
		byte[] body = httpGet(parseUrl(link.url()));
		Path dir = store.dir();
		Files.createDirectories(dir);
		Path target = dir.resolve(link.file());
		// nazwa bez koncowki .json — AllianceStore nie wczyta niedokonczonego pliku
		Path tmp = dir.resolve("." + link.file() + ".download");
		try {
			Files.write(tmp, body);
			AllianceFileParser.parse(tmp);
			if (Files.exists(target) && Arrays.equals(Files.readAllBytes(target), body)) {
				return FetchOutcome.UNCHANGED;
			}
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
			return FetchOutcome.UPDATED;
		} finally {
			Files.deleteIfExists(tmp);
		}
	}

	private static byte[] httpGet(URI uri) throws IOException {
		URLConnection raw = uri.toURL().openConnection();
		if (!(raw instanceof HttpURLConnection conn)) throw new IOException("To nie jest link http(s)");
		conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
		conn.setReadTimeout(READ_TIMEOUT_MS);
		conn.setInstanceFollowRedirects(true);
		conn.setRequestProperty("User-Agent", "MyAllay's (Minecraft mod)");
		conn.setRequestProperty("Accept", "application/json, text/plain, */*");
		try {
			int code = conn.getResponseCode();
			if (code != HttpURLConnection.HTTP_OK) {
				throw new IOException("serwer odpowiedzial HTTP " + code
						+ (code == 403 || code == 404 ? " (link wygasl albo jest bledny?)" : ""));
			}
			if (conn.getContentLengthLong() > MAX_BYTES) throw new IOException("plik jest za duzy (max 2 MB)");
			try (InputStream in = conn.getInputStream()) {
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				byte[] buf = new byte[8192];
				int n;
				while ((n = in.read(buf)) != -1) {
					out.write(buf, 0, n);
					if (out.size() > MAX_BYTES) throw new IOException("plik jest za duzy (max 2 MB)");
				}
				return out.toByteArray();
			}
		} catch (java.net.SocketTimeoutException e) {
			throw new IOException("serwer nie odpowiada (timeout)", e);
		} catch (java.net.UnknownHostException e) {
			throw new IOException("nieznany adres " + e.getMessage(), e);
		} finally {
			conn.disconnect();
		}
	}

	public static URI parseUrl(String raw) throws IOException {
		if (raw == null || raw.isBlank()) throw new IOException("pusty link");
		URI uri;
		try {
			uri = new URI(raw.trim());
		} catch (URISyntaxException e) {
			throw new IOException("niepoprawny link: " + e.getMessage(), e);
		}
		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		if (!scheme.equals("https") && !scheme.equals("http")) {
			throw new IOException("link musi zaczynac sie od https://");
		}
		if (uri.getHost() == null || uri.getHost().isBlank()) throw new IOException("w linku brakuje adresu serwera");
		return uri;
	}

	/** Linki do zalacznikow Discorda wygasaja po ~24 h — do auto-aktualizacji potrzebny jest staly link. */
	public static boolean isExpiringDiscordLink(String url) {
		try {
			String host = new URI(url).getHost();
			if (host == null) return false;
			host = host.toLowerCase(Locale.ROOT);
			return host.equals("cdn.discordapp.com") || host.equals("media.discordapp.net");
		} catch (URISyntaxException e) {
			return false;
		}
	}

	static String suggestFileName(URI uri) {
		String path = uri.getRawPath() == null ? "" : uri.getRawPath();
		String last = path.substring(path.lastIndexOf('/') + 1);
		last = URLDecoder.decode(last, StandardCharsets.UTF_8);
		String base = last.toLowerCase(Locale.ROOT).endsWith(".json") ? last.substring(0, last.length() - 5) : last;
		base = base.replaceAll("[^A-Za-z0-9._-]", "_").replaceAll("^[._]+", "");
		if (base.length() > 40) base = base.substring(0, 40);
		if (base.isEmpty()) base = uri.getHost().replaceAll("[^A-Za-z0-9.-]", "_");
		return "link-" + base + ".json";
	}

	private String uniqueFileName(String suggested) {
		String base = suggested.substring(0, suggested.length() - 5);
		String name = suggested;
		for (int i = 2; isTaken(name); i++) name = base + "-" + i + ".json";
		return name;
	}

	private boolean isTaken(String name) {
		if (find(name) != null) return true;
		return Files.exists(store.dir().resolve(name));
	}

	private static boolean isSafeFileName(String name) {
		return name.matches("[A-Za-z0-9._-]+\\.json") && !name.startsWith(".");
	}
}
