package com.avatarbending.client.particle;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.TextureManager;

/**
 * Additive blending: overlapping particles add their light together, so glows, fire and lightning
 * look like real light. Rendered by ParticleManagerMixin right after the vanilla particle sheets.
 */
public final class FxSheets {
	public static final ParticleTextureSheet ADDITIVE = new ParticleTextureSheet() {
		@Override
		public BufferBuilder begin(Tessellator tessellator, TextureManager textureManager) {
			RenderSystem.depthMask(false);
			RenderSystem.setShaderTexture(0, SpriteAtlasTexture.PARTICLE_ATLAS_TEXTURE);
			RenderSystem.enableBlend();
			RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
			return tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR_LIGHT);
		}

		@Override
		public String toString() {
			return "AVATARBENDING_ADDITIVE";
		}
	};

	private FxSheets() {
	}
}
