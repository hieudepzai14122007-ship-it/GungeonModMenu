package com.avatarbending.client.render;

import com.avatarbending.entity.BlockProjectileEntity;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/**
 * Draws boulders, ice shards and meteors as a spinning block scaled to the entity's size.
 */
public class BlockProjectileRenderer<T extends BlockProjectileEntity> extends EntityRenderer<T> {
	private final BlockRenderManager blockRenderer;

	public BlockProjectileRenderer(EntityRendererFactory.Context context) {
		super(context);
		this.blockRenderer = context.getBlockRenderManager();
		this.shadowRadius = 0.4f;
	}

	@Override
	public void render(T entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
		BlockState state = entity.getBlockState();
		if (state.getRenderType() != BlockRenderType.MODEL) {
			return;
		}
		float size = entity.getWidth();
		float spin = (entity.age + tickDelta) * entity.spinSpeed();
		matrices.push();
		matrices.translate(0, entity.getHeight() / 2, 0);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(spin * 0.7f));
		matrices.scale(size, size, size);
		matrices.translate(-0.5, -0.5, -0.5);
		int blockLight = entity.fullBright() ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light;
		blockRenderer.renderBlockAsEntity(state, matrices, vertexConsumers, blockLight, OverlayTexture.DEFAULT_UV);
		matrices.pop();
		super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
	}

	@Override
	public Identifier getTexture(T entity) {
		return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
	}
}
