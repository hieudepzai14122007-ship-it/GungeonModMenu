package com.avatarbending.client.fx;

import com.avatarbending.bending.Element;
import com.avatarbending.client.particle.BeamParticle;
import com.avatarbending.client.particle.FxParticle;
import com.avatarbending.client.particle.ModParticlesClient;
import com.avatarbending.client.particle.RingParticle;
import com.avatarbending.fx.Colors;
import com.avatarbending.fx.FxType;
import com.avatarbending.network.ModPayloads.FxPayload;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side effect library: turns one {@link FxPayload} into a full particle show, plus the small
 * particle helpers every other effect uses.
 */
public final class ClientFx {
	public static final Random RANDOM = Random.create();
	private static final Vec3d UP = new Vec3d(0, 1, 0);

	private ClientFx() {
	}

	// ---------------------------------------------------------------- basics

	public static ClientWorld world() {
		return MinecraftClient.getInstance().world;
	}

	/** Scales particle counts by the player's "Particles" video setting. */
	public static int count(double base) {
		MinecraftClient client = MinecraftClient.getInstance();
		double factor = 1.0;
		if (client.options != null) {
			ParticlesMode mode = client.options.getParticles().getValue();
			factor = mode == ParticlesMode.MINIMAL ? 0.25 : mode == ParticlesMode.DECREASED ? 0.5 : 1.0;
		}
		double n = base * factor;
		int whole = (int) n;
		return whole + (RANDOM.nextDouble() < n - whole ? 1 : 0);
	}

	public static double rand(double min, double max) {
		return min + RANDOM.nextDouble() * (max - min);
	}

	public static Vec3d randomUnit() {
		double z = RANDOM.nextDouble() * 2 - 1;
		double a = RANDOM.nextDouble() * Math.PI * 2;
		double r = Math.sqrt(1 - z * z);
		return new Vec3d(r * Math.cos(a), z, r * Math.sin(a));
	}

	public static Vec3d randomInCone(Vec3d dir, double spread) {
		return dir.normalize().add(randomUnit().multiply(spread)).normalize();
	}

	public static Vec3d perpendicular(Vec3d axis) {
		Vec3d helper = Math.abs(axis.normalize().y) < 0.95 ? UP : new Vec3d(1, 0, 0);
		return axis.crossProduct(helper).normalize();
	}

