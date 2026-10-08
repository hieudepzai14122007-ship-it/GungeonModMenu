package com.avatarbending.client.fx;

import com.avatarbending.fx.AttachedFxType;
import com.avatarbending.fx.Colors;
import com.avatarbending.network.ModPayloads.AttachFxPayload;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.avatarbending.client.fx.ClientFx.count;
import static com.avatarbending.client.fx.ClientFx.droplet;
import static com.avatarbending.client.fx.ClientFx.dust;
import static com.avatarbending.client.fx.ClientFx.ember;
import static com.avatarbending.client.fx.ClientFx.flame;
import static com.avatarbending.client.fx.ClientFx.glow;
import static com.avatarbending.client.fx.ClientFx.lighten;
import static com.avatarbending.client.fx.ClientFx.rand;
import static com.avatarbending.client.fx.ClientFx.randomInCone;
import static com.avatarbending.client.fx.ClientFx.randomUnit;
import static com.avatarbending.client.fx.ClientFx.shard;
import static com.avatarbending.client.fx.ClientFx.spark;
import static com.avatarbending.client.fx.ClientFx.wind;

/**
 * Effects that follow an entity for a while. Rendered each client tick at the entity's current
 * position, so shields, tentacles, breath and beams move with it.
 */
public final class AttachedFxManager {
	private static final class Active {
		final int entityId;
		final AttachedFxType type;
		final int data;
		final Vec3d anchor;
		int remaining;
		int age;

		Active(int entityId, AttachedFxType type, int duration, int data, Vec3d anchor) {
			this.entityId = entityId;
			this.type = type;
			this.remaining = duration;
			this.data = data;
			this.anchor = anchor;
		}
	}

	private static final List<Active> ACTIVE = new ArrayList<>();
	private static final Vec3d UP = new Vec3d(0, 1, 0);

	private AttachedFxManager() {
	}

	public static void add(AttachFxPayload payload) {
		AttachedFxType type = AttachedFxType.byOrdinal(payload.type());
		if (type == null) {
			return;
		}
		ACTIVE.removeIf(a -> a.entityId == payload.entityId() && a.type == type);
		if (payload.duration() > 0) {
			ACTIVE.add(new Active(payload.entityId(), type, payload.duration(), payload.data(),
				new Vec3d(payload.ax(), payload.ay(), payload.az())));
		}
	}

	public static void clear() {
		ACTIVE.clear();
	}

	public static void tick(ClientWorld world) {
		Iterator<Active> it = ACTIVE.iterator();
		while (it.hasNext()) {
			Active a = it.next();
			Entity entity = world.getEntityById(a.entityId);
			if (entity == null || !entity.isAlive() || a.remaining-- <= 0) {
				it.remove();
				continue;
			}
			render(world, a, entity);
			a.age++;
		}
	}

	private static Vec3d chest(Entity e) {
		return e.getPos().add(0, e.getHeight() * 0.6, 0);
	}

	private static Vec3d hands(Entity e) {
		Vec3d look = e.getRotationVec(1f);
		if (ClientFx.isFirstPersonView(e)) {
			// From your own eyes: a little further out and lower, so it frames the view instead of covering it.
			return e.getEyePos().add(look.multiply(1.6)).add(0, -0.7, 0);
		}
		return e.getEyePos().add(look.multiply(0.8)).add(0, -0.3, 0);
	}

