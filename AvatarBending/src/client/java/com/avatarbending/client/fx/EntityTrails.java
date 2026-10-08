package com.avatarbending.client.fx;

import com.avatarbending.client.particle.ModParticlesClient;
import com.avatarbending.entity.AirBladeEntity;
import com.avatarbending.entity.AirBlastEntity;
import com.avatarbending.entity.BlizzardEntity;
import com.avatarbending.entity.BoulderEntity;
import com.avatarbending.entity.FireBlastEntity;
import com.avatarbending.entity.FireDragonEntity;
import com.avatarbending.entity.IceShardEntity;
import com.avatarbending.entity.LavaBombEntity;
import com.avatarbending.entity.MaelstromEntity;
import com.avatarbending.entity.MeteorEntity;
import com.avatarbending.entity.RockEntity;
import com.avatarbending.entity.TidalWaveEntity;
import com.avatarbending.entity.TornadoEntity;
import com.avatarbending.entity.VolcanoEntity;
import com.avatarbending.entity.WaterBlastEntity;
import com.avatarbending.entity.WaterOrbEntity;
import com.avatarbending.fx.Colors;
import net.minecraft.entity.Entity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

import static com.avatarbending.client.fx.ClientFx.RANDOM;
import static com.avatarbending.client.fx.ClientFx.count;
import static com.avatarbending.client.fx.ClientFx.droplet;
import static com.avatarbending.client.fx.ClientFx.dust;
import static com.avatarbending.client.fx.ClientFx.ember;
import static com.avatarbending.client.fx.ClientFx.flame;
import static com.avatarbending.client.fx.ClientFx.glow;
import static com.avatarbending.client.fx.ClientFx.lighten;
import static com.avatarbending.client.fx.ClientFx.perpendicular;
import static com.avatarbending.client.fx.ClientFx.rand;
import static com.avatarbending.client.fx.ClientFx.randomUnit;
import static com.avatarbending.client.fx.ClientFx.spark;
import static com.avatarbending.client.fx.ClientFx.wind;

/**
 * Draws every bending entity on the client: projectile trails, the fire dragon's body, tornado
 * funnels, tidal waves, whirlpools, blizzards and volcanoes. Called each client tick through
 * {@link com.avatarbending.fx.ClientHooks}.
 */
public final class EntityTrails {
	private static final Vec3d UP = new Vec3d(0, 1, 0);
	private static final int DRAGON_LENGTH = 16;
	/** Recent positions of each fire dragon, head first, so its body can follow the head. */
	private static final Map<Entity, Deque<Vec3d>> DRAGON_BODIES = new WeakHashMap<>();

	private EntityTrails() {
	}

	public static void tick(Entity entity) {
		if (ClientFx.world() == null || ModParticlesClient.glow == null) {
			return;
		}
		if (entity instanceof AirBladeEntity e) {
			airBlade(e);
		} else if (entity instanceof AirBlastEntity e) {
			airBlast(e);
		} else if (entity instanceof FireDragonEntity e) {
			fireDragon(e);
		} else if (entity instanceof FireBlastEntity e) {
			fireBlast(e);
		} else if (entity instanceof WaterOrbEntity e) {
			waterOrb(e);
		} else if (entity instanceof WaterBlastEntity e) {
			waterBlast(e);
		} else if (entity instanceof IceShardEntity e) {
			iceShard(e);
		} else if (entity instanceof MeteorEntity e) {
			meteor(e);
		} else if (entity instanceof LavaBombEntity e) {
			lavaBomb(e);
		} else if (entity instanceof RockEntity e) {
			rock(e);
		} else if (entity instanceof BoulderEntity e) {
			boulder(e);
		} else if (entity instanceof TornadoEntity e) {
			tornado(e);
		} else if (entity instanceof TidalWaveEntity e) {
			tidalWave(e);
		} else if (entity instanceof MaelstromEntity e) {
			maelstrom(e);
		} else if (entity instanceof BlizzardEntity e) {
			blizzard(e);
		} else if (entity instanceof VolcanoEntity e) {
			volcano(e);
		}
	}

