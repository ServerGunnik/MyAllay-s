package dev.servergunnik.allymod.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AllianceLinksTest {
	private static final String SAMPLE = """
			[
			  { "nick": "KrolPolski", "allay": true,  "status": "Król",    "kingdom": "Polska" },
			  { "nick": "KrolNiemiec","allay": false, "status": "Król",    "kingdom": "Niemcy" }
			]
			""";

	/** Mini serwer HTTP: sciezka -> tresc; brak sciezki = 404. */
	private static final class FakeBot implements AutoCloseable {
		final Map<String, String> files = new ConcurrentHashMap<>();
		final HttpServer server;

		FakeBot() throws IOException {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/", exchange -> {
				String body = files.get(exchange.getRequestURI().getPath());
				byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
				exchange.sendResponseHeaders(body == null ? 404 : 200, bytes.length == 0 ? -1 : bytes.length);
				try (OutputStream out = exchange.getResponseBody()) {
					out.write(bytes);
				}
			});
			server.start();
		}

		String url(String path) {
			return "http://127.0.0.1:" + server.getAddress().getPort() + path;
		}

		@Override
		public void close() {
			server.stop(0);
		}
	}

	@Test
	void addAndFetchDownloadsConfigAndSavesLink(@TempDir Path tmp) throws IOException {
		try (FakeBot bot = new FakeBot()) {
			bot.files.put("/sojusz/snz-sojusz.json", SAMPLE);
			AllianceStore store = new AllianceStore(tmp.resolve("snz-sojusz"));
			AllianceLinks links = new AllianceLinks(tmp.resolve("allymod/sojusz-linki.json"), store);

			AllianceLinks.Link link = links.addAndFetch(bot.url("/sojusz/snz-sojusz.json"));
			store.reload();

			assertEquals("link-snz-sojusz.json", link.file());
			assertEquals(2, store.size());
			assertEquals("Polska", store.get("krolpolski").kingdom());
			assertNull(links.status(link.file()).error());

			AllianceLinks reloaded = new AllianceLinks(tmp.resolve("allymod/sojusz-linki.json"), store);
			reloaded.load();
			assertEquals(1, reloaded.all().size());
			assertEquals(link.url(), reloaded.all().get(0).url());
		}
	}

	@Test
	void fetchUpdatesOnlyWhenContentChanged(@TempDir Path tmp) throws IOException {
		try (FakeBot bot = new FakeBot()) {
			bot.files.put("/a.json", SAMPLE);
			AllianceStore store = new AllianceStore(tmp.resolve("snz-sojusz"));
			AllianceLinks links = new AllianceLinks(tmp.resolve("links.json"), store);
			AllianceLinks.Link link = links.addAndFetch(bot.url("/a.json"));

			assertEquals(AllianceLinks.FetchOutcome.UNCHANGED, links.fetch(link));

			bot.files.put("/a.json", "[ { \"nick\": \"Wiking_7\", \"allay\": true, \"status\": \"Król\", \"kingdom\": \"Norwegia\" } ]");
			assertEquals(AllianceLinks.FetchOutcome.UPDATED, links.fetch(link));
			store.reload();
			assertEquals(1, store.size());
			assertEquals("Norwegia", store.get("wiking_7").kingdom());
		}
	}

	@Test
	void brokenOrMissingRemoteKeepsLastGoodFile(@TempDir Path tmp) throws IOException {
		try (FakeBot bot = new FakeBot()) {
			bot.files.put("/a.json", SAMPLE);
			AllianceStore store = new AllianceStore(tmp.resolve("snz-sojusz"));
			AllianceLinks links = new AllianceLinks(tmp.resolve("links.json"), store);
			AllianceLinks.Link link = links.addAndFetch(bot.url("/a.json"));

			bot.files.put("/a.json", "[ { zepsuty");
			assertThrows(IOException.class, () -> links.fetch(link));
			bot.files.remove("/a.json");
			IOException e = assertThrows(IOException.class, () -> links.fetch(link));
			assertTrue(e.getMessage().contains("404"));
			assertNotNull(links.status(link.file()).error());

			store.reload();
			assertEquals(2, store.size());
			assertEquals(1, store.configs().size());
		}
	}

	@Test
	void badLinkIsNotSaved(@TempDir Path tmp) throws IOException {
		try (FakeBot bot = new FakeBot()) {
			AllianceStore store = new AllianceStore(tmp.resolve("snz-sojusz"));
			AllianceLinks links = new AllianceLinks(tmp.resolve("links.json"), store);

			assertThrows(IOException.class, () -> links.addAndFetch(bot.url("/nie-ma.json")));
			assertThrows(IOException.class, () -> links.addAndFetch("ftp://example.com/a.json"));
			assertThrows(IOException.class, () -> links.addAndFetch("to nie link"));
			assertTrue(links.all().isEmpty());
			assertFalse(Files.exists(tmp.resolve("links.json")));
		}
	}

	@Test
	void sameFileNameFromTwoLinksGetsUniqueNames(@TempDir Path tmp) throws IOException {
		try (FakeBot bot = new FakeBot()) {
			bot.files.put("/polska/snz-sojusz.json", SAMPLE);
			bot.files.put("/norwegia/snz-sojusz.json",
					"[ { \"nick\": \"Wiking_7\", \"allay\": true, \"status\": \"Król\", \"kingdom\": \"Norwegia\" } ]");
			AllianceStore store = new AllianceStore(tmp.resolve("snz-sojusz"));
			AllianceLinks links = new AllianceLinks(tmp.resolve("links.json"), store);

			AllianceLinks.Link a = links.addAndFetch(bot.url("/polska/snz-sojusz.json"));
			AllianceLinks.Link b = links.addAndFetch(bot.url("/norwegia/snz-sojusz.json"));
			store.reload();

			assertEquals("link-snz-sojusz.json", a.file());
			assertEquals("link-snz-sojusz-2.json", b.file());
			assertEquals(3, store.size());

			assertTrue(links.remove(b.file()));
			assertEquals(1, links.all().size());
		}
	}

	@Test
	void fileNameAndDiscordDetection() throws IOException {
		assertEquals("link-snz-sojusz.json", AllianceLinks.suggestFileName(
				URI.create("https://cdn.discordapp.com/attachments/1/2/snz-sojusz.json?ex=abc&hm=def")));
		assertEquals("link-example.com.json", AllianceLinks.suggestFileName(URI.create("https://example.com/")));
		assertEquals("link-Kr_l.json", AllianceLinks.suggestFileName(URI.create("https://example.com/Kr%C3%B3l")));
		assertTrue(AllianceLinks.isExpiringDiscordLink("https://cdn.discordapp.com/attachments/1/2/a.json"));
		assertFalse(AllianceLinks.isExpiringDiscordLink("https://raw.githubusercontent.com/x/y/main/a.json"));
	}
}
