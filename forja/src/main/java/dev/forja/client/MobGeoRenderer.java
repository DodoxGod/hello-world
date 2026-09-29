package dev.forja.client;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/**
 * One of the mod's own monsters, drawn by GeckoLib. It dies by its own death clip
 * (entity.MobMoves), so it is not also tipped over on its side the way vanilla lays a body down.
 */
public class MobGeoRenderer<T extends Mob & GeoEntity> extends GeoEntityRenderer<T, LivingEntityRenderState> {
	public MobGeoRenderer(EntityRendererProvider.Context context, EntityType<? extends T> type) {
		super(context, type);
	}

	@Override
	public LivingEntityRenderState createRenderState(T mob, Void relatedObject) {
		return new LivingEntityRenderState();
	}

	@Override
	protected float getDeathMaxRotation(GeoRenderState state) {
		return 0.0F;
	}
}
