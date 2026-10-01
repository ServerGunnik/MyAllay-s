package dev.servergunnik.allymod.render;

import java.util.Locale;

import dev.servergunnik.allymod.Allymod;

/**
 * Pomiar czasu wykonania naszych render mixinow — do weryfikacji ze nie
 * regresujemy FPS. Wolane wylacznie z render threada, wiec zwykle long-i
 * (bez AtomicLong-ow) sa bezpieczne.
 */
public final class RenderProfiler {
	private static final int SAMPLES_PER_LOG = 600;

	public static volatile boolean enabled = false;

	private static long wireframeSumNs = 0;
	private static long wireframeCount = 0;
	private static long nametagSumNs = 0;
	private static long nametagCount = 0;

	private RenderProfiler() {}

	public static void setEnabled(boolean value) {
		enabled = value;
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void recordWireframe(long ns) {
		wireframeSumNs += ns;
		if (++wireframeCount >= SAMPLES_PER_LOG) {
			emit("wireframe", wireframeSumNs, wireframeCount);
			wireframeSumNs = 0;
			wireframeCount = 0;
		}
	}

	public static void recordNametag(long ns) {
		nametagSumNs += ns;
		if (++nametagCount >= SAMPLES_PER_LOG) {
			emit("nametag  ", nametagSumNs, nametagCount);
			nametagSumNs = 0;
			nametagCount = 0;
		}
	}

	private static void emit(String label, long sumNs, long count) {
		double avgUs = sumNs / 1000.0 / count;
		Allymod.LOGGER.info(String.format(Locale.ROOT,
				"fps-debug: %s avg %.2f us (samples: %d)", label, avgUs, count));
	}
}
