package com.avatarbending.mixin.client;

import com.avatarbending.client.particle.FxSheets;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.texture.TextureManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Queue;

/**
 * Draws the mod's additive (glowing) particles after the vanilla particle sheets. Vanilla only
 * renders a fixed list of sheets, so the additive sheet needs this hook.
 */
@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin {
	@Shadow
	@Final
	private Map<ParticleTextureSheet, Queue<Particle>> particles;

	@Shadow
	@Final
	private TextureManager textureManager;

	@Inject(method = "renderParticles", at = @At("TAIL"))
	private void avatarbending$renderAdditive(LightmapTextureManager lightmap, Camera camera, float tickDelta, CallbackInfo ci) {
		Queue<Particle> queue = particles.get(FxSheets.ADDITIVE);
		if (queue == null || queue.isEmpty()) {
			return;
		}
		lightmap.enable();
		RenderSystem.enableDepthTest();
		RenderSystem.setShader(GameRenderer::getParticleProgram);
		BufferBuilder buffer = FxSheets.ADDITIVE.begin(Tessellator.getInstance(), textureManager);
		if (buffer != null) {
			for (Particle particle : queue) {
				particle.buildGeometry(buffer, camera, tickDelta);
			}
			BuiltBuffer built = buffer.endNullable();
			if (built != null) {
				BufferRenderer.drawWithGlobalProgram(built);
			}
		}
		RenderSystem.depthMask(true);
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableBlend();
		lightmap.disable();
	}
}