	public static int lighten(int rgb, float f) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		r = (int) (r + (255 - r) * f);
		g = (int) (g + (255 - g) * f);
		b = (int) (b + (255 - b) * f);
		return (r << 16) | (g << 8) | b;
	}

	public static int darken(int rgb, float f) {
		int r = (int) (((rgb >> 16) & 0xFF) * f);
		int g = (int) (((rgb >> 8) & 0xFF) * f);
		int b = (int) ((rgb & 0xFF) * f);
		return (r << 16) | (g << 8) | b;
	}

	private static void add(Particle particle) {
		MinecraftClient.getInstance().particleManager.addParticle(particle);
	}

	private static FxParticle make(SpriteProvider sprites, Vec3d pos, Vec3d vel) {
		FxParticle p = new FxParticle(world(), pos.x, pos.y, pos.z, vel.x, vel.y, vel.z, sprites);
		add(p);
		return p;
	}

	public static FxParticle glow(Vec3d pos, Vec3d vel) {
		return make(ModParticlesClient.glow, pos, vel);
	}

	public static FxParticle spark(Vec3d pos, Vec3d vel) {
		return make(ModParticlesClient.spark, pos, vel).spin(rand(-0.4, 0.4) > 0 ? 0.3f : -0.3f);
	}

	public static FxParticle wind(Vec3d pos, Vec3d vel) {
		return make(ModParticlesClient.wind, pos, vel).solid().spin((float) rand(-0.35, 0.35));
	}

	public static FxParticle ember(Vec3d pos, Vec3d vel) {
		return make(ModParticlesClient.ember, pos, vel).color(0xFFE6A0, 0xFF3A00);
	}

	public static FxParticle flame(Vec3d pos, Vec3d vel) {
		return make(ModParticlesClient.flame, pos, vel).animated();
	}

	public static FxParticle droplet(Vec3d pos, Vec3d vel) {
		return make(ModParticlesClient.droplet, pos, vel).solid().gravity(1f).collide();
	}

	public static FxParticle dust(Vec3d pos, Vec3d vel) {
		return make(ModParticlesClient.dust, pos, vel).solid().lit();
	}

	public static FxParticle shard(Vec3d pos, Vec3d vel) {
		return make(ModParticlesClient.shard, pos, vel).solid().gravity(1f).collide().spin((float) rand(-0.5, 0.5));
	}

	public static RingParticle ring(Vec3d pos, Vec3d axis, float radius, int life) {
		RingParticle p = new RingParticle(world(), pos.x, pos.y, pos.z, axis, radius, life, ModParticlesClient.ring.getSprite(RANDOM));
		add(p);
		return p;
	}

	public static RingParticle sigilRing(Vec3d pos, float radius, int life) {
		RingParticle p = new RingParticle(world(), pos.x, pos.y, pos.z, UP, radius, life, ModParticlesClient.sigil.getSprite(RANDOM));
		add(p);
		return p;
	}

	public static BeamParticle beam(Vec3d from, Vec3d to, float width, int life) {
		BeamParticle p = new BeamParticle(world(), from, to, width, life, ModParticlesClient.beam.getSprite(RANDOM));
		add(p);
		return p;
	}

	public static void vanilla(net.minecraft.particle.ParticleEffect effect, Vec3d pos, Vec3d vel) {
		world().addParticle(effect, true, pos.x, pos.y, pos.z, vel.x, vel.y, vel.z);
	}

	/** Map color of the ground block under {@code pos}, for tinting dust. */
	public static int groundColor(Vec3d pos) {
		BlockState state = groundState(pos);
		int c = state.getMapColor(world(), BlockPos.ofFloored(pos)).color;
		return c == 0 ? Colors.EARTH : c;
	}

	public static BlockState groundState(Vec3d pos) {
		BlockPos p = BlockPos.ofFloored(pos);
		for (int i = 0; i < 4; i++) {
			BlockState s = world().getBlockState(p.down(i));
			if (!s.isAir() && s.getFluidState().isEmpty()) {
				return s;
			}
		}
		return Blocks.STONE.getDefaultState();
	}

	// ---------------------------------------------------------------- dispatcher

	public static void play(FxPayload payload) {
		if (world() == null || ModParticlesClient.glow == null) {
			return;
		}
		FxType type = FxType.byOrdinal(payload.type());
		if (type == null) {
			return;
		}
		Vec3d pos = new Vec3d(payload.x(), payload.y(), payload.z());
		Vec3d vec = new Vec3d(payload.vx(), payload.vy(), payload.vz());
		float s = payload.scale();
		int c = payload.color();
		int data = payload.data();
		if (isBurstLike(type)) {
			s *= nearCameraScale(pos);
		}
		switch (type) {
			case BURST -> burst(pos, c, s);
			case RING -> ring(pos, vec, s, data > 0 ? data : 12).color(c);
			case GROUND_SHOCKWAVE -> groundShockwave(pos, c, s);
			case BEAM -> energyBeam(pos, pos.add(vec), c, s, data > 0 ? data : 8);
			case LIGHTNING -> lightning(pos, pos.add(vec), c, s);
			case EXPLOSION -> explosion(pos, c, s, Element.byOrdinal(data));
			case CHARGE -> charge(pos, c, s, Math.max(1, data));
			case PILLAR -> pillar(pos, c, s, data > 0 ? data : 30);
			case SPIRAL -> spiral(pos, c, s, data / 10f);
			case WIND_BURST -> windBurst(pos, vec, s);
			case SPLASH -> splash(pos, s);
			case FROST -> frost(pos, s);
			case DEBRIS -> debris(pos, s, Block.getStateFromRawId(data));
			case FLAMES -> flames(pos, c, s);
			case SIGIL -> sigil(pos, c, s, Math.max(1, data));
			case FLASH -> ScreenEffects.flashAt(pos, c, 0.6f, s, 10);
			case SHAKE -> ScreenEffects.shakeAt(pos, s, Math.max(1, data), 14);
			case LINE -> line(pos, pos.add(vec), c, s);
			case SLASH -> slash(pos, vec, c, s);
			case CONE -> cone(pos, vec, c, s, Element.byOrdinal(data));
			case FIRE_RING -> fireRing(pos, c, s, Math.max(1, data));
		}
	}

	private static boolean isBurstLike(FxType type) {
		return switch (type) {
			case BURST, WIND_BURST, SPLASH, FROST, FLAMES, CHARGE, DEBRIS -> true;
			default -> false;
		};
	}

	/**
	 * Bursts that go off right in front of your own eyes (at your hands) are shrunk in first person,
	 * so casting never blinds you. Everyone else still sees them at full size.
	 */
	public static float nearCameraScale(Vec3d pos) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.options == null || !client.options.getPerspective().isFirstPerson()) {
			return 1f;
		}
		double d = ScreenEffects.distanceToCamera(pos);
		return d >= 3 ? 1f : (float) (0.3 + 0.7 * (d / 3));
	}

	/** True when {@code e} is the entity you are looking out of in first person. */
	public static boolean isFirstPersonView(net.minecraft.entity.Entity e) {
		MinecraftClient client = MinecraftClient.getInstance();
		return e == client.getCameraEntity() && client.options.getPerspective().isFirstPerson();
	}

	// ---------------------------------------------------------------- effects

	public static void burst(Vec3d pos, int color, float s) {
		glow(pos, Vec3d.ZERO).color(0xFFFFFF).size(0.9f * s, 0).life(6);
		glow(pos, Vec3d.ZERO).color(color).size(1.6f * s, 0).life(9);
		for (int i = 0, n = count(18 * s); i < n; i++) {
			Vec3d dir = randomUnit();
			glow(pos, dir.multiply(rand(0.08, 0.3) * s)).color(lighten(color, 0.3f), color).size((float) rand(0.2, 0.45) * s, 0)
				.life((int) rand(10, 20)).drag(0.86f);
		}
		for (int i = 0, n = count(10 * s); i < n; i++) {
			Vec3d dir = randomUnit();
			spark(pos, dir.multiply(rand(0.25, 0.5) * s)).color(lighten(color, 0.5f)).size(0.18f, 0).life((int) rand(6, 12)).drag(0.82f);
		}
	}

	public static void groundShockwave(Vec3d pos, int color, float radius) {
		Vec3d base = pos.add(0, 0.08, 0);
		ring(base, UP, radius, 14).color(color).alpha(0.95f);
		ClientScheduler.later(3, () -> ring(base.add(0, 0.05, 0), UP, radius * 0.7f, 12).color(0xFFFFFF).alpha(0.5f));
		int dustColor = groundColor(pos);
		BlockState ground = groundState(pos);
		ClientScheduler.schedule(age -> {
			float t = (age + 1) / 12f;
			float r = radius * (1 - (1 - t) * (1 - t) * (1 - t));
			for (int i = 0, n = count(10 + radius); i < n; i++) {
				double a = RANDOM.nextDouble() * Math.PI * 2;
				Vec3d p = base.add(Math.cos(a) * r, 0, Math.sin(a) * r);
				dust(p, new Vec3d(Math.cos(a) * 0.05, rand(0.03, 0.09), Math.sin(a) * 0.05)).color(dustColor)
					.size((float) rand(0.6, 1.0), (float) rand(1.4, 2.2)).alpha(0.75f, 0f).life((int) rand(18, 30));
				if (i % 2 == 0) {
					vanilla(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), p, new Vec3d(0, rand(0.1, 0.3), 0));
				}
			}
			return age >= 11;
		});
		ScreenEffects.shakeAt(pos, Math.min(4f, radius / 4f), radius * 3.5, 14);
	}

	public static void energyBeam(Vec3d from, Vec3d to, int color, float width, int life) {
		beam(from, to, width, life).color(color).alpha(0.7f);
		beam(from, to, width * 0.35f, life).color(lighten(color, 0.7f)).alpha(0.95f);
		Vec3d axis = to.subtract(from);
		if (axis.lengthSquared() > 1.0E-4) {
			// Shock rings racing along the beam (they read well even when you look straight down it).
			Vec3d dir = axis.normalize();
			double speed = Math.max(1.5, axis.length() / 8);
			for (int k = 0; k < 2; k++) {
				ring(from, dir, width * (1.2f + k * 0.6f), 8).color(lighten(color, 0.3f)).fixed(0.2f, 0f).alpha(0.75f)
					.velocity(dir.x * speed * (1 - k * 0.35), dir.y * speed * (1 - k * 0.35), dir.z * speed * (1 - k * 0.35));
			}
		}
		glow(from, Vec3d.ZERO).color(color).size(width * 2.5f, 0).life(life);
		glow(to, Vec3d.ZERO).color(color).size(width * 3.5f, 0).life(life + 2);
		double len = to.distanceTo(from);
		for (int i = 0, n = count(len * 1.2); i < n; i++) {
			Vec3d p = from.lerp(to, RANDOM.nextDouble());
			glow(p, randomUnit().multiply(0.04)).color(color).size(width * 0.6f, 0).life((int) rand(8, 16));
		}
	}

	/** Builds a jagged path between two points (shared by lightning and branches). */
	private static List<Vec3d> jagged(Vec3d from, Vec3d to, double segmentLength, double jitter) {
		List<Vec3d> points = new ArrayList<>();
		Vec3d delta = to.subtract(from);
		double len = delta.length();
		int n = Math.max(3, (int) (len / segmentLength));
		Vec3d side = perpendicular(delta);
		Vec3d side2 = delta.normalize().crossProduct(side);
		points.add(from);
		for (int i = 1; i < n; i++) {
			double t = i / (double) n;
			double env = Math.sin(t * Math.PI);
			Vec3d off = side.multiply(rand(-1, 1) * jitter * env).add(side2.multiply(rand(-1, 1) * jitter * env));
			points.add(from.add(delta.multiply(t)).add(off));
		}
		points.add(to);
		return points;
	}

	public static void lightning(Vec3d from, Vec3d to, int color, float intensity) {
		double len = to.distanceTo(from);
		List<Vec3d> path = jagged(from, to, 1.4, Math.min(1.4, 0.15 + len * 0.05));
		int life = 7;
		for (int i = 0; i < path.size() - 1; i++) {
			Vec3d a = path.get(i);
			Vec3d b = path.get(i + 1);
			beam(a, b, 0.55f * intensity, life).color(color).alpha(0.65f).flicker();
			beam(a, b, 0.16f * intensity, life).color(0xFFFFFF).alpha(1f).flicker();
			glow(a, Vec3d.ZERO).color(color).size(0.5f * intensity, 0).life(life);
		}
		int branches = 2 + (int) (len / 7);
		for (int b = 0; b < branches; b++) {
			Vec3d start = path.get(1 + RANDOM.nextInt(Math.max(1, path.size() - 2)));
			Vec3d dir = to.subtract(from).normalize().add(randomUnit().multiply(1.1)).normalize();
			Vec3d end = start.add(dir.multiply(rand(2, 5) * intensity));
			List<Vec3d> branch = jagged(start, end, 1.0, 0.5);
			for (int i = 0; i < branch.size() - 1; i++) {
				beam(branch.get(i), branch.get(i + 1), 0.25f * intensity, life - 2).color(color).alpha(0.55f).flicker();
				beam(branch.get(i), branch.get(i + 1), 0.07f * intensity, life - 2).color(0xFFFFFF).alpha(0.9f).flicker();
			}
		}
		glow(to, Vec3d.ZERO).color(0xFFFFFF).size(2.5f * intensity, 0).life(8);
		glow(to, Vec3d.ZERO).color(color).size(4f * intensity, 0).life(12);
		for (int i = 0, n = count(30 * intensity); i < n; i++) {
			spark(to, randomUnit().multiply(rand(0.2, 0.7))).color(lighten(color, 0.6f)).size(0.2f, 0).life((int) rand(6, 14)).drag(0.8f);
		}
		ring(to.add(0, 0.1, 0), UP, 3.5f * intensity, 10).color(color);
		ScreenEffects.flashAt(to, 0xD8ECFF, 0.55f, 64, 7);
		ScreenEffects.shakeAt(to, 1.6f * intensity, 48, 12);
	}

	public static void explosion(Vec3d pos, int color, float s, Element element) {
		glow(pos, Vec3d.ZERO).color(0xFFFFFF).size(2.2f * s, 0).life(6);
		glow(pos, Vec3d.ZERO).color(color).size(3.4f * s, 0).life(11);
		ring(pos, UP, 4.5f * s, 12).color(color);
		ring(pos, randomUnit().add(0, 2, 0), 3.2f * s, 10).color(lighten(color, 0.4f)).alpha(0.6f);
		if (element == null) {
			burst(pos, color, s);
		} else {
			switch (element) {
				case FIRE -> fireBlast(pos, s, color);
				case WATER -> splash(pos, s);
				case EARTH -> debris(pos, s, groundState(pos));
				case AIR -> windBurst(pos, UP, s);
				case AVATAR -> {
					fireBlast(pos, s * 0.6f, Colors.FIRE);
					splash(pos, s * 0.6f);
					debris(pos, s * 0.6f, groundState(pos));
					windBurst(pos, UP, s * 0.6f);
					burst(pos, Colors.AVATAR, s * 0.8f);
				}
			}
		}
		ScreenEffects.shakeAt(pos, 1.3f * s, 24 * s, 16);
		if (s >= 1.5f) {
			ScreenEffects.flashAt(pos, lighten(color, 0.5f), 0.35f, 10 * s, 8);
		}
	}

	private static void fireBlast(Vec3d pos, float s, int color) {
		boolean blue = color == Colors.BLUE_FIRE;
		int hot = blue ? 0xC8F0FF : 0xFFE7A0;
		int cool = blue ? 0x2050FF : 0xFF3C00;
		for (int i = 0, n = count(28 * s); i < n; i++) {
			Vec3d dir = randomUnit().add(0, 0.35, 0).normalize();
			flame(pos, dir.multiply(rand(0.1, 0.35) * s)).color(hot, cool).size((float) rand(0.5, 0.9) * s, 0.2f * s)
				.life((int) rand(12, 22)).drag(0.85f);
		}
		for (int i = 0, n = count(24 * s); i < n; i++) {
			ember(pos, randomUnit().multiply(rand(0.15, 0.5) * s).add(0, 0.1, 0)).color(hot, cool).life((int) rand(20, 40))
				.gravity(-0.03f).drag(0.92f).size(0.13f, 0.03f);
		}
		for (int i = 0, n = count(10 * s); i < n; i++) {
			dust(pos.add(randomUnit().multiply(0.5 * s)), new Vec3d(0, rand(0.03, 0.08), 0)).color(0x2A2420).size(1.0f * s, 2.4f * s)
				.alpha(0.55f, 0f).life((int) rand(30, 50));
		}
	}

	public static void charge(Vec3d pos, int color, float radius, int duration) {
		ClientScheduler.schedule(age -> {
			float progress = age / (float) duration;
			for (int i = 0, n = count(3 + 5 * progress * radius); i < n; i++) {
				Vec3d dir = randomUnit();
				Vec3d start = pos.add(dir.multiply(radius * rand(0.7, 1.0)));
				int life = 10;
				Vec3d vel = pos.subtract(start).multiply(1.0 / life);
				glow(start, vel).color(lighten(color, 0.2f), color).size(0.15f, 0.45f).life(life).drag(1f);
			}
			if (age % 2 == 0) {
				glow(pos, Vec3d.ZERO).color(color).size(0.4f + 1.4f * progress, 0.2f).life(4);
			}
			if (RANDOM.nextFloat() < 0.4f) {
				spark(pos.add(randomUnit().multiply(0.3)), randomUnit().multiply(0.05)).color(lighten(color, 0.6f)).size(0.2f, 0).life(5);
			}
			if (age >= duration) {
				burst(pos, color, 1.2f);
				return true;
			}
			return false;
		});
	}

	public static void pillar(Vec3d base, int color, float height, int life) {
		Vec3d top = base.add(0, height, 0);
		beam(base, top, 2.2f, life).color(color).alpha(0.5f);
		beam(base, top, 0.7f, life).color(lighten(color, 0.8f)).alpha(0.95f);
		ring(base.add(0, 0.1, 0), UP, 5f, 20).color(color);
		glow(base, Vec3d.ZERO).color(color).size(4f, 0).life(14);
		ClientScheduler.schedule(age -> {
			if (age % 3 == 0) {
				ring(base.add(0, 0.3, 0), UP, 1.6f, 22).color(lighten(color, 0.3f)).fixed(0.1f, 0.1f).alpha(0.7f).velocity(0, height / 22.0, 0);
			}
			for (int i = 0, n = count(5); i < n; i++) {
				double a = RANDOM.nextDouble() * Math.PI * 2;
				double r = rand(0.2, 1.3);
				glow(base.add(Math.cos(a) * r, rand(0, 2), Math.sin(a) * r), new Vec3d(0, rand(0.3, 0.8), 0)).color(color)
					.size(0.3f, 0).life((int) rand(15, 30)).drag(0.98f);
			}
			return age >= life;
		});
		ScreenEffects.flashAt(base, lighten(color, 0.6f), 0.4f, 40, 12);
	}

	public static void spiral(Vec3d base, int color, float radius, float height) {
		ClientScheduler.schedule(age -> {
			float t = age / 30f;
			for (int k = 0; k < 3; k++) {
				double a = age * 0.42 + k * Math.PI * 2 / 3;
				Vec3d p = base.add(Math.cos(a) * radius, t * height, Math.sin(a) * radius);
				glow(p, new Vec3d(0, 0.02, 0)).color(lighten(color, 0.3f), color).size(0.35f, 0).life(16);
				if (RANDOM.nextFloat() < 0.3f) {
					spark(p, new Vec3d(0, 0.05, 0)).color(lighten(color, 0.7f)).size(0.15f, 0).life(10);
				}
			}
			return age >= 30;
		});
	}

	public static void windBurst(Vec3d pos, Vec3d dir, float s) {
		Vec3d axis = dir.lengthSquared() < 1.0E-4 ? UP : dir.normalize();
		ring(pos, axis, 2.6f * s, 10).color(0xFFFFFF).alpha(0.6f);
		Vec3d side = perpendicular(axis);
		Vec3d side2 = axis.crossProduct(side);
		for (int i = 0, n = count(26 * s); i < n; i++) {
			double a = RANDOM.nextDouble() * Math.PI * 2;
			Vec3d radial = side.multiply(Math.cos(a)).add(side2.multiply(Math.sin(a)));
			Vec3d vel = radial.multiply(rand(0.15, 0.4) * s).add(axis.multiply(rand(0.0, 0.25) * s));
			wind(pos, vel).color(0xFFFFFF).size(0.5f * s, 1.7f * s).alpha(0.75f, 0f).life((int) rand(10, 18)).drag(0.88f);
		}
		for (int i = 0, n = count(8 * s); i < n; i++) {
			dust(pos.add(randomUnit().multiply(0.4 * s)), randomUnit().multiply(0.08 * s)).color(0xF4F8FF).size(0.8f * s, 2f * s)
				.alpha(0.5f, 0f).life((int) rand(16, 26));
		}
		glow(pos, Vec3d.ZERO).color(Colors.AIR).size(1.6f * s, 0).life(6).alpha(0.6f, 0f);
	}

	public static void splash(Vec3d pos, float s) {
		ring(pos, UP, 2.8f * s, 12).color(Colors.WATER);
		glow(pos, Vec3d.ZERO).color(Colors.WATER).size(2.4f * s, 0).life(8);
		for (int i = 0, n = count(40 * s); i < n; i++) {
			Vec3d dir = randomUnit().add(0, 0.6, 0).normalize();
			droplet(pos, dir.multiply(rand(0.12, 0.45) * s)).color(0xCFEFFF, Colors.WATER).size((float) rand(0.15, 0.3), 0.1f)
				.life((int) rand(18, 30));
		}
		for (int i = 0, n = count(16 * s); i < n; i++) {
			vanilla(ParticleTypes.SPLASH, pos.add(randomUnit().multiply(0.6 * s)), Vec3d.ZERO);
		}
		for (int i = 0, n = count(8 * s); i < n; i++) {
			dust(pos.add(randomUnit().multiply(0.5 * s)), randomUnit().multiply(0.04)).color(0xD8F0FF).size(0.8f * s, 2.0f * s)
				.alpha(0.45f, 0f).life((int) rand(16, 28));
		}
		for (int i = 0, n = count(10 * s); i < n; i++) {
			glow(pos, randomUnit().multiply(rand(0.05, 0.25) * s)).color(Colors.WATER).size(0.35f, 0).life((int) rand(10, 18));
		}
	}

	public static void frost(Vec3d pos, float s) {
		ring(pos, UP, 2.6f * s, 12).color(Colors.ICE);
		glow(pos, Vec3d.ZERO).color(Colors.ICE).size(2.2f * s, 0).life(9);
		for (int i = 0, n = count(26 * s); i < n; i++) {
			Vec3d dir = randomUnit().add(0, 0.4, 0).normalize();
			shard(pos, dir.multiply(rand(0.15, 0.45) * s)).color(0xE8FCFF).size((float) rand(0.15, 0.3), 0.1f).life((int) rand(20, 35));
		}
		for (int i = 0, n = count(14 * s); i < n; i++) {
			spark(pos, randomUnit().multiply(rand(0.1, 0.35) * s)).color(Colors.ICE).size(0.2f, 0).life((int) rand(8, 14));
		}
		for (int i = 0, n = count(20 * s); i < n; i++) {
			vanilla(ParticleTypes.SNOWFLAKE, pos.add(randomUnit().multiply(0.8 * s)), randomUnit().multiply(0.05));
		}
	}

	public static void debris(Vec3d pos, float s, BlockState state) {
		BlockState block = state.isAir() ? Blocks.STONE.getDefaultState() : state;
		int color = block.getMapColor(world(), BlockPos.ofFloored(pos)).color;
		if (color == 0) {
			color = Colors.EARTH;
		}
		BlockStateParticleEffect crumbs = new BlockStateParticleEffect(ParticleTypes.BLOCK, block);
		for (int i = 0, n = count(36 * s); i < n; i++) {
			Vec3d dir = randomUnit().add(0, 0.7, 0).normalize();
			vanilla(crumbs, pos.add(randomUnit().multiply(0.4 * s)), dir.multiply(rand(0.2, 0.6) * s));
		}
		for (int i = 0, n = count(18 * s); i < n; i++) {
			Vec3d dir = randomUnit().add(0, 0.8, 0).normalize();
			shard(pos, dir.multiply(rand(0.2, 0.5) * s)).color(color).size((float) rand(0.18, 0.35), 0.15f).life((int) rand(25, 40));
		}
		for (int i = 0, n = count(12 * s); i < n; i++) {
			dust(pos.add(randomUnit().multiply(0.6 * s)), new Vec3d(rand(-0.05, 0.05), rand(0.02, 0.08), rand(-0.05, 0.05)))
				.color(lighten(color, 0.2f)).size(0.9f * s, 2.4f * s).alpha(0.7f, 0f).life((int) rand(26, 44));
		}
	}

	public static void flames(Vec3d pos, int color, float s) {
		fireBlast(pos, s, color);
		glow(pos, Vec3d.ZERO).color(color).size(1.8f * s, 0).life(7);
	}

	public static void sigil(Vec3d pos, int color, float radius, int duration) {
		Vec3d base = pos.add(0, 0.06, 0);
		sigilRing(base, radius, duration).color(color).fixed(0.05f, 0.15f).alpha(0.65f);
		sigilRing(base.add(0, 0.02, 0), radius * 0.62f, duration).color(lighten(color, 0.4f)).fixed(-0.08f, 0.2f).alpha(0.5f);
		ClientScheduler.schedule(age -> {
			for (int i = 0, n = count(3); i < n; i++) {
				double a = RANDOM.nextDouble() * Math.PI * 2;
				Vec3d p = base.add(Math.cos(a) * radius * 0.95, 0, Math.sin(a) * radius * 0.95);
				glow(p, new Vec3d(0, rand(0.04, 0.12), 0)).color(color).size(0.25f, 0).life((int) rand(12, 24)).drag(0.98f);
			}
			return age >= duration;
		});
	}

	public static void line(Vec3d from, Vec3d to, int color, float density) {
		double len = to.distanceTo(from);
		for (int i = 0, n = count(len * Math.max(0.5, density) * 2); i < n; i++) {
			Vec3d p = from.lerp(to, RANDOM.nextDouble()).add(randomUnit().multiply(0.05));
			glow(p, randomUnit().multiply(0.01)).color(color).size(0.22f, 0).life((int) rand(8, 14));
		}
	}

	public static void slash(Vec3d center, Vec3d forward, int color, float radius) {
		Vec3d f = forward.lengthSquared() < 1.0E-4 ? new Vec3d(0, 0, 1) : forward.normalize();
		Vec3d right = f.crossProduct(UP);
		if (right.lengthSquared() < 1.0E-4) {
			right = new Vec3d(1, 0, 0);
		}
		right = right.normalize();
		boolean fire = color == Colors.FIRE || color == Colors.BLUE_FIRE;
		for (int i = 0, n = count(28); i < n; i++) {
			double a = Math.toRadians(-70 + 140 * (i / (double) Math.max(1, n - 1)));
			Vec3d dir = f.multiply(Math.cos(a)).add(right.multiply(Math.sin(a)));
			Vec3d p = center.add(dir.multiply(radius));
			glow(p, dir.multiply(0.15)).color(lighten(color, 0.3f), color).size(0.45f, 0).life((int) rand(7, 11));
			if (fire) {
				flame(p, dir.multiply(0.12).add(0, 0.03, 0)).color(color == Colors.BLUE_FIRE ? 0xC8F0FF : 0xFFE7A0, color)
					.size(0.5f, 0.15f).life((int) rand(10, 16));
			} else {
				wind(p, dir.multiply(0.2)).color(0xFFFFFF).size(0.5f, 1.2f).alpha(0.6f, 0f).life(10);
			}
		}
		vanilla(ParticleTypes.SWEEP_ATTACK, center.add(f.multiply(radius * 0.7)), Vec3d.ZERO);
	}

	public static void cone(Vec3d origin, Vec3d vec, int color, float spread, Element element) {
		double len = vec.length();
		if (len < 1.0E-3) {
			return;
		}
		Vec3d dir = vec.multiply(1 / len);
		for (int i = 0, n = count(30); i < n; i++) {
			Vec3d d = randomInCone(dir, spread);
			Vec3d vel = d.multiply(len / 12 * rand(0.7, 1.2));
			if (element == Element.FIRE) {
				flame(origin, vel).color(color == Colors.BLUE_FIRE ? 0xC8F0FF : 0xFFE7A0, color).size(0.3f, 1.2f).life(12).drag(0.92f);
			} else if (element == Element.WATER) {
				droplet(origin, vel).color(0xCFEFFF, Colors.WATER).size(0.2f, 0.1f).life(16);
			} else if (element == Element.EARTH) {
				dust(origin, vel.multiply(0.6)).color(Colors.EARTH).size(0.5f, 1.4f).alpha(0.7f, 0f).life(16);
			} else {
				wind(origin, vel).color(0xFFFFFF).size(0.4f, 1.4f).alpha(0.65f, 0f).life(12);
			}
		}
	}

	public static void fireRing(Vec3d pos, int color, float radius, int ticks) {
		boolean blue = color == Colors.BLUE_FIRE;
		int hot = blue ? 0xC8F0FF : 0xFFE7A0;
		Vec3d base = pos.add(0, 0.1, 0);
		ring(base, UP, radius, ticks + 4).color(color).alpha(0.9f);
		ClientScheduler.schedule(age -> {
			float t = (age + 1) / (float) ticks;
			float r = radius * Math.min(1f, 0.1f + 0.9f * t);
			for (int i = 0, n = count(8 + r * 5); i < n; i++) {
				double a = RANDOM.nextDouble() * Math.PI * 2;
				Vec3d out = new Vec3d(Math.cos(a), 0, Math.sin(a));
				Vec3d p = base.add(out.multiply(r));
				flame(p, out.multiply(0.08).add(0, rand(0.08, 0.2), 0)).color(hot, color).size((float) rand(0.5, 0.9), 0.15f)
					.life((int) rand(10, 18)).drag(0.9f);
				if (i % 3 == 0) {
					ember(p, out.multiply(0.1).add(0, rand(0.1, 0.25), 0)).color(hot, color).life((int) rand(16, 28)).gravity(-0.02f);
				}
				if (i % 4 == 0) {
					dust(p.add(0, 0.6, 0), new Vec3d(0, rand(0.03, 0.07), 0)).color(0x2A2420).size(0.8f, 2.0f).alpha(0.45f, 0f)
						.life((int) rand(24, 40));
				}
			}
			return age + 1 >= ticks;
		});
		glow(base, Vec3d.ZERO).color(color).size(radius * 0.6f, 0).life(8).alpha(0.6f, 0f);
	}

	public static float lerpAngle(float t, float a, float b) {
		return MathHelper.lerpAngleDegrees(t, a, b);
	}
}
