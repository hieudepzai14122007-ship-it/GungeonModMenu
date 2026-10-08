package com.avatarbending.ability;

import com.avatarbending.AvatarBending;
import com.avatarbending.bending.AvatarState;
import com.avatarbending.bending.Element;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Everything an ability needs while casting, plus shared helpers for targeting, damage and particles.
 */
public final class AbilityContext {
	private final ServerPlayerEntity player;
	private final ServerWorld world;
	private final boolean avatarState;
	private final float power;
	private boolean failed;

	public AbilityContext(ServerPlayerEntity player, ServerWorld world, boolean avatarState) {
		this.player = player;
		this.world = world;
		this.avatarState = avatarState;
		this.power = avatarState ? AvatarState.POWER : 1f;
	}

	public ServerPlayerEntity player() {
		return player;
	}

	public ServerWorld world() {
		return world;
	}

	/** Damage / size multiplier: 1.0 normally, higher in the Avatar State. */
	public float power() {
		return power;
	}

	public boolean avatarState() {
		return avatarState;
	}

	/** Marks the cast as failed so no chi or cooldown is spent. */
	public void fail() {
		failed = true;
	}

	public boolean failed() {
		return failed;
	}

	// ---------------------------------------------------------------- geometry

	public Vec3d eyes() {
		return player.getEyePos();
	}

	public Vec3d look() {
		return player.getRotationVec(1f);
	}

	/** Horizontal look direction (never zero). */
	public Vec3d flatLook() {
		Vec3d look = look();
		Vec3d flat = new Vec3d(look.x, 0, look.z);
		if (flat.lengthSquared() < 1.0E-4) {
			float yaw = player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
			flat = new Vec3d(-MathHelper.sin(yaw), 0, MathHelper.cos(yaw));
		}
		return flat.normalize();
	}

	/** Rotates a direction around the vertical axis. */
	public static Vec3d rotateY(Vec3d dir, double degrees) {
		double rad = Math.toRadians(degrees);
		double cos = Math.cos(rad);
		double sin = Math.sin(rad);
		return new Vec3d(dir.x * cos - dir.z * sin, dir.y, dir.x * sin + dir.z * cos);
	}

	/** Block the player is looking at, or the point at max range. */
	public Vec3d aimPoint(double range) {
		HitResult hit = raycast(range);
		return hit.getPos();
	}

