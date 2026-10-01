package dev.servergunnik.allymod.input;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

import dev.servergunnik.allymod.Allymod;
import dev.servergunnik.allymod.gui.ManageScreen;

public final class AllymodKeybinds {
	private static KeyBinding openGui;

	private AllymodKeybinds() {}

	public static void register() {
		openGui = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"allymod.key.gui",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_F6,
				KeyBinding.Category.MISC
		));
		Allymod.LOGGER.info("Keybind zarejestrowany (Options -> Controls -> Miscellaneous)");

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openGui.wasPressed()) {
				if (client.currentScreen == null) {
					client.setScreen(new ManageScreen());
				}
			}
		});
	}
}
