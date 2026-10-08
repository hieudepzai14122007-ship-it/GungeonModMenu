package com.avatarbending.entity;

import com.avatarbending.AvatarBending;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModEntities {
	public static final EntityType<AirBlastEntity> AIR_BLAST = register("air_blast",
		EntityType.Builder.<AirBlastEntity>create(AirBlastEntity::new, SpawnGroup.MISC).dimensions(0.7f, 0.7f));
	public static final EntityType<FireBlastEntity> FIRE_BLAST = register("fire_blast",
		EntityType.Builder.<FireBlastEntity>create(FireBlastEntity::new, SpawnGroup.MISC).dimensions(0.5f, 0.5f).makeFireImmune());
	public static final EntityType<WaterBlastEntity> WATER_BLAST = register("water_blast",
		EntityType.Builder.<WaterBlastEntity>create(WaterBlastEntity::new, SpawnGroup.MISC).dimensions(0.5f, 0.5f));
	public static final EntityType<BoulderEntity> BOULDER = register("boulder",
		EntityType.Builder.<BoulderEntity>create(BoulderEntity::new, SpawnGroup.MISC).dimensions(1.0f, 1.0f));
	public static final EntityType<IceShardEntity> ICE_SHARD = register("ice_shard",
		EntityType.Builder.<IceShardEntity>create(IceShardEntity::new, SpawnGroup.MISC).dimensions(0.3f, 0.3f));
	public static final EntityType<MeteorEntity> METEOR = register("meteor",
		EntityType.Builder.<MeteorEntity>create(MeteorEntity::new, SpawnGroup.MISC).dimensions(2.5f, 2.5f).makeFireImmune());
	public static final EntityType<TornadoEntity> TORNADO = register("tornado",
		EntityType.Builder.<TornadoEntity>create(TornadoEntity::new, SpawnGroup.MISC).dimensions(1.0f, 2.0f));
	public static final EntityType<TidalWaveEntity> TIDAL_WAVE = register("tidal_wave",
		EntityType.Builder.<TidalWaveEntity>create(TidalWaveEntity::new, SpawnGroup.MISC).dimensions(1.0f, 1.0f));

	private ModEntities() {
	}

	private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		EntityType<T> type = builder
			.maxTrackingRange(10)
			.trackingTickInterval(1)
			.disableSaving()
			.disableSummon()
			.build(AvatarBending.MOD_ID + ":" + name);
		return Registry.register(Registries.ENTITY_TYPE, AvatarBending.id(name), type);
	}

	public static void register() {
		// Static initializer does the work.
	}
}
