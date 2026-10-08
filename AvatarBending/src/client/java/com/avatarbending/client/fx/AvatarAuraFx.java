package com.avatarbending.client.fx;

import com.avatarbending.fx.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

import static com.avatarbending.client.fx.ClientFx.count;
import static com.avatarbending.client.fx.ClientFx.droplet;
import static com.avatarbending.client.fx.ClientFx.dust;
import static com.avatarbending.client.fx.ClientFx.flame;
import static com.avatarbending.client.fx.ClientFx.glow;
import static com.avatarbending.client.fx.ClientFx.rand;
import static com.avatarbending.client.fx.ClientFx.randomUnit;
import static com.avatarbending.client.fx.ClientFx.spark;
import static com.avatarbending.client.fx.ClientFx.wind;

/**
 * The Avatar State aura: four elemental orbs orbiting the Avatar inside a swirling sphere of air,
 * a soft halo and rising sparks. Rendered on every client that can see the player.
 */
public final class AvatarAuraFx {
	private static final int[] COLORS = {Colors.FIRE, Colors.WATER, Colors.EARTH_GREEN, 0xF4FAFF};

	private AvatarAuraFx() {
	}

	public static void tick(Entity e, int age) {
		Vec3d base = e.getPos();
		Vec3d chest = base.add(0, e.getHeight() * 0.55, 0);
		MinecraftClient client = MinecraftClient.getInstance();
		if (e == client.getCameraEntity() && client.options.getPerspective().isFirstPerson()) {
			// Seen from your own eyes: keep the aura at your feet so it never blocks the view.
			firstPerson(base, age);
			return;
		}
		for (int k = 0; k < 4; k++) {
			double ang = age * 0.17 + k * Math.PI / 2;
			double h = 1.0 + 0.5 * Math.sin(age * 0.09 + k * 1.3);
			Vec3d p = base.add(Math.cos(ang) * 1.5, h, Math.sin(ang) * 1.5);
			glow(p, Vec3d.ZERO).color(COLORS[k]).size(0.6f, 0.15f).life(5);
			glow(p, Vec3d.ZERO).color(0xFFFFFF).size(0.22f, 0.05f).life(3);
			Vec3d trail = new Vec3d(Math.sin(ang) * 0.04, 0.01, -Math.cos(ang) * 0.04);
			switch (k) {
				case 0 -> flame(p, trail).color(0xFFE7A0, Colors.FIRE).size(0.35f, 0.05f).life(9);
				case 1 -> droplet(p, trail).color(0xCFEFFF, Colors.WATER).size(0.15f, 0.08f).life(10).gravity(0.15f);
				case 2 -> dust(p, trail).color(Colors.EARTH).size(0.3f, 0.6f).alpha(0.6f, 0f).life(10);
				default -> wind(p, trail).color(0xFFFFFF).size(0.3f, 0.7f).alpha(0.55f, 0f).life(8);
			}
		}
		if (age % 2 == 0) {
			for (int i = 0, n = count(3); i < n; i++) {
				Vec3d dir = randomUnit();
				Vec3d tangent = new Vec3d(-dir.z, 0, dir.x).multiply(0.18);
				wind(chest.add(dir.multiply(2.1)), tangent).color(0xEAF6FF).size(0.6f, 1.2f).alpha(0.22f, 0f).life(10).drag(0.97f);
			}
			glow(chest, Vec3d.ZERO).color(Colors.AVATAR).size(2.2f, 1.6f).alpha(0.16f, 0f).life(3);
		}
		for (int i = 0, n = count(2); i < n; i++) {
			spark(base.add(rand(-0.6, 0.6), rand(0, 0.3), rand(-0.6, 0.6)), new Vec3d(0, rand(0.05, 0.12), 0)).color(Colors.AVATAR)
				.size(0.14f, 0).life((int) rand(12, 20)).drag(0.98f);
		}
		if (age % 40 == 0) {
			ClientFx.ring(base.add(0, 0.08, 0), new Vec3d(0, 1, 0), 4.5f, 16).color(Colors.AVATAR).alpha(0.55f);
		}
	}

	private static void firstPerson(Vec3d base, int age) {
		for (int k = 0; k < 4; k++) {
			double ang = age * 0.17 + k * Math.PI / 2;
			Vec3d p = base.add(Math.cos(ang) * 1.3, 0.15, Math.sin(ang) * 1.3);
			glow(p, Vec3d.ZERO).color(COLORS[k]).size(0.35f, 0.1f).life(4);
		}
		for (int i = 0, n = count(2); i < n; i++) {
			spark(base.add(rand(-0.8, 0.8), rand(0, 0.2), rand(-0.8, 0.8)), new Vec3d(0, rand(0.03, 0.06), 0)).color(Colors.AVATAR)
				.size(0.12f, 0).life((int) rand(10, 16)).drag(0.98f);
		}
		if (age % 40 == 0) {
			ClientFx.ring(base.add(0, 0.08, 0), new Vec3d(0, 1, 0), 4.5f, 16).color(Colors.AVATAR).alpha(0.55f);
		}
	}
}
