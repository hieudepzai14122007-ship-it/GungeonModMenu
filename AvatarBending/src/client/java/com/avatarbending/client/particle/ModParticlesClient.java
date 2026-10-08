package com.avatarbending.client.particle;

import com.avatarbending.fx.ModParticles;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.particle.SimpleParticleType;

/**
 * Registers particle factories and keeps each particle type's sprites so client effects can build
 * fully configured particles directly.
 */
public final class ModParticlesClient {
	public static SpriteProvider glow;
	public static SpriteProvider spark;
	public static SpriteProvider wind;
	public static SpriteProvider ring;
	public static SpriteProvider sigil;
	public static SpriteProvider beam;
	public static SpriteProvider ember;
	public static SpriteProvider flame;
	public static SpriteProvider droplet;
	public static SpriteProvider dust;
	public static SpriteProvider shard;

	private ModParticlesClient() {
	}

	public static void register() {
		ParticleFactoryRegistry registry = ParticleFactoryRegistry.getInstance();
		registry.register(ModParticles.GLOW, provider -> {
			glow = provider;
			return simple(provider, 0xFFFFFF, 0.4f, 12);
		});
		registry.register(ModParticles.SPARK, provider -> {
			spark = provider;
			return simple(provider, 0xFFFFFF, 0.2f, 8);
		});
		registry.register(ModParticles.WIND, provider -> {
			wind = provider;
			return (type, world, x, y, z, vx, vy, vz) -> new FxParticle(world, x, y, z, vx, vy, vz, provider)
				.size(0.5f, 1.2f).alpha(0.6f, 0f).life(14).spin(0.2f).solid();
		});
		registry.register(ModParticles.RING, provider -> {
			ring = provider;
			return simple(provider, 0xFFFFFF, 0.5f, 10);
		});
		registry.register(ModParticles.SIGIL, provider -> {
			sigil = provider;
			return simple(provider, 0xFFFFFF, 0.5f, 10);
		});
		registry.register(ModParticles.BEAM, provider -> {
			beam = provider;
			return simple(provider, 0xFFFFFF, 0.3f, 6);
		});
		registry.register(ModParticles.EMBER, provider -> {
			ember = provider;
			return (type, world, x, y, z, vx, vy, vz) -> new FxParticle(world, x, y, z, vx, vy, vz, provider)
				.color(0xFFE08A, 0xFF3A00).size(0.12f, 0.02f).life(20).gravity(-0.02f);
		});
		registry.register(ModParticles.FLAME, provider -> {
			flame = provider;
			return (type, world, x, y, z, vx, vy, vz) -> new FxParticle(world, x, y, z, vx, vy, vz, provider)
				.color(0xFFD27A, 0xFF4A10).size(0.4f, 0.1f).life(14).animated();
		});
		registry.register(ModParticles.DROPLET, provider -> {
			droplet = provider;
			return (type, world, x, y, z, vx, vy, vz) -> new FxParticle(world, x, y, z, vx, vy, vz, provider)
				.color(0x9FD8FF).size(0.18f, 0.12f).life(20).gravity(1f).collide().solid();
		});
		registry.register(ModParticles.DUST, provider -> {
			dust = provider;
			return (type, world, x, y, z, vx, vy, vz) -> new FxParticle(world, x, y, z, vx, vy, vz, provider)
				.color(0xC8B8A0).size(0.6f, 1.4f).alpha(0.6f, 0f).life(24).lit().solid();
		});
		registry.register(ModParticles.SHARD, provider -> {
			shard = provider;
			return (type, world, x, y, z, vx, vy, vz) -> new FxParticle(world, x, y, z, vx, vy, vz, provider)
				.color(0xD0D0D0).size(0.2f, 0.15f).life(30).gravity(1f).collide().lit().solid();
		});
	}

	private static net.minecraft.client.particle.ParticleFactory<SimpleParticleType> simple(SpriteProvider provider, int color, float size, int life) {
		return (type, world, x, y, z, vx, vy, vz) -> new FxParticle(world, x, y, z, vx, vy, vz, provider)
			.color(color).size(size, 0f).life(life);
	}
}