	private static Vec3d center(Entity e) {
		return e.getPos().add(0, e.getHeight() / 2, 0);
	}

	private static Vec3d heading(Entity e) {
		Vec3d v = e.getVelocity();
		return v.lengthSquared() < 1.0E-4 ? e.getRotationVec(1f) : v.normalize();
	}

	// ---------------------------------------------------------------- air

	private static void airBlast(AirBlastEntity e) {
		Vec3d p = center(e);
		Vec3d v = e.getVelocity();
		for (int i = 0, n = count(3); i < n; i++) {
			wind(p.add(randomUnit().multiply(0.25)), v.multiply(0.1).add(randomUnit().multiply(0.03))).color(0xFFFFFF)
				.size(0.5f, 1.3f).alpha(0.55f, 0f).life(8).drag(0.9f);
		}
		glow(p, Vec3d.ZERO).color(Colors.AIR).size(0.9f, 0.2f).alpha(0.35f, 0f).life(3);
		if (e.age % 3 == 0) {
			ClientFx.ring(p, v, 0.7f, 6).color(0xFFFFFF).alpha(0.4f);
		}
	}

	private static void airBlade(AirBladeEntity e) {
		Vec3d f = heading(e);
		Vec3d right = f.crossProduct(UP);
		right = right.lengthSquared() < 1.0E-4 ? new Vec3d(1, 0, 0) : right.normalize();
		Vec3d step = e.getVelocity().multiply(0.5);
		// Two crescents per tick (current and half a step back) so the fast blade leaves a solid trail.
		for (int sub = 0; sub < 2; sub++) {
			Vec3d p = center(e).subtract(step.multiply(sub));
			for (int k = -5; k <= 5; k++) {
				double a = k / 5.0 * 1.15;
				Vec3d q = p.add(right.multiply(Math.sin(a) * 1.3)).subtract(f.multiply((1 - Math.cos(a)) * 0.7));
				float edge = 1f - Math.abs(k) / 6f;
				glow(q, Vec3d.ZERO).color(0xFFFFFF, Colors.AIR).size(0.45f * edge + 0.1f, 0.05f).life(4);
				if (RANDOM.nextFloat() < 0.35f) {
					wind(q, f.multiply(-0.05)).color(0xFFFFFF).size(0.4f, 1.0f).alpha(0.45f, 0f).life(8);
				}
			}
			glow(p, Vec3d.ZERO).color(Colors.AIR).size(2.0f, 0.8f).alpha(0.3f, 0f).life(3);
		}
	}

	// ---------------------------------------------------------------- fire

	private static void fireBlast(FireBlastEntity e) {
		Vec3d p = center(e);
		Vec3d v = e.getVelocity();
		int color = e.color();
		boolean blue = e.style() == FireBlastEntity.STYLE_BLUE;
		boolean meteor = e.style() == FireBlastEntity.STYLE_METEOR;
		int hot = blue ? 0xC8F0FF : 0xFFE7A0;
		float s = meteor ? 1.6f : 1f;
		for (int i = 0, n = count(4 * s); i < n; i++) {
			flame(p.add(randomUnit().multiply(0.15 * s)), v.multiply(-0.12).add(randomUnit().multiply(0.03))).color(hot, color)
				.size(0.55f * s, 0.1f).life((int) rand(6, 10)).drag(0.9f);
		}
		glow(p, Vec3d.ZERO).color(color).size(0.9f * s, 0.3f).alpha(0.7f, 0f).life(3);
		glow(p, Vec3d.ZERO).color(hot).size(0.4f * s, 0.1f).life(2);
		if (RANDOM.nextFloat() < 0.5f) {
			ember(p, randomUnit().multiply(0.05)).color(hot, color).life(14);
		}
		if (meteor && e.age % 2 == 0) {
			dust(p, new Vec3d(0, 0.02, 0)).color(0x2A2420).size(0.8f, 2.0f).alpha(0.5f, 0f).life(30);
		}
	}

