package com.avatarbending.client.fx;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Camera shake and full-screen flashes. Both respect the vanilla "Distortion Effects" accessibility
 * slider, so players who dislike screen shake can turn it down, and "Hide Lightning Flashes" turns
 * the flashes off completely.
 */
public final class ScreenEffects {
	private static float shakeStrength;
	private static int shakeTicks;
	private static int shakeTotal;
	private static int flashColor;
	private static float flashAlpha;
	private static int flashTicks;
	private static int flashTotal;

	private ScreenEffects() {
	}

	private static float comfort() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client.options == null ? 1f : client.options.getDistortionEffectScale().getValue().floatValue();
	}

	/** Shake the camera. Stronger shakes replace weaker ones. */
	public static void shake(float strength, int ticks) {
		float current = shakeTicks > 0 ? shakeStrength * shakeTicks / (float) shakeTotal : 0f;
		if (strength > current) {
			shakeStrength = strength;
			shakeTicks = shakeTotal = Math.max(1, ticks);
		}
	}

	/** Shake that fades with the distance between the camera and {@code pos}. */
	public static void shakeAt(Vec3d pos, float strength, double radius, int ticks) {
		double d = distanceToCamera(pos);
		if (d < radius) {
			shake((float) (strength * (1 - d / radius)), ticks);
		}
	}

	public static void flash(int rgb, float alpha, int ticks) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.options != null && client.options.getHideLightningFlashes().getValue()) {
			return;
		}
		float current = flashTicks > 0 ? flashAlpha * flashTicks / (float) flashTotal : 0f;
		if (alpha > current) {
			flashColor = rgb;
			flashAlpha = alpha;
			flashTicks = flashTotal = Math.max(1, ticks);
		}
	}

	public static void flashAt(Vec3d pos, int rgb, float alpha, double radius, int ticks) {
		double d = distanceToCamera(pos);
		if (d < radius) {
			flash(rgb, (float) (alpha * (1 - d / radius)), ticks);
		}
	}

	public static double distanceToCamera(Vec3d pos) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.gameRenderer == null || client.gameRenderer.getCamera() == null) {
			return Double.MAX_VALUE;
		}
		return client.gameRenderer.getCamera().getPos().distanceTo(pos);
	}

	public static void tick() {
		if (shakeTicks > 0) {
			shakeTicks--;
		}
		if (flashTicks > 0) {
			flashTicks--;
		}
	}

	public static void clear() {
		shakeTicks = 0;
		flashTicks = 0;
	}

	/** Camera rotation offset (yaw, pitch) in degrees, or null when not shaking. */
	public static float[] cameraShake(float tickDelta) {
		if (shakeTicks <= 0) {
			return null;
		}
		float k = MathHelper.clamp((shakeTicks - tickDelta) / shakeTotal, 0f, 1f);
		float amp = shakeStrength * k * k * 1.6f * comfort();
		if (amp < 0.01f) {
			return null;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		float time = (client.world == null ? 0 : client.world.getTime()) + tickDelta;
		float yaw = amp * (MathHelper.sin(time * 2.3f) * 0.6f + MathHelper.sin(time * 5.7f + 1.3f) * 0.4f);
		float pitch = amp * (MathHelper.sin(time * 2.9f + 2.1f) * 0.6f + MathHelper.sin(time * 6.4f) * 0.4f);
		return new float[] {yaw, pitch};
	}

	public static void renderFlash(DrawContext context, float tickDelta) {
		if (flashTicks <= 0) {
			return;
		}
		float k = MathHelper.clamp((flashTicks - tickDelta) / flashTotal, 0f, 1f);
		float a = flashAlpha * k * k * Math.max(0.35f, comfort());
		int alpha = MathHelper.clamp((int) (a * 255), 0, 255);
		if (alpha <= 2) {
			return;
		}
		context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), (alpha << 24) | (flashColor & 0xFFFFFF));
	}
}
