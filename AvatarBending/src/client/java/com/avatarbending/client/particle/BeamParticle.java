package com.avatarbending.client.particle;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * A glowing strip of light between two points, always turned towards the camera.
 * Used for lightning bolts, energy beams, cables and pillars of light.
 */
public class BeamParticle extends Particle {
	private final Sprite sprite;
	private final Vec3d delta;
	private final float width;
	private float alphaStart = 0.9f;
	private boolean flicker;

	public BeamParticle(ClientWorld world, Vec3d from, Vec3d to, float width, int life, Sprite sprite) {
		super(world, from.x, from.y, from.z);
		this.sprite = sprite;
		this.delta = to.subtract(from);
		this.width = width;
		this.maxAge = Math.max(1, life);
		this.collidesWithWorld = false;
		this.gravityStrength = 0;
		this.velocityX = this.velocityY = this.velocityZ = 0;
	}

	public BeamParticle color(int rgb) {
		this.red = ((rgb >> 16) & 0xFF) / 255f;
		this.green = ((rgb >> 8) & 0xFF) / 255f;
		this.blue = (rgb & 0xFF) / 255f;
		return this;
	}

	public BeamParticle alpha(float alpha) {
		this.alphaStart = alpha;
		return this;
	}

	public BeamParticle flicker() {
		this.flicker = true;
		return this;
	}

	@Override
	public void buildGeometry(VertexConsumer consumer, Camera camera, float tickDelta) {
		float t = MathHelper.clamp((this.age + tickDelta) / this.maxAge, 0f, 1f);
		float a = alphaStart * (1 - t * t);
		if (flicker) {
			a *= 0.65f + 0.35f * this.random.nextFloat();
		}
		Vec3d cam = camera.getPos();
		Vec3d start = new Vec3d(MathHelper.lerp(tickDelta, this.prevPosX, this.x) - cam.x,
			MathHelper.lerp(tickDelta, this.prevPosY, this.y) - cam.y,
			MathHelper.lerp(tickDelta, this.prevPosZ, this.z) - cam.z);
		Vec3d end = start.add(delta);
		Vec3d mid = start.add(end).multiply(0.5);
		Vec3d dir = delta.normalize();
		Vec3d side = dir.crossProduct(mid.multiply(-1));
		if (side.lengthSquared() < 1.0E-6) {
			side = dir.crossProduct(new Vec3d(0, 1, 0));
			if (side.lengthSquared() < 1.0E-6) {
				side = new Vec3d(1, 0, 0);
			}
		}
		side = side.normalize().multiply(width * 0.5);
		float u0 = sprite.getMinU();
		float u1 = sprite.getMaxU();
		float v0 = sprite.getMinV();
		float v1 = sprite.getMaxV();
		int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
		Vec3d[] corners = {start.subtract(side), start.add(side), end.add(side), end.subtract(side)};
		float[][] uv = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
		for (int i = 0; i < 4; i++) {
			emit(consumer, corners[i], uv[i], a, light);
		}
		for (int i = 3; i >= 0; i--) {
			emit(consumer, corners[i], uv[i], a, light);
		}
	}

	private void emit(VertexConsumer consumer, Vec3d p, float[] uv, float a, int light) {
		consumer.vertex((float) p.x, (float) p.y, (float) p.z).texture(uv[0], uv[1]).color(this.red, this.green, this.blue, a).light(light);
	}

	@Override
	public ParticleTextureSheet getType() {
		return FxSheets.ADDITIVE;
	}
}
