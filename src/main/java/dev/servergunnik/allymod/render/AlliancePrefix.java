package dev.servergunnik.allymod.render;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import dev.servergunnik.allymod.Allymod;
import dev.servergunnik.allymod.data.AllianceEntry;
import dev.servergunnik.allymod.data.AllianceStore;

/**
 * Prefiks "[Panstwo • Status] " z snz-sojusz.json — zielony dla allay=true,
 * czerwony dla allay=false. Wspolny dla nametagu nad glowa i listy TAB.
 */
public final class AlliancePrefix {
	private AlliancePrefix() {}

	public static AllianceEntry lookup(String nick) {
		AllianceStore alliance = Allymod.alliance();
		return alliance == null ? null : alliance.get(nick);
	}

	public static Component prepend(AllianceEntry entry, Component name) {
		ChatFormatting color = entry.allay() ? ChatFormatting.GREEN : ChatFormatting.RED;
		// Pusty rodzic, zeby nick nie dziedziczyl koloru prefiksu.
		return Component.empty()
				.append(Component.literal(entry.label()).withStyle(color))
				.append(name);
	}
}
