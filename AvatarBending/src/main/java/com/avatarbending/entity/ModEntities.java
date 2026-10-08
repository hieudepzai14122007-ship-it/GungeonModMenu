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

	public static final EntityType<AirBladeEntity> AIR_BLADE = register("air_blade",
		EntityType.Builder.<AirBladeEntity>create(AirBladeEntity::new, SpawnGroup.MISC).dimensions(2.2f, 0.6f));
	public static final EntityType<WaterOrbEntity> WATER_ORB = register("water_orb",
		EntityType.Builder.<WaterOrbEntity>create(WaterOrbEntity::new, SpawnGroup.MISC).dimensions(0.8f, 0.8f));
	public static final EntityType<RockEntity> ROCK = register("rock",
		EntityType.Builder.<RockEntity>create(RockEntity::new, SpawnGroup.MISC).dimensions(0.45f, 0.45f));
	public static final EntityType<LavaBombEntity> LAVA_BOMB = register("lava_bomb",
		EntityType.Builder.<LavaBombEntity>create(LavaBombEntity::new, SpawnGroup.MISC).dimensions(0.55f, 0.55f).makeFireImmune());
	public static final EntityType<FireDragonEntity> FIRE_DRAGON = register("fire_dragon",
		EntityType.Builder.<FireDragonEntity>create(FireDragonEntity::new, SpawnGroup.MISC).dimensions(0.9f, 0.9f).makeFireImmune());
	public static final EntityType<MaelstromEntity> MAELSTROM = register("maelstrom",
		EntityType.Builder.<MaelstromEntity>create(MaelstromEntity::new, SpawnGroup.MISC).dimensions(1.0f, 1.0f));
	public static final EntityType<BlizzardEntity> BLIZZARD = register("blizzard",
		EntityType.Builder.<BlizzardEntity>create(BlizzardEntity::new, SpawnGroup.MISC).dimensions(1.0f, 1.0f));
	public static final EntityType<VolcanoEntity> VOLCANO = register("volcano",
		EntityType.Builder.<VolcanoEntity>create(VolcanoEntity::new, SpawnGroup.MISC).dimensions(1.0f, 1.0f).makeFireImmune());

	private ModEntities() {
	}

	private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		EntityType<T> type = builder
			.maxTrackingRange(12)
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
