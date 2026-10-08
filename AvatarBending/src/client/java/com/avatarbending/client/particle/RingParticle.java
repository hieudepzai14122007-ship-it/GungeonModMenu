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
import org.joml.Vector3f;

/**
 * A flat ring (or magic circle) lying in the plane perpendicular to an axis. It can expand
 * like a shockwave or stay at a fixed size and spin.
 */
public class RingParticle extends Particle {
	private final Sprite sprite;
	private final Vector3f u;
	private final Vector3f v;
	private final float radius;
	private boolean expand = true;
	private float spin;
	private float fadeIn;
	private float alphaStart = 0.9f;
	private boolean additive = true;

	public RingParticle(ClientWorld world, double x, double y, double z, Vec3d axis, float radius, int life, Sprite sprite) {
		super(world, x, y, z);
		this.sprite = sprite;
		this.radius = radius;
		this.maxAge = Math.max(1, life);
		this.collidesWithWorld = false;
		this.gravityStrength = 0;
		this.velocityMultiplier = 1f;
		this.velocityX = this.velocityY = this.velocityZ = 0;
		Vec3d n = axis.lengthSquared() < 1.0E-6 ? new Vec3d(0, 1, 0) : axis.normalize();
		Vec3d helper = Math.abs(n.y) < 0.95 ? new Vec3d(0, 1, 0) : new Vec3d(1, 0, 0);
		Vec3d a = n.crossProduct(helper).normalize();
		Vec3d b = n.crossProduct(a).normalize();
		this.u = new Vector3f((float) a.x, (float) a.y, (float) a.z);
		this.v = new Vector3f((float) b.x, (float) b.y, (float) b.z);
		this.angle = this.prevAngle = this.random.nextFloat() * MathHelper.TAU;
	}

	public RingParticle color(int rgb) {
		this.red = ((rgb >> 16) & 0xFF) / 255f;
		this.green = ((rgb >> 8) & 0xFF) / 255f;
		this.blue = (rgb & 0xFF) / 255f;
		return this;
	}

	public RingParticle fixed(float spinPerTick, float fadeInFraction) {
		this.expand = false;
		this.spin = spinPerTick;
		this.fadeIn = fadeInFraction;
		return this;
	}

	public RingParticle alpha(float alpha) {
		this.alphaStart = alpha;
		return this;
	}

	public RingParticle velocity(double vx, double vy, double vz) {
		this.velocityX = vx;
		this.velocityY = vy;
		this.velocityZ = vz;
		return this;
	}

	public RingParticle solid() {
		this.additive = false;
		return this;
	}

	@Override
	public void tick() {
		super.tick();
		this.prevAngle = this.angle;
		this.angle += spin;
	}

	@Override
	public void buildGeometry(VertexConsumer consumer, Camera camera, float tickDelta) {
		float t = MathHelper.clamp((this.age + tickDelta) / this.maxAge, 0f, 1f);
		float r;
		float a;
		if (expand) {
			float ease = 1 - (1 - t) * (1 - t) * (1 - t);
			r = radius * (0.08f + 0.92f * ease);
			a = alphaStart * (float) Math.pow(1 - t, 1.3);
		} else {
			r = radius * Math.min(1f, t * 8f + 0.2f);
			a = alphaStart * Math.min(1f, fadeIn > 0 ? t / fadeIn : 1f) * Math.min(1f, (1 - t) * 5f);
		}
		if (a <= 0.003f) {
			return;
		}
		Vec3d cam = camera.getPos();
		float cx = (float) (MathHelper.lerp(tickDelta, this.prevPosX, this.x) - cam.x);
		float cy = (float) (MathHelper.lerp(tickDelta, this.prevPosY, this.y) - cam.y);
		float cz = (float) (MathHelper.lerp(tickDelta, this.prevPosZ, this.z) - cam.z);
		float ang = MathHelper.lerp(tickDelta, this.prevAngle, this.angle);
		float cos = MathHelper.cos(ang);
		float sin = MathHelper.sin(ang);
		Vector3f du = new Vector3f(u).mul(cos).add(new Vector3f(v).mul(sin)).mul(r);
		Vector3f dv = new Vector3f(v).mul(cos).sub(new Vector3f(u).mul(sin)).mul(r);
		float u0 = sprite.getMinU();
		float u1 = sprite.getMaxU();
		float v0 = sprite.getMinV();
		float v1 = sprite.getMaxV();
		int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
		float[][] corners = {
			{cx - du.x - dv.x, cy - du.y - dv.y, cz - du.z - dv.z, u0, v1},
			{cx + du.x - dv.x, cy + du.y - dv.y, cz + du.z - dv.z, u1, v1},
			{cx + du.x + dv.x, cy + du.y + dv.y, cz + du.z + dv.z, u1, v0},
			{cx - du.x + dv.x, cy - du.y + dv.y, cz - du.z + dv.z, u0, v0},
		};
		for (int i = 0; i < 4; i++) {
			emit(consumer, corners[i], a, light);
		}
		for (int i = 3; i >= 0; i--) {
			emit(consumer, corners[i], a, light);
		}
	}

	private void emit(VertexConsumer consumer, float[] c, float a, int light) {
		consumer.vertex(c[0], c[1], c[2]).texture(c[3], c[4]).color(this.red, this.green, this.blue, a).light(light);
	}

	@Override
	public ParticleTextureSheet getType() {
		return additive ? FxSheets.ADDITIVE : ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
	}
}
