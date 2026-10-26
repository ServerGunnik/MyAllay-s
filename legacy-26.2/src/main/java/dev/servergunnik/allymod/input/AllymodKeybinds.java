package dev.servergunnik.allymod.input;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import dev.servergunnik.allymod.Allymod;
import dev.servergunnik.allymod.gui.ManageScreen;

public final class AllymodKeybinds {
	private static KeyMapping openGui;

	private AllymodKeybinds() {}

	public static void register() {
		openGui = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"allymod.key.gui",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_F6,
				KeyMapping.Category.MISC
		));
		Allymod.LOGGER.info("Keybind zarejestrowany: '{}' -> {} (Options -> Controls -> Miscellaneous)",
				openGui.getName(), openGui.saveString());

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openGui.consumeClick()) {
				if (client.gui.screen() == null) {
					client.setScreenAndShow(new ManageScreen());
				}
			}
		});
	}
}
