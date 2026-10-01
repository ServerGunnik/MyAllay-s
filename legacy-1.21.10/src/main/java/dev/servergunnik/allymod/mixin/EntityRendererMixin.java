package dev.servergunnik.allymod.mixin;

import java.util.UUID;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.servergunnik.allymod.Allymod;
import dev.servergunnik.allymod.cache.RelationCache;
import dev.servergunnik.allymod.data.RelationKind;
import dev.servergunnik.allymod.render.AllymodRenderState;
import dev.servergunnik.allymod.render.RenderProfiler;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

	@Inject(method = "updateRenderState", at = @At("TAIL"))
	private void allymod$storeUuidAndColorName(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
		if (!(entity instanceof PlayerEntity)) return;
		((AllymodRenderState) state).allymod$setUuid(entity.getUuid());

		RelationCache cache = Allymod.cache();
		if (cache == null) return;
		RelationCache.RenderStatus status = cache.statusOf(entity.getUuid());
		if (status == null) return;
		if (state.displayName == null) return;

		long start = RenderProfiler.enabled ? System.nanoTime() : 0L;
		TextColor color = TextColor.fromRgb(status.argb() & 0x00FFFFFF);
		String tag = status.kind() == RelationKind.ALLY ? "[A] " : "[E] ";
		MutableText labeled = Text.literal(tag).setStyle(state.displayName.getStyle().withColor(color))
				.append(state.displayName.copy().setStyle(state.displayName.getStyle().withColor(color)));
		state.displayName = labeled;
		if (RenderProfiler.enabled) RenderProfiler.recordNametag(System.nanoTime() - start);
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void allymod$drawWireframe(EntityRenderState state, MatrixStack matrices,
	                                    OrderedRenderCommandQueue queue,
	                                    CameraRenderState camera, CallbackInfo ci) {
		UUID uuid = ((AllymodRenderState) state).allymod$uuid();
		if (uuid == null) return;

		RelationCache cache = Allymod.cache();
		if (cache == null) return;
		RelationCache.RenderStatus status = cache.statusOf(uuid);
		if (status == null) return;

		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc != null && mc.player != null && uuid.equals(mc.player.getUuid())) return;

		float halfW = state.width / 2f;
		float h = state.height;
		int argb = status.argb();

		long start = RenderProfiler.enabled ? System.nanoTime() : 0L;
		queue.submitCustom(matrices, RenderLayer.LINES, (pose, buf) ->
				drawBoxLines(pose, buf, -halfW, 0f, -halfW, halfW, h, halfW, argb));
		if (RenderProfiler.enabled) RenderProfiler.recordWireframe(System.nanoTime() - start);
	}

	private static final float LINE_WIDTH = 2f;

	private static void drawBoxLines(MatrixStack.Entry pose, VertexConsumer buf,
	                                  float x1, float y1, float z1,
	                                  float x2, float y2, float z2,
	                                  int argb) {
		line(pose, buf, x1, y1, z1, x2, y1, z1, 1f, 0f, 0f, argb);
		line(pose, buf, x2, y1, z1, x2, y1, z2, 0f, 0f, 1f, argb);
		line(pose, buf, x2, y1, z2, x1, y1, z2, 1f, 0f, 0f, argb);
		line(pose, buf, x1, y1, z2, x1, y1, z1, 0f, 0f, 1f, argb);
		line(pose, buf, x1, y2, z1, x2, y2, z1, 1f, 0f, 0f, argb);
		line(pose, buf, x2, y2, z1, x2, y2, z2, 0f, 0f, 1f, argb);
		line(pose, buf, x2, y2, z2, x1, y2, z2, 1f, 0f, 0f, argb);
		line(pose, buf, x1, y2, z2, x1, y2, z1, 0f, 0f, 1f, argb);
		line(pose, buf, x1, y1, z1, x1, y2, z1, 0f, 1f, 0f, argb);
		line(pose, buf, x2, y1, z1, x2, y2, z1, 0f, 1f, 0f, argb);
		line(pose, buf, x2, y1, z2, x2, y2, z2, 0f, 1f, 0f, argb);
		line(pose, buf, x1, y1, z2, x1, y2, z2, 0f, 1f, 0f, argb);
	}

	private static void line(MatrixStack.Entry pose, VertexConsumer buf,
	                          float x1, float y1, float z1,
	                          float x2, float y2, float z2,
	                          float nx, float ny, float nz, int argb) {
		buf.vertex(pose, x1, y1, z1).color(argb).normal(nx, ny, nz);
		buf.vertex(pose, x2, y2, z2).color(argb).normal(nx, ny, nz);
	}
}