	private static void fireDragon(FireDragonEntity e) {
		Deque<Vec3d> body = DRAGON_BODIES.computeIfAbsent(e, k -> new ArrayDeque<>());
		Vec3d head = center(e);
		body.addFirst(head);
		while (body.size() > DRAGON_LENGTH) {
			body.removeLast();
		}
		Iterator<Vec3d> it = body.iterator();
		int i = 0;
		while (it.hasNext()) {
			Vec3d p = it.next();
			float t = i / (float) DRAGON_LENGTH;
			float size = 1.5f * (1 - t) + 0.35f;
			int color = t < 0.3f ? Colors.FIRE_CORE : t < 0.7f ? Colors.FIRE : 0xD02800;
			glow(p, Vec3d.ZERO).color(color).size(size, size * 0.5f).alpha(0.8f, 0f).life(3);
			if (i % 2 == 0 || RANDOM.nextFloat() < 0.3f) {
				flame(p.add(randomUnit().multiply(0.2 * size)), randomUnit().multiply(0.03).add(0, 0.02, 0)).color(0xFFE7A0, color)
					.size(size * 0.8f, 0.1f).life((int) rand(6, 10));
			}
			// Spiky fins along the back.
			if (i % 3 == 1 && i < DRAGON_LENGTH - 2) {
				flame(p.add(0, size * 0.5, 0), new Vec3d(0, 0.06, 0)).color(0xFFE7A0, Colors.FIRE).size(0.35f, 0.05f).life(5);
			}
			i++;
		}
		// Head: bright core, glowing eyes and whiskers of fire.
		Vec3d f = heading(e);
		Vec3d side = perpendicular(f);
		glow(head, Vec3d.ZERO).color(0xFFFFFF).size(0.7f, 0.3f).life(2);
		glow(head.add(f.multiply(0.4)).add(side.multiply(0.25)).add(0, 0.2, 0), Vec3d.ZERO).color(0xFFF6C0).size(0.18f, 0.1f).life(2);
		glow(head.add(f.multiply(0.4)).subtract(side.multiply(0.25)).add(0, 0.2, 0), Vec3d.ZERO).color(0xFFF6C0).size(0.18f, 0.1f).life(2);
		for (int k = -1; k <= 1; k += 2) {
			Vec3d whisker = side.multiply(k * 0.08).add(f.multiply(-0.06)).add(0, 0.02, 0);
			flame(head.add(side.multiply(k * 0.35)), whisker).color(0xFFE7A0, Colors.FIRE).size(0.25f, 0.05f).life(8);
		}
		for (int k = 0, n = count(2); k < n; k++) {
			ember(head, randomUnit().multiply(0.08)).life(18);
		}
	}

	private static void meteor(MeteorEntity e) {
		Vec3d p = center(e);
		Vec3d v = e.getVelocity();
		for (int i = 0, n = count(8); i < n; i++) {
			flame(p.add(randomUnit().multiply(1.0)), v.multiply(-0.1).add(randomUnit().multiply(0.05))).color(0xFFE7A0, Colors.LAVA)
				.size(1.2f, 0.3f).life((int) rand(10, 14));
		}
		for (int i = 0, n = count(3); i < n; i++) {
			dust(p.add(randomUnit().multiply(0.8)), new Vec3d(0, 0.05, 0)).color(0x2A2420).size(1.5f, 3.5f).alpha(0.6f, 0f)
				.life((int) rand(30, 45));
		}
		for (int i = 0, n = count(4); i < n; i++) {
			ember(p, randomUnit().multiply(0.2)).color(0xFFE7A0, Colors.LAVA).life(24);
		}
		glow(p, Vec3d.ZERO).color(Colors.LAVA).size(5f, 2f).alpha(0.5f, 0f).life(3);
	}

