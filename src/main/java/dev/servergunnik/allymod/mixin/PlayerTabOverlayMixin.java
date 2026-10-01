package dev.servergunnik.allymod.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.servergunnik.allymod.data.AllianceEntry;
import dev.servergunnik.allymod.render.AlliancePrefix;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {

	// require = 0: lista TAB to dodatek — jesli Mojang zmieni nazwe metody, nametagi dalej dzialaja.
	@Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true, require = 0)
	private void allymod$alliancePrefix(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
		AllianceEntry member = AlliancePrefix.lookup(info.getProfile().name());
		if (member == null) return;
		cir.setReturnValue(AlliancePrefix.prepend(member, cir.getReturnValue()));
	}
}
