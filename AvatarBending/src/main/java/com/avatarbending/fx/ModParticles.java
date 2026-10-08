package com.avatarbending.fx;

import com.avatarbending.AvatarBending;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * Custom particle types. Their look (color, size, motion) is configured on the client by
 * {@code ClientFx}; the server mostly triggers whole effects through {@link Fx}.
 */
public final class ModParticles {
	public static final SimpleParticleType GLOW = register("glow");
	public static final SimpleParticleType SPARK = register("spark");
	public static final SimpleParticleType WIND = register("wind");
	public static final SimpleParticleType RING = register("ring");
	public static final SimpleParticleType SIGIL = register("sigil");
	public static final SimpleParticleType BEAM = register("beam");
	public static final SimpleParticleType EMBER = register("ember");
	public static final SimpleParticleType FLAME = register("flame");
	public static final SimpleParticleType DROPLET = register("droplet");
	public static final SimpleParticleType DUST = register("dust");
	public static final SimpleParticleType SHARD = register("shard");

	private ModParticles() {
	}

	private static SimpleParticleType register(String name) {
		return Registry.register(Registries.PARTICLE_TYPE, AvatarBending.id(name), FabricParticleTypes.simple(true));
	}

	public static void register() {
		// Static initializer does the work.
	}
}