	private static void lavaBomb(LavaBombEntity e) {
		Vec3d p = center(e);
		Vec3d v = e.getVelocity();
		flame(p.add(randomUnit().multiply(0.2)), v.multiply(-0.1)).color(0xFFE7A0, Colors.LAVA).size(0.5f, 0.1f).life(8);
		glow(p, Vec3d.ZERO).color(Colors.LAVA).size(1.2f, 0.4f).alpha(0.5f, 0f).life(3);
		if (e.age % 2 == 0) {
			dust(p, new Vec3d(0, 0.03, 0)).color(0x302820).size(0.5f, 1.4f).alpha(0.5f, 0f).life(20);
		}
		if (RANDOM.nextFloat() < 0.3f) {
			ClientFx.vanilla(ParticleTypes.DRIPPING_LAVA, p, Vec3d.ZERO);
		}
	}

	// ---------------------------------------------------------------- water

	private static void waterBlast(WaterBlastEntity e) {
		Vec3d p = center(e);
		Vec3d f = heading(e);
		Vec3d side = perpendicular(f);
		Vec3d side2 = f.crossProduct(side);
		glow(p, Vec3d.ZERO).color(lighten(Colors.WATER, 0.3f), Colors.WATER).size(0.6f, 0.2f).life(4);
		for (int k = 0; k < 2; k++) {
			double a = e.age * 0.9 + k * Math.PI;
			Vec3d off = side.multiply(Math.cos(a) * 0.3).add(side2.multiply(Math.sin(a) * 0.3));
			glow(p.add(off), f.multiply(-0.04)).color(lighten(Colors.WATER, 0.4f), Colors.WATER_DEEP).size(0.3f, 0.1f).life(8);
		}
		if (RANDOM.nextFloat() < 0.6f) {
			droplet(p.add(randomUnit().multiply(0.2)), f.multiply(0.1).add(randomUnit().multiply(0.05))).color(0xCFEFFF, Colors.WATER)
				.size(0.18f, 0.1f).life(12);
		}
		if (e.age % 3 == 0) {
			ClientFx.vanilla(ParticleTypes.SPLASH, p, Vec3d.ZERO);
		}
	}

	private static void waterOrb(WaterOrbEntity e) {
		Vec3d p = center(e);
		glow(p, Vec3d.ZERO).color(Colors.WATER).size(1.4f, 0.8f).alpha(0.6f, 0f).life(3);
		glow(p, Vec3d.ZERO).color(0xE0F6FF).size(0.5f, 0.2f).life(2);
		for (int i = 0, n = count(6); i < n; i++) {
			Vec3d dir = randomUnit();
			Vec3d tangent = dir.crossProduct(UP);
			glow(p.add(dir.multiply(0.55)), tangent.multiply(0.04)).color(lighten(Colors.WATER, 0.35f), Colors.WATER_DEEP)
				.size(0.3f, 0.1f).life(4);
		}
		if (RANDOM.nextFloat() < 0.5f) {
			spark(p.add(randomUnit().multiply(0.6)), Vec3d.ZERO).color(Colors.ICE).size(0.15f, 0).life(6);
		}
		if (RANDOM.nextFloat() < 0.4f) {
			droplet(p.add(randomUnit().multiply(0.4)), e.getVelocity().multiply(0.2)).color(0xCFEFFF, Colors.WATER).size(0.18f, 0.1f)
				.life(14);
		}
	}

	private static void iceShard(IceShardEntity e) {
		Vec3d p = center(e);
		spark(p, Vec3d.ZERO).color(Colors.ICE).size(0.18f, 0).life(6);
		glow(p, Vec3d.ZERO).color(Colors.ICE).size(0.4f, 0.1f).alpha(0.5f, 0f).life(3);
		if (RANDOM.nextFloat() < 0.4f) {
			ClientFx.vanilla(ParticleTypes.SNOWFLAKE, p, Vec3d.ZERO);
		}
	}

