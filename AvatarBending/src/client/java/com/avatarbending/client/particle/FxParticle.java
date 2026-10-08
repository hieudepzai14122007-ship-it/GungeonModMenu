package com.avatarbending.client.particle;

import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;

/**
 * A configurable billboard particle used by every bending effect. Builder-style setters control
 * color (with a gradient over its life), size, fading, motion and blending.
 */
public class FxParticle extends SpriteBillboardParticle {
	private final SpriteProvider sprites;
	private boolean animated;
	private float sizeStart = 0.25f;
	private float sizeEnd = 0f;
	private float alphaStart = 1f;
	private float alphaEnd = 0f;
	private float fadeIn;
	private int colorStart = 0xFFFFFF;
	private int colorEnd = -1;
	private boolean bright = true;
	private boolean additive = true;
	private float spin;

	public FxParticle(ClientWorld world, double x, double y, double z, double vx, double vy, double vz, SpriteProvider sprites) {
		super(world, x, y, z);
		this.velocityX = vx;
		this.velocityY = vy;
		this.velocityZ = vz;
		this.sprites = sprites;
		this.setSprite(sprites);
		this.collidesWithWorld = false;
		this.gravityStrength = 0f;
		this.velocityMultiplier = 0.9f;
		this.maxAge = 20;
		this.angle = this.prevAngle = this.random.nextFloat() * MathHelper.TAU;
		applyLife(0f);
	}

	public FxParticle life(int ticks) {
		this.maxAge = Math.max(1, ticks);
		return this;
	}

	public FxParticle size(float start, float end) {
		this.sizeStart = start;
		this.sizeEnd = end;
		return this;
	}

	public FxParticle color(int rgb) {
		this.colorStart = rgb;
		this.colorEnd = -1;
		applyLife(0f);
		return this;
	}

	public FxParticle color(int from, int to) {
		this.colorStart = from;
		this.colorEnd = to;
		applyLife(0f);
		return this;
	}

	public FxParticle alpha(float start, float end) {
		this.alphaStart = start;
		this.alphaEnd = end;
		applyLife(0f);
		return this;
	}

	/** Fraction of the life spent fading in. */
	public FxParticle fadeIn(float fraction) {
		this.fadeIn = fraction;
		applyLife(0f);
		return this;
	}

	public FxParticle gravity(float gravity) {
		this.gravityStrength = gravity;
		return this;
	}

	public FxParticle drag(float drag) {
		this.velocityMultiplier = drag;
		return this;
	}

	public FxParticle spin(float radiansPerTick) {
		this.spin = radiansPerTick;
		return this;
	}

	public FxParticle animated() {
		this.animated = true;
		this.setSpriteForAge(sprites);
		return this;
	}

	/** Lit by the world instead of glowing. */
	public FxParticle lit() {
		this.bright = false;
		return this;
	}

	/** Normal alpha blending instead of additive light. */
	public FxParticle solid() {
		this.additive = false;
		return this;
	}

	public FxParticle collide() {
		this.collidesWithWorld = true;
		return this;
	}

	@Override
	public void tick() {
		super.tick();
		this.prevAngle = this.angle;
		this.angle += spin;
		if (animated && !this.dead) {
			this.setSpriteForAge(sprites);
		}
		applyLife(this.age / (float) this.maxAge);
	}

	private void applyLife(float t) {
		int c = colorEnd < 0 ? colorStart : lerpColor(colorStart, colorEnd, t);
		this.red = ((c >> 16) & 0xFF) / 255f;
		this.green = ((c >> 8) & 0xFF) / 255f;
		this.blue = (c & 0xFF) / 255f;
		float a = MathHelper.lerp(t, alphaStart, alphaEnd);
		if (fadeIn > 0 && t < fadeIn) {
			a *= t / fadeIn;
		}
		this.alpha = MathHelper.clamp(a, 0f, 1f);
	}

	private static int lerpColor(int a, int b, float t) {
		int r = (int) MathHelper.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
		int g = (int) MathHelper.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
		int bl = (int) MathHelper.lerp(t, a & 0xFF, b & 0xFF);
		return (r << 16) | (g << 8) | bl;
	}

	@Override
	public float getSize(float tickDelta) {
		float t = MathHelper.clamp((this.age + tickDelta) / this.maxAge, 0f, 1f);
		return MathHelper.lerp(t, sizeStart, sizeEnd);
	}

	@Override
	protected int getBrightness(float tint) {
		return bright ? LightmapTextureManager.MAX_LIGHT_COORDINATE : super.getBrightness(tint);
	}

	@Override
	public ParticleTextureSheet getType() {
		return additive ? FxSheets.ADDITIVE : ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
	}
}
