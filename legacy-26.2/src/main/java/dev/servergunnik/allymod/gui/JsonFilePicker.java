package dev.servergunnik.allymod.gui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/**
 * Systemowe okno "Otworz plik" (LWJGL tinyfd — jest w bibliotekach Minecrafta).
 * Blokuje watek az gracz zamknie okno, wiec wolac poza render threadem.
 */
final class JsonFilePicker {
	private JsonFilePicker() {}

	/** Pusta lista = gracz anulowal. */
	static List<Path> pickJsonFiles(String title, String filterDescription) {
		String result;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			PointerBuffer filters = stack.mallocPointer(1);
			filters.put(stack.UTF8("*.json"));
			filters.flip();
			result = TinyFileDialogs.tinyfd_openFileDialog(title, defaultDirectory(), filters, filterDescription, true);
		}
		List<Path> paths = new ArrayList<>();
		if (result == null || result.isBlank()) return paths;
		// przy wielokrotnym wyborze tinyfd rozdziela sciezki znakiem '|'
		for (String part : result.split("\\|")) {
			if (!part.isBlank()) paths.add(Path.of(part));
		}
		return paths;
	}

	private static String defaultDirectory() {
		Path home = Path.of(System.getProperty("user.home"));
		Path downloads = home.resolve("Downloads");
		Path dir = Files.isDirectory(downloads) ? downloads : home;
		return dir.toAbsolutePath() + java.io.File.separator;
	}
}