	private static void tidalWave(TidalWaveEntity e) {
		Vec3d base = e.getPos();
		Vec3d dir = e.direction();
		Vec3d perp = e.perpendicular();
		double halfWidth = TidalWaveEntity.HALF_WIDTH * e.scale();
		double height = TidalWaveEntity.HEIGHT * e.scale();
		for (int i = 0, n = count(56 * e.scale()); i < n; i++) {
			double s = rand(-halfWidth, halfWidth);
			double edge = 1 - (s / halfWidth) * (s / halfWidth);
			double top = height * (0.35 + 0.65 * edge);
			double h = RANDOM.nextDouble() * top;
			double curl = (h / height) * (h / height) * 1.4;
			Vec3d p = base.add(perp.multiply(s)).add(dir.multiply(curl)).add(0, h, 0);
			glow(p, dir.multiply(0.12).add(0, 0.02, 0)).color(lighten(Colors.WATER, 0.25f), Colors.WATER_DEEP).size(1.0f, 0.4f)
				.alpha(0.7f, 0f).life(6);
			if (i % 3 == 0) {
				droplet(p, dir.multiply(0.15).add(0, rand(0.0, 0.1), 0)).color(0xCFEFFF, Colors.WATER).size(0.22f, 0.1f).life(14);
			}
		}
		for (int i = 0, n = count(12 * e.scale()); i < n; i++) {
			double s = rand(-halfWidth, halfWidth);
			double edge = 1 - (s / halfWidth) * (s / halfWidth);
			double top = height * (0.35 + 0.65 * edge);
			Vec3d p = base.add(perp.multiply(s)).add(dir.multiply(1.1)).add(0, top, 0);
			glow(p, dir.multiply(0.15)).color(0xEAF6FF).size(0.5f, 0.2f).life(5);
			if (RANDOM.nextFloat() < 0.5f) {
				droplet(p, dir.multiply(0.25).add(0, 0.15, 0)).color(0xEAF6FF, Colors.WATER).size(0.2f, 0.1f).life(16);
			}
		}
		for (int i = 0, n = count(8); i < n; i++) {
			ClientFx.vanilla(ParticleTypes.SPLASH, base.add(perp.multiply(rand(-halfWidth, halfWidth))).add(dir), Vec3d.ZERO);
		}
		// Foam churning at the foot of the wave.
		for (int i = 0, n = count(4); i < n; i++) {
			dust(base.add(perp.multiply(rand(-halfWidth, halfWidth))).add(dir.multiply(0.5)).add(0, 0.2, 0), dir.multiply(0.08))
				.color(0xEAF6FF).size(0.8f, 1.8f).alpha(0.5f, 0f).life(16);
		}
	}

	private static void maelstrom(MaelstromEntity e) {
		double radius = MaelstromEntity.RADIUS * e.scale();
		Vec3d base = e.getPos().add(0, 0.15, 0);
		for (int i = 0, n = count(26); i < n; i++) {
			double r = Math.sqrt(RANDOM.nextDouble()) * radius;
			double a = RANDOM.nextDouble() * Math.PI * 2;
			double depth = -0.4 * (1 - r / radius);
			Vec3d p = base.add(Math.cos(a) * r, depth, Math.sin(a) * r);
			Vec3d tangent = new Vec3d(-Math.sin(a), 0, Math.cos(a)).multiply(0.12 + 0.35 * (1 - r / radius));
			Vec3d inward = new Vec3d(-Math.cos(a), 0, -Math.sin(a)).multiply(0.04);
			glow(p, tangent.add(inward)).color(lighten(Colors.WATER, 0.3f), Colors.WATER_DEEP).size(0.65f, 0.25f).alpha(0.7f, 0f).life(8);
		}
		for (int i = 0, n = count(5); i < n; i++) {
			double a = e.age * 0.6 + i * 1.3;
			Vec3d p = base.add(Math.cos(a) * 0.5, rand(0, 3), Math.sin(a) * 0.5);
			glow(p, new Vec3d(0, 0.12, 0)).color(lighten(Colors.WATER, 0.4f), Colors.WATER).size(0.5f, 0.1f).life(6);
		}
		if (e.age % 10 == 0) {
			ClientFx.ring(base, UP, (float) radius, 12).color(Colors.WATER).alpha(0.5f);
		}
		if (e.age % 2 == 0) {
			double a = RANDOM.nextDouble() * Math.PI * 2;
			double r = rand(0, radius);
			ClientFx.vanilla(ParticleTypes.SPLASH, base.add(Math.cos(a) * r, 0.1, Math.sin(a) * r), Vec3d.ZERO);
		}
	}

