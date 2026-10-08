package com.avatarbending.client.render;

import com.avatarbending.AvatarBending;
import com.avatarbending.client.ClientBenderState;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * In the Avatar State a player's eyes and arrow tattoos glow white-blue.
 */
public class AvatarEyesFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
	private static final Identifier TEXTURE = AvatarBending.id("textures/entity/avatar_glow.png");

	public AvatarEyesFeatureRenderer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
		super(context);
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, AbstractClientPlayerEntity entity,
		float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
		if (entity.isInvisible() || !ClientBenderState.isInAvatarState(entity.getId())) {
			return;
		}
		VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEyes(TEXTURE));
		getContextModel().render(matrices, consumer, 0xF00000, OverlayTexture.DEFAULT_UV);
	}
}