	/** Raycast against blocks and living entities. */
	public HitResult raycast(double range) {
		Vec3d start = eyes();
		Vec3d end = start.add(look().multiply(range));
		BlockHitResult blockHit = world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER,
			RaycastContext.FluidHandling.NONE, player));
		Vec3d limit = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getPos();
		Box box = player.getBoundingBox().stretch(limit.subtract(start)).expand(1.0);
		EntityHitResult entityHit = ProjectileUtil.raycast(player, start, limit, box,
			e -> e instanceof LivingEntity && isTarget(e), start.squaredDistanceTo(limit));
		if (entityHit != null) {
			return entityHit;
		}
		return blockHit;
	}

	@Nullable
	public LivingEntity targetEntity(double range) {
		HitResult hit = raycast(range);
		if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity living) {
			return living;
		}
		return null;
	}

	/**
	 * Finds the ground surface at or near {@code y}: the first open position (air, grass...) above a
	 * block you could stand on (stone, dirt, leaves...). Searches from {@code y + searchUp} down.
	 */
	@Nullable
	public BlockPos groundAt(double x, double y, double z, int searchUp, int searchDown) {
		BlockPos start = BlockPos.ofFloored(x, y, z);
		for (int dy = searchUp; dy >= -searchDown; dy--) {
			BlockPos pos = start.up(dy);
			if (isOpen(world.getBlockState(pos)) && isGround(pos.down())) {
				return pos;
			}
		}
		return null;
	}

	public boolean isGround(BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		return state.getFluidState().isEmpty() && state.isSideSolidFullSquare(world, pos, Direction.UP);
	}

	/** Finds the top of a water surface near {@code y} (for freezing water), or null. */
	@Nullable
	public BlockPos waterSurfaceAt(double x, double y, double z, int searchUp, int searchDown) {
		BlockPos start = BlockPos.ofFloored(x, y, z);
		for (int dy = searchUp; dy >= -searchDown; dy--) {
			BlockPos pos = start.up(dy);
			if (world.getFluidState(pos).isStill() && world.getBlockState(pos.up()).isAir()) {
				return pos;
			}
		}
		return null;
	}

	/** Places a temporary block, unless a creature is standing in that spot. */
	public boolean placeTemp(BlockPos pos, BlockState state, int duration) {
		if (!world.getEntitiesByClass(LivingEntity.class, new Box(pos), Entity::isAlive).isEmpty()) {
			return false;
		}
		return com.avatarbending.effect.TempBlocks.place(world, pos, state, duration);
	}

	public boolean isOpen(BlockState state) {
		return state.isAir() || (state.isReplaceable() && state.getFluidState().isEmpty());
	}

	/** Block the player is standing on (used as the material for earthbending). */
	public BlockState groundBlock() {
		BlockPos pos = player.getBlockPos().down();
		for (int i = 0; i < 3; i++) {
			BlockState state = world.getBlockState(pos.down(i));
			if (state.isSolidBlock(world, pos.down(i))) {
				return state;
			}
		}
		return Blocks.STONE.getDefaultState();
	}

	/**
	 * Converts a ground block into the block that earthbending raises. Ores and valuable blocks
	 * become stone so earthbending can never be used to duplicate them.
	 */
	public static BlockState earthMaterial(BlockState ground) {
		if (ground.isIn(BlockTags.SAND)) {
			return Blocks.SANDSTONE.getDefaultState();
		}
		if (ground.isIn(BlockTags.DIRT) || ground.isOf(Blocks.FARMLAND) || ground.isOf(Blocks.DIRT_PATH)) {
			return Blocks.PACKED_MUD.getDefaultState();
		}
		if (ground.isOf(Blocks.NETHERRACK) || ground.isIn(BlockTags.NYLIUM)) {
			return Blocks.NETHERRACK.getDefaultState();
		}
		if (ground.isOf(Blocks.SOUL_SAND) || ground.isOf(Blocks.SOUL_SOIL)) {
			return Blocks.SOUL_SOIL.getDefaultState();
		}
		if (ground.isOf(Blocks.END_STONE)) {
			return Blocks.END_STONE.getDefaultState();
		}
		if (ground.isOf(Blocks.BASALT) || ground.isOf(Blocks.BLACKSTONE)) {
			return Blocks.BLACKSTONE.getDefaultState();
		}
		if (ground.isOf(Blocks.DEEPSLATE) || ground.isOf(Blocks.COBBLED_DEEPSLATE) || ground.isOf(Blocks.TUFF)) {
			return Blocks.COBBLED_DEEPSLATE.getDefaultState();
		}
		if (ground.isIn(BlockTags.SNOW) || ground.isOf(Blocks.ICE) || ground.isOf(Blocks.PACKED_ICE)) {
			return Blocks.PACKED_ICE.getDefaultState();
		}
		if (ground.isIn(BlockTags.TERRACOTTA)) {
			return Blocks.TERRACOTTA.getDefaultState();
		}
		return Blocks.COBBLESTONE.getDefaultState();
	}

	// ---------------------------------------------------------------- targeting & damage

	/** Whether this entity may be hit by the caster's bending (not the caster, their pets or armor stands). */
	public boolean isTarget(Entity entity) {
		if (entity == player || !entity.isAlive() || entity.isSpectator()) {
			return false;
		}
		if (entity instanceof ArmorStandEntity) {
			return false;
		}
		if (entity instanceof TameableEntity tameable && tameable.isOwner(player)) {
			return false;
		}
		if (entity instanceof PlayerEntity other && (other.isCreative() || !player.shouldDamagePlayer(other))) {
			return false;
		}
		return entity instanceof LivingEntity;
	}

	public List<LivingEntity> targetsAround(Vec3d center, double radius) {
		Box box = new Box(center, center).expand(radius);
		return world.getEntitiesByClass(LivingEntity.class, box,
			e -> isTarget(e) && e.squaredDistanceTo(center) <= radius * radius);
	}

	public List<LivingEntity> targetsIn(Box box) {
		return world.getEntitiesByClass(LivingEntity.class, box, this::isTarget);
	}

	public DamageSource damageSource(Element element) {
		return damageSource(world, element, null, player);
	}

	public static DamageSource damageSource(ServerWorld world, Element element, @Nullable Entity direct, @Nullable Entity attacker) {
		RegistryKey<DamageType> key = AvatarBending.damageType(element);
		return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(key), direct, attacker);
	}

	/** Deals {@code amount} (scaled by power) of bending damage, ignoring the usual hit cooldown. */
	public boolean damage(LivingEntity target, Element element, float amount) {
		target.timeUntilRegen = 0;
		return target.damage(damageSource(element), amount * power);
	}

	public void knockback(Entity target, Vec3d direction, double strength, double lift) {
		Vec3d dir = direction.lengthSquared() < 1.0E-4 ? flatLook() : direction.normalize();
		double resist = target instanceof LivingEntity living
			? 1.0 - living.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE) * 0.6
			: 1.0;
		Vec3d push = new Vec3d(dir.x * strength, lift, dir.z * strength).multiply(power * resist);
		target.setVelocity(target.getVelocity().multiply(0.3).add(push));
		target.velocityModified = true;
		target.fallDistance = 0;
	}

	// ---------------------------------------------------------------- particles & sound

	public void sound(SoundEvent sound, float volume, float pitch) {
		world.playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundCategory.PLAYERS, volume, pitch);
	}

	public void soundAt(Vec3d pos, SoundEvent sound, float volume, float pitch) {
		world.playSound(null, pos.x, pos.y, pos.z, sound, SoundCategory.PLAYERS, volume, pitch);
	}

	public void particles(ParticleEffect effect, Vec3d pos, int count, double spread, double speed) {
		world.spawnParticles(effect, pos.x, pos.y, pos.z, count, spread, spread, spread, speed);
	}

	public void ring(ParticleEffect effect, Vec3d center, double radius, int points, double speed) {
		for (int i = 0; i < points; i++) {
			double angle = (Math.PI * 2 * i) / points;
			double x = center.x + Math.cos(angle) * radius;
			double z = center.z + Math.sin(angle) * radius;
			world.spawnParticles(effect, x, center.y, z, 1, 0.05, 0.05, 0.05, speed);
		}
	}

	public void line(ParticleEffect effect, Vec3d from, Vec3d to, double step, double jitter) {
		Vec3d delta = to.subtract(from);
		double length = delta.length();
		if (length < 1.0E-3) {
			return;
		}
		Vec3d dir = delta.multiply(1 / length);
		for (double d = 0; d <= length; d += step) {
			Vec3d p = from.add(dir.multiply(d));
			world.spawnParticles(effect, p.x, p.y, p.z, 1, jitter, jitter, jitter, 0);
		}
	}

	public DustParticleEffect dust(Element element, float scale) {
		return new DustParticleEffect(element.colorVector(), scale);
	}

	public static BlockStateParticleEffect blockDust(BlockState state) {
		return new BlockStateParticleEffect(ParticleTypes.BLOCK, state);
	}

	/** A burst of the element's signature particles. */
	public void elementBurst(Element element, Vec3d pos, int count) {
		particles(dust(element, 1.6f), pos, count / 2, 0.6, 0);
		switch (element) {
			case AIR -> particles(ParticleTypes.CLOUD, pos, count, 0.4, 0.15);
			case WATER -> {
				particles(ParticleTypes.SPLASH, pos, count, 0.5, 0.2);
				particles(ParticleTypes.BUBBLE_POP, pos, count / 2, 0.5, 0.1);
			}
			case EARTH -> particles(blockDust(groundBlock()), pos, count, 0.5, 0.2);
			case FIRE -> {
				particles(ParticleTypes.FLAME, pos, count, 0.4, 0.12);
				particles(ParticleTypes.LAVA, pos, count / 6, 0.3, 0);
			}
			case AVATAR -> {
				particles(ParticleTypes.END_ROD, pos, count, 0.5, 0.15);
				particles(ParticleTypes.TOTEM_OF_UNDYING, pos, count, 0.5, 0.4);
			}
		}
	}

	public static Direction horizontalFacing(Vec3d dir) {
		return Direction.getFacing(dir.x, 0, dir.z);
	}
}