	private static void blizzard(BlizzardEntity e) {
		double radius = BlizzardEntity.RADIUS * e.scale();
		Vec3d base = e.getPos();
		for (int i = 0, n = count(18); i < n; i++) {
			double r = Math.sqrt(RANDOM.nextDouble()) * radius;
			double a = RANDOM.nextDouble() * Math.PI * 2;
			Vec3d p = base.add(Math.cos(a) * r, rand(0.2, 6), Math.sin(a) * r);
			Vec3d tangent = new Vec3d(-Math.sin(a), -0.12, Math.cos(a)).multiply(0.45);
			wind(p, tangent).color(0xF4FAFF).size(0.5f, 1.2f).alpha(0.5f, 0f).life(10);
		}
		for (int i = 0, n = count(12); i < n; i++) {
			double r = Math.sqrt(RANDOM.nextDouble()) * radius;
			double a = RANDOM.nextDouble() * Math.PI * 2;
			Vec3d p = base.add(Math.cos(a) * r, rand(0.5, 7), Math.sin(a) * r);
			ClientFx.vanilla(ParticleTypes.SNOWFLAKE, p, new Vec3d(-Math.sin(a) * 0.3, -0.1, Math.cos(a) * 0.3));
		}
		if (e.age % 2 == 0) {
			for (int i = 0, n = count(2); i < n; i++) {
				double r = Math.sqrt(RANDOM.nextDouble()) * radius;
				double a = RANDOM.nextDouble() * Math.PI * 2;
				Vec3d p = base.add(Math.cos(a) * r, rand(7, 8.5), Math.sin(a) * r);
				dust(p, new Vec3d(-Math.sin(a) * 0.08, 0, Math.cos(a) * 0.08)).color(0xC8D4E0).size(2.5f, 4f).alpha(0.4f, 0f).life(30);
			}
		}
		if (e.age % 20 == 0) {
			ClientFx.ring(base.add(0, 0.1, 0), UP, (float) radius, 16).color(Colors.ICE).alpha(0.5f);
		}
	}

	// ---------------------------------------------------------------- earth & lava

	private static void rock(RockEntity e) {
		Vec3d p = center(e);
		if (e.orbiting()) {
			if (RANDOM.nextFloat() < 0.3f) {
				dust(p, new Vec3d(0, -0.02, 0)).color(ClientFx.groundColor(e.getPos())).size(0.25f, 0.6f).alpha(0.5f, 0f).life(10);
			}
			return;
		}
		dust(p, e.getVelocity().multiply(-0.05)).color(ClientFx.groundColor(e.getPos())).size(0.3f, 0.9f).alpha(0.6f, 0f).life(12);
	}

	private static void boulder(BoulderEntity e) {
		Vec3d p = center(e);
		Vec3d v = e.getVelocity();
		if (v.lengthSquared() < 0.2) {
			// Still rising out of the ground.
			for (int i = 0, n = count(2); i < n; i++) {
				ClientFx.vanilla(new BlockStateParticleEffect(ParticleTypes.BLOCK, e.getBlockState()), p.add(randomUnit().multiply(0.5)),
					new Vec3d(0, -0.1, 0));
			}
			return;
		}
		dust(p, v.multiply(-0.05)).color(ClientFx.groundColor(e.getPos())).size(0.6f, 1.6f).alpha(0.55f, 0f).life(16);
		if (RANDOM.nextFloat() < 0.4f) {
			ClientFx.vanilla(new BlockStateParticleEffect(ParticleTypes.BLOCK, e.getBlockState()), p, Vec3d.ZERO);
		}
	}

