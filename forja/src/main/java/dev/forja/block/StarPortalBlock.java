package dev.forja.block;

import com.mojang.serialization.MapCodec;
import dev.forja.world.StarYard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Portal estelar: the lit hole of a star portal frame (docs/HERRERO_DIMENSION.md, 1.3). Like the end
 * portal, a sheet you step into, and it takes you at once.
 *
 * <p>In any world it takes a player to the arrival platform of the Cementerio entre Estrellas, and
 * remembers where they stood (just outside the frame, on the side they came in from). In the graveyard
 * the same block, the return well behind the arrival platform, takes them back there. Only players use
 * it: nothing else has business in the smith's graveyard.
 */
public class StarPortalBlock extends Block implements Portal {
	public static final MapCodec<StarPortalBlock> CODEC = simpleCodec(StarPortalBlock::new);
	private static final VoxelShape SHAPE = Block.box(0.0, 6.0, 0.0, 16.0, 12.0, 16.0);

	public StarPortalBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getEntityInsideCollisionShape(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
		return state.getShape(level, pos);
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean precise) {
		if (entity instanceof net.minecraft.world.entity.player.Player && entity.canUsePortal(false)) {
			entity.setAsInsidePortal(this, pos);
		}
	}

	@Override
	public int getPortalTransitionTime(ServerLevel level, Entity entity) {
		return 0;
	}

	@Override
	public @Nullable TeleportTransition getPortalDestination(ServerLevel current, Entity entity, BlockPos pos) {
		if (!(entity instanceof ServerPlayer player)) {
			return null;
		}
		TeleportTransition.PostTeleportTransition after = TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET);
		if (current.dimension() == StarYard.LEVEL) {
			return StarYard.departure(player, after);
		}
		StarYard.remember(player, outside(current, pos, player.position()), player.getYRot() + 180.0F);
		return StarYard.arrival(current.getServer(), after);
	}

	/**
	 * A place to stand just outside the frame this portal block is part of, on the side the player came
	 * in from: out past the ring, on the first spot with ground under it and room to stand.
	 */
	public static Vec3 outside(Level level, BlockPos portal, Vec3 from) {
		// The middle of the lit hole: the portal block with portal on all four sides, or this one.
		BlockPos centre = portal;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos at = portal.offset(dx, 0, dz);
				if (level.getBlockState(at.north()).getBlock() instanceof StarPortalBlock && level.getBlockState(at.south()).getBlock() instanceof StarPortalBlock
					&& level.getBlockState(at.east()).getBlock() instanceof StarPortalBlock && level.getBlockState(at.west()).getBlock() instanceof StarPortalBlock) {
					centre = at;
				}
			}
		}
		Vec3 middle = Vec3.atBottomCenterOf(centre);
		Vec3 away = new Vec3(from.x - middle.x, 0.0, from.z - middle.z);
		if (away.lengthSqr() < 1.0E-4) {
			away = new Vec3(0.0, 0.0, 1.0);
		}
		away = away.normalize();
		for (double out = 3.6; out <= 7.0; out += 0.5) {
			Vec3 spot = middle.add(away.scale(out));
			for (int dy = 3; dy >= -3; dy--) {
				BlockPos feet = BlockPos.containing(spot.x, centre.getY() + dy, spot.z);
				if (level.getBlockState(feet.below()).isSolid() && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
					&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
					return new Vec3(spot.x, feet.getY(), spot.z);
				}
			}
		}
		return middle.add(away.scale(3.6)).add(0.0, 1.0, 0.0);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.END_ROD, pos.getX() + random.nextDouble(), pos.getY() + 0.8, pos.getZ() + random.nextDouble(),
				0.0, 0.02 + random.nextDouble() * 0.03, 0.0);
		}
		if (random.nextInt(6) == 0) {
			level.addParticle(dev.forja.registry.ModParticles.BRASA, pos.getX() + random.nextDouble(), pos.getY() + 0.8,
				pos.getZ() + random.nextDouble(), 0.0, 0.02, 0.0);
		}
	}
}