	private static void render(ClientWorld world, Active a, Entity e) {
		int age = a.age;
		switch (a.type) {
			case WIND_SHIELD -> {
				Vec3d c = chest(e);
				double r = 2.6;
				for (int i = 0, n = count(9); i < n; i++) {
					Vec3d dir = randomUnit();
					Vec3d tangent = new Vec3d(-dir.z, 0, dir.x).multiply(0.28);
					wind(c.add(dir.multiply(r)), tangent).color(0xFFFFFF).size(0.5f, 1.1f).alpha(0.45f, 0f).life(10).drag(0.96f);
				}
				if (age % 8 == 0) {
					ClientFx.ring(c, randomUnit().add(0, 2, 0), (float) r, 12).color(Colors.AIR).alpha(0.3f);
				}
			}
			case OCTOPUS -> {
				Vec3d base = e.getPos().add(0, 1.0, 0);
				for (int k = 0; k < 8; k++) {
					double baseAngle = k * Math.PI / 4 + age * 0.05;
					int segments = Math.max(4, count(10));
					for (int j = 1; j <= segments; j++) {
						double t = j / (double) segments;
						double wave = Math.sin(age * 0.28 + k * 1.7 + t * 4.5);
						double angle = baseAngle + wave * 0.35 * t;
						double r = t * 2.7;
						double h = Math.sin(t * Math.PI) * 1.1 + wave * 0.45 * t - t * 0.5;
						Vec3d p = base.add(Math.cos(angle) * r, h, Math.sin(angle) * r);
						glow(p, Vec3d.ZERO).color(lighten(Colors.WATER, 0.35f), Colors.WATER_DEEP).size((float) (0.42 * (1 - t * 0.55)), 0.1f)
							.life(3).alpha(0.85f, 0.2f);
						if (j == segments && ClientFx.RANDOM.nextFloat() < 0.35f) {
							droplet(p, randomUnit().multiply(0.06)).color(0xD8F2FF, Colors.WATER).size(0.16f, 0.08f).life(14);
						}
					}
				}
			}
			case AIR_SCOOTER -> {
				Vec3d c = e.getPos().add(0, 0.35, 0);
				for (int i = 0, n = count(10); i < n; i++) {
					Vec3d dir = randomUnit();
					Vec3d tangent = dir.crossProduct(new Vec3d(-e.getVelocity().z, 0, e.getVelocity().x).add(0.01, 0, 0)).normalize().multiply(0.3);
					wind(c.add(dir.multiply(0.55)), tangent).color(0xFFFFFF).size(0.3f, 0.7f).alpha(0.6f, 0f).life(6);
				}
				glow(c, Vec3d.ZERO).color(Colors.AIR).size(1.2f, 0.4f).alpha(0.35f, 0f).life(3);
			}
			case EARTH_ARMOR -> {
				if (age % 2 == 0) {
					Vec3d c = e.getPos();
					int color = ClientFx.groundColor(c);
					for (int i = 0; i < 3; i++) {
						double ang = age * 0.15 + i * Math.PI * 2 / 3;
						Vec3d p = c.add(Math.cos(ang) * 0.75, 0.3 + (i * 0.55) % 1.6, Math.sin(ang) * 0.75);
						shard(p, new Vec3d(-Math.sin(ang) * 0.08, 0.01, Math.cos(ang) * 0.08)).color(color).size(0.2f, 0.15f).life(6).gravity(0f);
					}
				}
				if (age % 6 == 0) {
					dust(e.getPos().add(rand(-0.4, 0.4), rand(0, 1.6), rand(-0.4, 0.4)), new Vec3d(0, 0.02, 0)).color(ClientFx.groundColor(e.getPos()))
						.size(0.4f, 0.9f).alpha(0.45f, 0f).life(14);
				}
			}
			case BLOODBENT -> {
				Vec3d c = chest(e);
				for (int i = 0, n = count(3); i < n; i++) {
					glow(c.add(randomUnit().multiply(e.getWidth() * 0.7)), new Vec3d(0, rand(0.02, 0.06), 0)).color(Colors.BLOOD, 0x400008)
						.size(0.35f, 0.05f).life(14).alpha(0.9f, 0f);
				}
				if (age % 10 == 0) {
					ClientFx.ring(e.getPos().add(0, 0.05, 0), UP, 1.6f, 14).color(Colors.BLOOD).alpha(0.6f);
				}
				Entity caster = world.getEntityById(a.data);
				if (caster != null && age % 2 == 0) {
					ClientFx.line(hands(caster), c, Colors.BLOOD, 0.6f);
				}
			}
			case SPIRIT_FORM -> {
				// From your own eyes, keep the glow below eye level so it never blocks the view.
				double top = ClientFx.isFirstPersonView(e) ? e.getHeight() * 0.5 : e.getHeight();
				for (int i = 0, n = count(5); i < n; i++) {
					Vec3d p = e.getPos().add(rand(-0.35, 0.35), rand(0, top), rand(-0.35, 0.35));
					glow(p, new Vec3d(0, rand(0.01, 0.04), 0)).color(Colors.SPIRIT, Colors.AVATAR).size(0.35f, 0.05f).life(12).alpha(0.7f, 0f);
				}
				if (age % 3 == 0 && !ClientFx.isFirstPersonView(e)) {
					spark(chest(e).add(randomUnit().multiply(0.8)), new Vec3d(0, 0.05, 0)).color(0xFFFFFF).size(0.15f, 0).life(10);
				}
			}
			case FIRE_JET -> {
				Vec3d feet = e.getPos();
				Vec3d vel = e.getVelocity();
				Vec3d exhaust = vel.lengthSquared() < 0.01 ? new Vec3d(0, -1, 0) : vel.normalize().multiply(-1);
				for (int i = 0, n = count(7); i < n; i++) {
					Vec3d d = randomInCone(exhaust, 0.25);
					flame(feet.add(0, 0.2, 0), d.multiply(rand(0.25, 0.45))).color(0xFFE7A0, Colors.FIRE).size(0.55f, 0.1f).life(10).drag(0.9f);
				}
				for (int i = 0, n = count(3); i < n; i++) {
					ember(feet, exhaust.multiply(0.2).add(randomUnit().multiply(0.1))).life(18);
				}
				glow(feet.add(0, 0.2, 0), Vec3d.ZERO).color(Colors.FIRE).size(1.3f, 0.3f).life(3).alpha(0.6f, 0f);
			}
			case WATER_SPOUT -> {
				Vec3d feet = e.getPos();
				int groundY = (int) Math.floor(feet.y) - 24;
				BlockPos p = BlockPos.ofFloored(feet);
				for (int dy = 0; dy < 24; dy++) {
					BlockPos q = p.down(dy);
					if (!world.getBlockState(q).getCollisionShape(world, q).isEmpty() || !world.getFluidState(q).isEmpty()) {
						groundY = q.getY() + 1;
						break;
					}
				}
				double height = feet.y - groundY;
				for (int i = 0, n = count(Math.max(4, height * 2.5)); i < n; i++) {
					double y = groundY + ClientFx.RANDOM.nextDouble() * Math.max(0.5, height);
					double ang = age * 0.6 + y * 1.3;
					Vec3d q = new Vec3d(feet.x + Math.cos(ang) * 0.45, y, feet.z + Math.sin(ang) * 0.45);
					glow(q, new Vec3d(0, 0.15, 0)).color(lighten(Colors.WATER, 0.3f), Colors.WATER_DEEP).size(0.45f, 0.2f).life(5).alpha(0.8f, 0f);
				}
				if (age % 6 == 0) {
					ClientFx.ring(new Vec3d(feet.x, groundY + 0.1, feet.z), UP, 2f, 12).color(Colors.WATER).alpha(0.6f);
					for (int i = 0; i < count(6); i++) {
						ClientFx.vanilla(ParticleTypes.SPLASH, new Vec3d(feet.x + rand(-1, 1), groundY + 0.2, feet.z + rand(-1, 1)), Vec3d.ZERO);
					}
				}
				droplet(feet.add(rand(-0.3, 0.3), 0.1, rand(-0.3, 0.3)), new Vec3d(0, -0.1, 0)).color(0xCFEFFF).size(0.15f, 0.1f).life(20);
			}
			case HEALING -> {
				Vec3d base = e.getPos();
				for (int k = 0; k < 2; k++) {
					double ang = age * 0.4 + k * Math.PI;
					double h = (age % 20) / 20.0 * e.getHeight() * 1.2;
					glow(base.add(Math.cos(ang) * 0.8, h, Math.sin(ang) * 0.8), new Vec3d(0, 0.03, 0)).color(Colors.HEAL, Colors.WATER)
						.size(0.32f, 0.05f).life(14);
				}
				if (age % 4 == 0) {
					spark(base.add(rand(-0.6, 0.6), rand(0.2, 1.8), rand(-0.6, 0.6)), new Vec3d(0, 0.04, 0)).color(0xE8FFF4).size(0.15f, 0).life(12);
				}
			}
			case CHARGE_HANDS -> {
				Vec3d target = hands(e);
				int color = a.data == 0 ? Colors.AVATAR : a.data;
				float progress = Math.min(1f, age / (float) Math.max(1, age + a.remaining));
				if (ClientFx.isFirstPersonView(e)) {
					progress *= 0.5f;
				}
				for (int i = 0, n = count(4 + progress * 4); i < n; i++) {
					Vec3d dir = randomUnit();
					Vec3d start = target.add(dir.multiply(rand(1.0, 1.8)));
					glow(start, target.subtract(start).multiply(1 / 8.0)).color(lighten(color, 0.3f), color).size(0.12f, 0.35f).life(8).drag(1f);
				}
				glow(target, Vec3d.ZERO).color(color).size(0.3f + 1.0f * progress, 0.2f).life(3);
				if (ClientFx.RANDOM.nextFloat() < 0.5f) {
					spark(target.add(randomUnit().multiply(0.25)), randomUnit().multiply(0.04)).color(lighten(color, 0.6f)).size(0.18f, 0).life(4);
				}
			}
			case DRAGON_BREATH -> {
				Vec3d look = e.getRotationVec(1f);
				boolean own = ClientFx.isFirstPersonView(e);
				// From your own eyes the fire starts further out and lower, so you can see where you breathe.
				Vec3d origin = own
					? e.getEyePos().add(look.multiply(1.8)).add(0, -0.45, 0)
					: e.getEyePos().add(look.multiply(0.6)).add(0, -0.15, 0);
				int color = a.data == 0 ? Colors.BLUE_FIRE : a.data;
				if (own) {
					// Looking straight down the cone, glowing particles would pile up into a white blob, so your
					// own breath is drawn as see-through fire instead.
					for (int i = 0, n = count(10); i < n; i++) {
						Vec3d d = randomInCone(look, 0.26);
						flame(origin, d.multiply(rand(0.7, 1.05))).color(0x9FD8FF, ClientFx.darken(color, 0.7f)).solid()
							.size(0.15f, 2.2f).alpha(0.65f, 0f).fadeIn(0.2f).life((int) rand(11, 15)).drag(0.95f);
					}
				} else {
					for (int i = 0, n = count(14); i < n; i++) {
						Vec3d d = randomInCone(look, 0.24);
						flame(origin, d.multiply(rand(0.7, 1.05))).color(0x9FD8FF, color).size(0.35f, 2.1f).life((int) rand(11, 15))
							.drag(0.95f);
					}
					for (int i = 0, n = count(5); i < n; i++) {
						glow(origin, randomInCone(look, 0.18).multiply(rand(0.6, 0.9))).color(color).size(0.6f, 1.8f).alpha(0.6f, 0f)
							.life(12).drag(0.95f);
					}
				}
				for (int i = 0, n = count(2); i < n; i++) {
					ember(origin, randomInCone(look, 0.35).multiply(rand(0.4, 0.8))).color(0xC8F0FF, color).life(24);
				}
				if (age % 2 == 0) {
					dust(origin.add(look.multiply(rand(4, 8))), randomInCone(look, 0.4).multiply(0.15).add(0, 0.05, 0)).color(0x283040)
						.size(1.2f, 2.8f).alpha(0.35f, 0f).life(26);
				}
			}
			case FROZEN -> {
				for (int i = 0, n = count(2); i < n; i++) {
					Vec3d p = e.getPos().add(rand(-0.6, 0.6) * e.getWidth(), rand(0, e.getHeight()), rand(-0.6, 0.6) * e.getWidth());
					spark(p, Vec3d.ZERO).color(Colors.ICE).size(0.16f, 0).life(10);
				}
				if (age % 3 == 0) {
					ClientFx.vanilla(ParticleTypes.SNOWFLAKE, e.getPos().add(rand(-0.5, 0.5), rand(0, e.getHeight()), rand(-0.5, 0.5)), Vec3d.ZERO);
				}
			}
			case ELEMENTAL_BEAM -> {
				Vec3d look = e.getRotationVec(1f);
				// Fired from the right hand, so the beam is visible from your own camera too.
				Vec3d right = look.crossProduct(UP);
				right = right.lengthSquared() < 1.0E-4 ? Vec3d.ZERO : right.normalize();
				Vec3d start = e.getEyePos().add(look.multiply(0.8)).add(right.multiply(0.4)).add(0, -0.4, 0);
				HitResult hit = e.raycast(40, 1f, false);
				Vec3d end = hit.getType() == HitResult.Type.MISS ? start.add(look.multiply(40)) : hit.getPos();
				ClientFx.beam(start, end, 1.5f, 2).color(Colors.AVATAR).alpha(0.45f);
				ClientFx.beam(start, end, 0.45f, 2).color(0xFFFFFF).alpha(0.95f);
				Vec3d axis = end.subtract(start);
				double len = axis.length();
				Vec3d side = ClientFx.perpendicular(axis);
				Vec3d side2 = axis.normalize().crossProduct(side);
				int[] colors = {Colors.FIRE, Colors.WATER, Colors.EARTH_GREEN, 0xFFFFFF};
				int steps = Math.max(6, count(len * 1.2));
				for (int k = 0; k < 4; k++) {
					for (int j = 0; j < steps; j++) {
						double t = j / (double) steps;
						double ang = age * 0.5 + k * Math.PI / 2 + t * len * 0.8;
						Vec3d p = start.add(axis.multiply(t)).add(side.multiply(Math.cos(ang) * 0.6)).add(side2.multiply(Math.sin(ang) * 0.6));
						glow(p, Vec3d.ZERO).color(colors[k]).size(0.3f, 0.1f).life(2);
					}
				}
				glow(end, Vec3d.ZERO).color(Colors.AVATAR).size(2.5f, 0.5f).life(3);
				for (int i = 0, n = count(4); i < n; i++) {
					spark(end, randomUnit().multiply(rand(0.2, 0.5))).color(0xFFFFFF).size(0.2f, 0).life(6);
				}
				// Rings racing along the beam: seen from behind they form a glowing tunnel.
				if (age % 3 == 0) {
					ClientFx.ring(start, look, 1.1f, 12).color(colors[(age / 3) % 4]).fixed(0.25f, 0.1f).alpha(0.8f)
						.velocity(look.x * 3, look.y * 3, look.z * 3);
				}
				for (int i = 0, n = count(3); i < n; i++) {
					Vec3d p = start.add(look.multiply(rand(0.5, 4)));
					spark(p, randomUnit().crossProduct(look).normalize().multiply(rand(0.1, 0.25)))
						.color(colors[ClientFx.RANDOM.nextInt(4)]).size(0.2f, 0).life(8);
				}
			}
			case METAL_CABLE -> {
				Vec3d from = hands(e);
				ClientFx.beam(from, a.anchor, 0.1f, 2).color(Colors.METAL).alpha(0.9f);
				ClientFx.beam(from, a.anchor, 0.28f, 2).color(0x808890).alpha(0.35f);
				if (age % 2 == 0) {
					spark(a.anchor, randomUnit().multiply(0.15)).color(0xFFE8A0).size(0.15f, 0).life(6);
				}
			}
			case ENERGY_LINK -> {
				Entity target = world.getEntityById(a.data);
				if (target != null) {
					Vec3d from = hands(e);
					Vec3d to = chest(target);
					ClientFx.beam(from, to, 0.9f, 2).color(Colors.SPIRIT).alpha(0.55f);
					ClientFx.beam(from, to, 0.25f, 2).color(0xFFFFFF).alpha(0.95f);
					for (int i = 0, n = count(3); i < n; i++) {
						glow(to.add(randomUnit().multiply(0.5)), new Vec3d(0, 0.04, 0)).color(Colors.SPIRIT).size(0.3f, 0).life(10);
					}
				}
			}
			case ELEMENT_STORM -> {
				Vec3d c = chest(e);
				double pulse = 0.5 + 0.5 * Math.sin(age * 0.2);
				double r = 2.5 + 5.5 * pulse;
				int[] colors = {Colors.FIRE, Colors.WATER, Colors.EARTH_GREEN, 0xFFFFFF};
				for (int k = 0; k < 4; k++) {
					for (int j = 0, n = count(7); j < n; j++) {
						double ang = age * 0.32 + k * Math.PI / 2 + j * 0.13;
						double h = Math.sin(age * 0.1 + k) * 1.2 * Math.sin(ang);
						Vec3d p = c.add(Math.cos(ang) * r, h, Math.sin(ang) * r);
						glow(p, Vec3d.ZERO).color(colors[k]).size(0.55f, 0.15f).life(3);
						if (j == 0) {
							switch (k) {
								case 0 -> flame(p, new Vec3d(0, 0.05, 0)).color(0xFFE7A0, Colors.FIRE).size(0.5f, 0.1f).life(8);
								case 1 -> droplet(p, Vec3d.ZERO).color(0xCFEFFF).size(0.2f, 0.1f).life(10);
								case 2 -> dust(p, Vec3d.ZERO).color(Colors.EARTH).size(0.5f, 0.9f).alpha(0.6f, 0f).life(10);
								default -> wind(p, Vec3d.ZERO).color(0xFFFFFF).size(0.5f, 0.9f).alpha(0.6f, 0f).life(8);
							}
						}
					}
				}
			}
			case AIR_CANNON_CHARGE -> {
				Vec3d target = hands(e);
				float near = ClientFx.isFirstPersonView(e) ? 0.45f : 1f;
				for (int i = 0, n = count(8); i < n; i++) {
					Vec3d dir = randomUnit();
					Vec3d start = target.add(dir.multiply(rand(1.5, 3.0)));
					Vec3d toward = target.subtract(start);
					Vec3d swirl = toward.crossProduct(UP).normalize().multiply(0.12);
					wind(start, toward.multiply(1 / 9.0).add(swirl)).color(0xFFFFFF).size(0.6f, 0.2f).alpha(0.2f, 0.7f).life(9).drag(1f);
				}
				glow(target, Vec3d.ZERO).color(Colors.AIR).size((0.6f + age * 0.04f) * near, 0.3f).life(3).alpha(0.7f, 0f);
				if (age % 5 == 0) {
					ClientFx.ring(target, e.getRotationVec(1f), 0.6f + age * 0.03f, 8).color(0xFFFFFF).alpha(0.5f);
				}
			}
		}
	}
}
