package dev.servergunnik.allymod.mixin;

import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.servergunnik.allymod.Allymod;
import dev.servergunnik.allymod.cache.RelationCache;
import dev.servergunnik.allymod.data.AllianceEntry;
import dev.servergunnik.allymod.data.RelationKind;
import dev.servergunnik.allymod.render.AlliancePrefix;
import dev.servergunnik.allymod.render.AllymodRenderState;
import dev.servergunnik.allymod.render.RenderProfiler;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void allymod$storeUuid(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
		if (entity instanceof Player) {
			((AllymodRenderState) state).allymod$setUuid(entity.getUUID());
		}
	}

	@Inject(method = "extractNameTags", at = @At("TAIL"))
	private void allymod$colorName(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
		if (!(entity instanceof Player player)) return;
		if (state.nameTag == null) return;
		RelationCache cache = Allymod.cache();
		RelationCache.RenderStatus status = cache == null ? null : cache.statusOf(entity.getUUID());
		AllianceEntry member = AlliancePrefix.lookup(player.getGameProfile().name());
		if (status == null && member == null) return;

		long start = RenderProfiler.enabled ? System.nanoTime() : 0L;
		Component name = state.nameTag;
		if (status != null) {
			TextColor color = TextColor.fromRgb(status.argb() & 0x00FFFFFF);
			Style styled = name.getStyle().withColor(color);
			String tag = status.kind() == RelationKind.ALLY ? "[A] " : "[E] ";
			name = Component.literal(tag).setStyle(styled)
					.append(name.copy().withStyle(styled));
		}
		if (member != null) {
			name = AlliancePrefix.prepend(member, name);
		}
		state.nameTag = name;
		if (RenderProfiler.enabled) RenderProfiler.recordNametag(System.nanoTime() - start);
	}

	@Inject(method = "submit", at = @At("TAIL"))
	private void allymod$drawWireframe(EntityRenderState state, PoseStack poseStack,
	                                    SubmitNodeCollector collector,
	                                    CameraRenderState camera, CallbackInfo ci) {
		UUID uuid = ((AllymodRenderState) state).allymod$uuid();
		if (uuid == null) return;

		RelationCache cache = Allymod.cache();
		if (cache == null) return;
		RelationCache.RenderStatus status = cache.statusOf(uuid);
		if (status == null) return;

		Minecraft mc = Minecraft.getInstance();
		if (mc != null && mc.player != null && uuid.equals(mc.player.getUUID())) return;

		float halfW = state.boundingBoxWidth / 2f;
		float h = state.boundingBoxHeight;
		int argb = status.argb();

		long start = RenderProfiler.enabled ? System.nanoTime() : 0L;
		collector.submitCustomGeometry(poseStack, RenderTypes.LINES,
				(pose, buf) -> drawBoxLines(pose, buf, -halfW, 0f, -halfW, halfW, h, halfW, argb));
		if (RenderProfiler.enabled) RenderProfiler.recordWireframe(System.nanoTime() - start);
	}

	private static void drawBoxLines(PoseStack.Pose pose, VertexConsumer buf,
	                                  float x1, float y1, float z1,
	                                  float x2, float y2, float z2,
	                                  int argb) {
		// dolne krawedzie
		line(pose, buf, x1, y1, z1, x2, y1, z1, 1f, 0f, 0f, argb);
		line(pose, buf, x2, y1, z1, x2, y1, z2, 0f, 0f, 1f, argb);
		line(pose, buf, x2, y1, z2, x1, y1, z2, 1f, 0f, 0f, argb);
		line(pose, buf, x1, y1, z2, x1, y1, z1, 0f, 0f, 1f, argb);
		// gorne krawedzie
		line(pose, buf, x1, y2, z1, x2, y2, z1, 1f, 0f, 0f, argb);
		line(pose, buf, x2, y2, z1, x2, y2, z2, 0f, 0f, 1f, argb);
		line(pose, buf, x2, y2, z2, x1, y2, z2, 1f, 0f, 0f, argb);
		line(pose, buf, x1, y2, z2, x1, y2, z1, 0f, 0f, 1f, argb);
		// pionowe krawedzie
		line(pose, buf, x1, y1, z1, x1, y2, z1, 0f, 1f, 0f, argb);
		line(pose, buf, x2, y1, z1, x2, y2, z1, 0f, 1f, 0f, argb);
		line(pose, buf, x2, y1, z2, x2, y2, z2, 0f, 1f, 0f, argb);
		line(pose, buf, x1, y1, z2, x1, y2, z2, 0f, 1f, 0f, argb);
	}

	private static final float LINE_WIDTH = 2f;

	private static void line(PoseStack.Pose pose, VertexConsumer buf,
	                          float x1, float y1, float z1,
	                          float x2, float y2, float z2,
	                          float nx, float ny, float nz, int argb) {
		buf.addVertex(pose, x1, y1, z1).setColor(argb).setNormal(pose, nx, ny, nz).setLineWidth(LINE_WIDTH);
		buf.addVertex(pose, x2, y2, z2).setColor(argb).setNormal(pose, nx, ny, nz).setLineWidth(LINE_WIDTH);
	}
}
