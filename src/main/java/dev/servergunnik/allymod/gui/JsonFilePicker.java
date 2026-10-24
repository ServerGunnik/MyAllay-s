package dev.servergunnik.allymod.gui;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.lwjgl.PointerBuffer;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryUtil;

import net.minecraft.client.Minecraft;

/**
 * Systemowe okno "Otworz plik" przez SDL3 (od 26.3 Minecraft uzywa SDL zamiast GLFW/tinyfd).
 * Okno jest asynchroniczne: wolac z render threadu, wynik przychodzi przez callback
 * (SDL moze go wywolac z innego watku — wolajacy przerzuca go na watek gry).
 */
final class JsonFilePicker {
	private JsonFilePicker() {}

	/** onResult: null = blad okna, pusta lista = gracz anulowal. */
	static void pickJsonFiles(String filterDescription, Consumer<List<Path>> onResult) {
		// Filtry i callback musza zyc do wywolania callbacku, wiec alokujemy je recznie.
		ByteBuffer name = MemoryUtil.memUTF8(filterDescription);
		ByteBuffer pattern = MemoryUtil.memUTF8("json");
		SDL_DialogFileFilter.Buffer filters = SDL_DialogFileFilter.calloc(1);
		filters.get(0).name(name).pattern(pattern);

		SDL_DialogFileCallback[] holder = new SDL_DialogFileCallback[1];
		holder[0] = SDL_DialogFileCallback.create((userdata, filelist, filter) -> {
			List<Path> paths = null;
			try {
				if (filelist != MemoryUtil.NULL) {
					paths = new ArrayList<>();
					// filelist to tablica char* zakonczona NULL-em
					for (int i = 0; ; i++) {
						long entry = MemoryUtil.memGetAddress(filelist + (long) i * PointerBuffer.POINTER_SIZE);
						if (entry == MemoryUtil.NULL) break;
						paths.add(Path.of(MemoryUtil.memUTF8(entry)));
					}
				}
			} finally {
				filters.free();
				MemoryUtil.memFree(name);
				MemoryUtil.memFree(pattern);
				holder[0].free();
			}
			onResult.accept(paths);
		});

		long window = Minecraft.getInstance().getWindow().handle();
		SDLDialog.SDL_ShowOpenFileDialog(holder[0], MemoryUtil.NULL, window, filters, defaultDirectory(), true);
	}

	private static String defaultDirectory() {
		Path home = Path.of(System.getProperty("user.home"));
		Path downloads = home.resolve("Downloads");
		Path dir = Files.isDirectory(downloads) ? downloads : home;
		return dir.toAbsolutePath() + java.io.File.separator;
	}
}
