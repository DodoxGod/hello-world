package dev.forja.compat;

import dev.forja.Forja;
import dev.forja.block.ForgeTableBlock;
import dev.forja.entity.FallenSmith;
import dev.forja.forge.Alloys;
import dev.forja.menu.Station;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * What Jade says about Forja's own things when you look at them: the heat under a forge table, which
 * decides what it can melt, and how far along the Herrero Caido is. Optional: this class is only ever
 * loaded through Jade's own entrypoint.
 */
@WailaPlugin
public class ForjaJadePlugin implements IWailaPlugin {
	public static final Identifier TABLE = Forja.id("mesa");
	public static final Identifier BOSS = Forja.id("herrero_caido");
	public static final Identifier WISP = Forja.id("pavesa");

	@Override
	public void registerClient(IWailaClientRegistration registration) {
		registration.registerBlockComponent(new TableProvider(), ForgeTableBlock.class);
		registration.addConfig(TABLE, true);
		registration.registerEntityComponent(new BossProvider(), FallenSmith.class);
		registration.addConfig(BOSS, true);
		registration.registerEntityComponent(new WispProvider(), dev.forja.entity.EmberWisp.class);
		registration.addConfig(WISP, true);
	}

	/** The forge table: how hot it is standing there, and what that heat is good for. */
	private static class TableProvider implements IBlockComponentProvider {
		@Override
		public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
			if (!(accessor.getBlock() instanceof ForgeTableBlock table) || table.station != Station.FORJA) {
				return;
			}
			Alloys.Heat heat = Alloys.heatUnder(accessor.getLevel(), accessor.getPosition());
			tooltip.add(Component.translatable("gui.forja.jade.calor", heat.displayName()));
		}

		@Override
		public Identifier getUid() {
			return TABLE;
		}
	}

	/** The boss: which of his three phases you are standing in front of. */
	private static class BossProvider implements IEntityComponentProvider {
		@Override
		public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
			if (accessor.getEntity() instanceof FallenSmith smith) {
				tooltip.add(Component.translatable("gui.forja.jade.fase", smith.phase(), FallenSmith.PHASES));
			}
		}

		@Override
		public Identifier getUid() {
			return BOSS;
		}
	}

	/** The wisp: whether it has eaten, which is the whole difference between a nuisance and a problem. */
	private static class WispProvider implements IEntityComponentProvider {
		@Override
		public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
			if (accessor.getEntity() instanceof dev.forja.entity.EmberWisp wisp) {
				tooltip.add(Component.translatable(wisp.isFed() ? "gui.forja.jade.avivada" : "gui.forja.jade.apagada"));
			}
		}

		@Override
		public Identifier getUid() {
			return WISP;
		}
	}
}