	private static void tornado(TornadoEntity e) {
		double height = TornadoEntity.HEIGHT * e.scale();
		double radius = TornadoEntity.RADIUS * e.scale();
		boolean fiery = e.fiery();
		Vec3d base = e.getPos();
		for (int i = 0, n = count(fiery ? 20 : 16); i < n; i++) {
			double t = RANDOM.nextDouble();
			double h = t * height;
			double r = 0.35 + t * t * radius * 1.1;
			double a = RANDOM.nextDouble() * Math.PI * 2;
			Vec3d p = base.add(Math.cos(a) * r, h, Math.sin(a) * r);
			Vec3d tangent = new Vec3d(-Math.sin(a), 0, Math.cos(a)).multiply(0.25 + 0.25 * t).add(0, 0.08, 0);
			if (fiery) {
				flame(p, tangent).color(0xFFE7A0, Colors.FIRE).size((float) (0.6 + t * 0.6), 0.2f).life((int) rand(8, 12));
				if (i % 3 == 0) {
					ember(p, tangent.multiply(1.5)).color(0xFFE7A0, Colors.FIRE).life(20).gravity(-0.02f);
				}
				if (i % 4 == 0) {
					dust(p, tangent.multiply(0.5)).color(0x2A2420).size(1.0f, 2.2f).alpha(0.4f, 0f).life(20);
				}
			} else {
				wind(p, tangent).color(0xF0F6FF).size((float) (0.6 + t * 0.6), (float) (1.4 + t)).alpha(0.5f, 0f).life(10);
			}
		}
		int ground = ClientFx.groundColor(base);
		for (int i = 0, n = count(3); i < n; i++) {
			double a = RANDOM.nextDouble() * Math.PI * 2;
			double r = rand(0.5, 1.8);
			dust(base.add(Math.cos(a) * r, 0.1, Math.sin(a) * r), new Vec3d(-Math.sin(a) * 0.15, rand(0.08, 0.2), Math.cos(a) * 0.15))
				.color(ground).size(0.8f, 1.8f).alpha(0.6f, 0f).life(20);
		}
		if (fiery) {
			glow(base.add(0, height * 0.45, 0), Vec3d.ZERO).color(Colors.FIRE).size((float) radius * 1.6f, (float) radius).alpha(0.18f, 0f)
				.life(3);
		}
		if (e.age % 6 == 0) {
			ClientFx.ring(base.add(0, 0.1, 0), UP, (float) radius, 10).color(fiery ? Colors.FIRE : Colors.AIR).alpha(0.4f);
		}
	}

	private static void volcano(VolcanoEntity e) {
		Vec3d c = e.crater();
		glow(c, Vec3d.ZERO).color(Colors.LAVA).size(2.5f, 1.5f).alpha(0.6f, 0f).life(3);
		for (int i = 0, n = count(5); i < n; i++) {
			flame(c.add(randomUnit().multiply(0.5)), new Vec3d(rand(-0.1, 0.1), rand(0.2, 0.5), rand(-0.1, 0.1))).color(0xFFE7A0, Colors.LAVA)
				.size(0.9f, 0.2f).life(14);
		}
		for (int i = 0, n = count(3); i < n; i++) {
			dust(c.add(0, 1, 0).add(randomUnit()), new Vec3d(rand(-0.05, 0.05), rand(0.1, 0.2), rand(-0.05, 0.05))).color(0x302820)
				.size(2f, 5f).alpha(0.6f, 0f).life(60);
		}
		for (int i = 0, n = count(4); i < n; i++) {
			ember(c, new Vec3d(rand(-0.2, 0.2), rand(0.3, 0.7), rand(-0.2, 0.2))).color(0xFFE7A0, Colors.LAVA).life(30).gravity(0.5f);
		}
		if (RANDOM.nextFloat() < 0.5f) {
			ClientFx.vanilla(ParticleTypes.LAVA, c, Vec3d.ZERO);
		}
	}
}
