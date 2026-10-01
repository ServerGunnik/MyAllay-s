package dev.servergunnik.allymod.mixin;

import java.util.UUID;

import net.minecraft.client.render.entity.state.EntityRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import dev.servergunnik.allymod.render.AllymodRenderState;

@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateMixin implements AllymodRenderState {
	@Unique
	private UUID allymod$uuid;

	@Override
	public UUID allymod$uuid() {
		return allymod$uuid;
	}

	@Override
	public void allymod$setUuid(UUID uuid) {
		this.allymod$uuid = uuid;
	}
}
