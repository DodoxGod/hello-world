package dev.forja.block.entity;

import dev.forja.block.CastingTableBlock;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.forge.Quality;
import dev.forja.item.CastingFrameItem;
import dev.forja.item.CastingMouldItem;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.PartType;
import dev.forja.registry.ModComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * One casting table, once a tick.
 *
 * <p>The cycle is short enough to say in a sentence: a mould or a frame on the table, exactly the metal
 * that part or tool is worth pulled out of the tanks and into it, five seconds while it sets, and the
 * finished thing on the table where the mould was. Nothing about it is a screen — you can watch every
 * step of it happen on top of the block.
 *
 * <p>Parts used to be cast inside the casting box's menu. Andy, 2026-09-28: "¿se podría hacer que el
 * líquido tenga que caer en la herramienta? pasando primero por el colador antes de que llegue a la mesa
 * con el molde, conservando todas las funciones que se obtienen por hacer el colado". So a mould now goes
 * on a table exactly like a frame, and every effect of the box's casting came with it:
 * <ul>
 * <li>the <b>strainer</b> standing on top of the table (StrainerBlockEntity) decides the pour: one that
 * holds the metal gives a clean casting, one that cannot take it breaks as the pour starts and the casting
 * comes out rough, and with none at all it is rough too — for frames as much as for moulds;</li>
 * <li>a clean <b>part</b> carries the {@link #CAST_PERCENT} upgrade the box used to give it;</li>
 * <li>the <b>table's stone</b> limits how hard a metal it takes (CastingTableBlock.Tier#holds), as the
 * box's material did.</li>
 * </ul>
 *
 * <p>The heat is the other half of the difficulty. A table warms beside anything that burns and bleeds
 * heat when nothing does, at a rate its stone decides. With no heat left it will not start a pour; with
 * the last of it, the pour it already started sets early and comes out rough whatever the strainer did.
 */
public class CastingTableBlockEntity extends BlockEntity implements WorldlyContainer {
	/** What lies on the table to be filled: a frame or a mould. */
	public static final int SLOT_FRAME = 0;
	public static final int SLOT_OUTPUT = 1;
	public static final int SIZE = 2;

	/** Ticks one casting takes. The same on every table: they differ in heat, not in speed. */
	public static final int COOK = 100;

	/** The most heat a table holds. */
	public static final int HOT = 200;

	/** What one casting spends, so a hot table is good for five of them before it needs a fire. */
	public static final int SPEND = 40;

	/** What anything burning beside or beneath it puts back every second. */
	public static final int WARMS = 25;

	/** How often heat is settled. */
	public static final int EVERY = 20;

	/**
	 * What a part poured cleanly is worth, as a percentage of one upgrade.
	 *
	 * <p>This is the whole reason to own a foundry. A part cut at the bench is a part; a part poured
	 * through a strainer that held, into a mould, off a table that was hot enough, comes out already
	 * <b>better than the sum of its metal</b> — and the upgrade rides up into whatever you build with
	 * it. A rough pour gets nothing at all, which is what makes the strainer worth infusing and the
	 * heat worth keeping. (It was the casting box's number while the box still cast parts.)
	 */
	public static final int CAST_PERCENT = 15;

	private static final int[] TOP = {SLOT_FRAME};
	private static final int[] BOTTOM = {SLOT_OUTPUT};

	private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
	private int progress;
	private int heat;
	/** What is in the mould right now, and how much of it. Empty until a pour starts. */
	private @Nullable Item metal;
	/** What the pour that is finishing was of, for the one tick finish() needs it after clearing metal. */
	private @Nullable Item lastPour;
	private int amount;
	/**
	 * Whether this pour will come out rough: it went in on the last of the heat, or through a strainer
	 * that broke, or through no strainer at all. Decided when the pour starts, kept until it sets.
	 */
	private boolean rough;
	/**
	 * The tick the pour began on.
	 *
	 * <p>The client draws the metal rising in the frame, and sending it a packet every tick to say so
	 * would be a packet a tick per table. It is told when the pour started instead, once, and works the
	 * rest out off the world clock.
	 */
	private long startedAt;
	private int settleIn = -1;
	/** Whether the last second of heat was a warming one, so a pour is not started on heat still coming in. */
	private boolean warming = true;
	/** No new pour before this tick: a moment to change the mould after taking a piece off by hand. */
	private long pauseUntil;

	/** How long taking a piece off by hand holds the next pour back: two seconds. */
	public static final int HAND_PAUSE = 40;

	public CastingTableBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.MESA_DE_COLADA, pos, state);
	}

	/**
	 * The colour of whatever is cooling in it, or -1 if nothing is.
	 *
	 * <p>Only the block's ambient particles want this, and they want it on the client, so it reads the
	 * stored metal rather than going near the assembler.
	 */
	public int metalColour() {
		if (this.metal == null) {
			return -1;
		}
		ForgeMaterial material = ForgeMaterial.fromInput(new ItemStack(this.metal));
		return material == null ? -1 : material.color;
	}

	public CastingTableBlock.Tier tier() {
		return this.getBlockState().getBlock() instanceof CastingTableBlock table ? table.tier : CastingTableBlock.Tier.LOSA;
	}

	// ------------------------------------------------------------------ what the renderer reads

	/** The mould or the frame lying on the table. */
	public ItemStack frame() {
		return this.items.get(SLOT_FRAME);
	}

	public ItemStack result() {
		return this.items.get(SLOT_OUTPUT);
	}

	public @Nullable Item metal() {
		return this.metal;
	}

	public int amount() {
		return this.amount;
	}

	public int heat() {
		return this.heat;
	}

	/** Whether the pour under way will come out rough. */
	public boolean pouringRough() {
		return this.metal != null && this.rough;
	}

	/** How far along the pour is, 0 to 1, as the server counts it. */
	public float progress() {
		return this.metal == null ? 0.0F : Math.min(1.0F, this.progress / (float) COOK);
	}

	/** The same thing read off the world clock, which is what the renderer has to go on. */
	public float progressAt(double gameTime) {
		return this.metal == null ? 0.0F
			: (float) Math.max(0.0, Math.min(1.0, (gameTime - this.startedAt) / COOK));
	}

	/** The strainer standing on this table, if there is one: the pour goes through it. */
	public @Nullable StrainerBlockEntity strainer() {
		return this.level != null && this.level.getBlockEntity(this.worldPosition.above()) instanceof StrainerBlockEntity strainer
			? strainer : null;
	}

	/** Whether this table's stone will take that metal at all. */
	public boolean takes(ForgeMaterial material) {
		return material.durability <= this.tier().holds;
	}

	// ------------------------------------------------------------------ what lies on it

	/** Oricalco poured over an ender pearl on the table makes an oricalco pearl (docs/HERRERO_DIMENSION.md, 1.2). */
	public static final int PEARL_COST = 2;

	/** Whether this is a pearl set on the table to have oricalco poured over it. */
	public static boolean pearl(ItemStack stack) {
		return stack.is(net.minecraft.world.item.Items.ENDER_PEARL);
	}

	/** What one pour off this mould or frame costs, or 0 if nothing castable is on the table. */
	public static int cost(ItemStack pattern) {
		if (pearl(pattern)) {
			return PEARL_COST;
		}
		ForgeType type = CastingFrameItem.typeOf(pattern);
		if (type != null) {
			return CastingFrameItem.cost(type);
		}
		PartType part = CastingMouldItem.partOf(pattern);
		return part == null ? 0 : part.cost;
	}

	/** Whether what is on the table can be poured in that metal: every part of a frame, or the mould's one. */
	public static boolean castable(ItemStack pattern, ForgeMaterial material) {
		ForgeType type = CastingFrameItem.typeOf(pattern);
		if (type != null) {
			return CastingFrameItem.castable(type, material);
		}
		PartType part = CastingMouldItem.partOf(pattern);
		return part != null && part.accepts(material);
	}

	/** Whether this goes on a table at all. */
	public static boolean pattern(ItemStack stack) {
		return CastingFrameItem.typeOf(stack) != null || CastingMouldItem.partOf(stack) != null || pearl(stack);
	}

	// ------------------------------------------------------------------ the work

	public static void serverTick(Level level, BlockPos pos, BlockState state, CastingTableBlockEntity table) {
		if (table.settleIn < 0) {
			// Staggered by position, so a row of twenty tables does not settle on the same tick.
			table.settleIn = Math.floorMod(pos.hashCode(), EVERY);
		}
		if (table.settleIn-- <= 0) {
			table.settleIn = EVERY;
			table.settle(level, pos);
		}
		if (table.metal == null) {
			table.progress = 0;
			table.start(level);
		} else if (++table.progress >= COOK) {
			table.finish(level, pos);
		}
		boolean pouring = table.metal != null;
		if (state.getValue(CastingTableBlock.LIT) != pouring) {
			level.setBlock(pos, state.setValue(CastingTableBlock.LIT, pouring), Block.UPDATE_ALL);
		}
	}

	/**
	 * One second of heat: what the fire gives, less what the stone lets go of.
	 *
	 * <p>Underneath counts as well as beside, because a table is a thing you stand over a fire, and the
	 * wisp lantern is still the tidiest way to do it.
	 */
	private void settle(Level level, BlockPos pos) {
		boolean beside = false;
		for (Direction side : Direction.values()) {
			if (dev.forja.forge.HeatSources.warmsBeside(level.getBlockState(pos.relative(side)))) {
				beside = true;
				break;
			}
		}
		// And the heat every forge reads (Alloys.heatAt): a fire under the table warms it like one beside it,
		// and a hot fluid in a pipe against it (FUNDICION_V2, part B) at the fluid's own rate, up to the
		// fluid's own ceiling — steam never past tepid, blaze blood twice as fast as a fire — paid for only
		// while it is what does the warming.
		dev.forja.forge.HeatSources.Supply line = dev.forja.forge.HeatSources.at(level, pos);
		boolean fire = beside || (!line.piped() && line.heat() != dev.forja.forge.Alloys.Heat.FRIA);
		int rate = fire ? WARMS : 0;
		int cap = fire ? HOT : 0;
		boolean fromPipe = false;
		if (line.piped() && (line.fluid().tableWarms > rate || line.fluid().tableCap > cap)) {
			rate = Math.max(rate, line.fluid().tableWarms);
			cap = Math.max(cap, line.fluid().tableCap);
			fromPipe = true;
		}
		int was = this.heat;
		this.warming = rate > 0;
		if (this.warming) {
			if (this.heat < cap) {
				this.heat = Math.min(cap, this.heat + rate);
				if (fromPipe) {
					line.draw(level, line.fluid().draw * dev.forja.forge.HeatFluid.TABLE_DRAW_TICKS);
				}
			}
		} else {
			// Ice brine against a table with nothing warming it takes the heat out twice as fast.
			this.heat = Math.max(0, this.heat - this.tier().cools * (line.quench() ? 2 : 1));
		}
		if (was != this.heat) {
			this.setChanged();
		}
	}

	/** Pulls exactly what the part or tool is worth out of the tanks, if everything is ready for it. */
	private void start(Level level) {
		if (this.heat <= 0 || level.getGameTime() < this.pauseUntil) {
			return;
		}
		// A table that is being warmed waits until it has a whole pour's worth of heat. "The last of the
		// heat" is a table going cold; but a table set down on a wisp lantern — the guide's own cure for
		// rough pours — started pouring the moment its first 25 heat came in, under the 40 a pour costs,
		// and turned out every first tool of the day rough.
		if (this.warming && this.heat < SPEND) {
			return;
		}
		ItemStack pattern = this.frame();
		int cost = cost(pattern);
		if (cost <= 0 || !this.result().isEmpty()) {
			return;
		}
		for (MeltTankBlockEntity tank : MeltTankBlockEntity.reachableFrom(level, this.worldPosition)) {
			Item held = tank.bankMetal();
			if (held == null || tank.isSet() || tank.bankAmount() < cost) {
				continue;
			}
			// A pearl takes oricalco and nothing else, on any table: it is a coat, not a part.
			if (pearl(pattern)) {
				if (held != dev.forja.registry.ModItems.ORICALCO || tank.drain(cost) < cost) {
					continue;
				}
				this.metal = held;
				this.amount = cost;
				this.rough = false;
				this.progress = 0;
				this.startedAt = level.getGameTime();
				if (level instanceof ServerLevel server) {
					server.playSound(null, this.worldPosition, SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.BLOCKS, 0.5F, 1.4F);
				}
				this.setChanged();
				return;
			}
			// Oricalco used to be poured on pearls and nothing else; it is a forge material now (ForgeMaterial.ORICALCO)
			// and goes into moulds and frames like any other poured metal, on a table whose stone holds it.
			ForgeMaterial material = ForgeMaterial.fromInput(new ItemStack(held));
			// The table's own stone has a limit, the one the casting box's material used to set: a slate
			// table will not have diamond poured into it, however good the strainer on top.
			if (material == null || !castable(pattern, material) || !this.takes(material)) {
				continue;
			}
			if (tank.drain(cost) < cost) {
				continue;
			}
			this.metal = held;
			this.amount = cost;
			// Started on the last of the heat: the metal will set in the mould before it is done.
			boolean cold = this.heat < SPEND;
			// And it has to come through the strainer first. One that holds lets it through clean; one that
			// cannot take it goes with the first of the pour; none at all and nothing catches the dross.
			this.rough = cold | !this.strain(level, material);
			this.progress = 0;
			this.startedAt = level.getGameTime();
			if (level instanceof ServerLevel server) {
				server.playSound(null, this.worldPosition, SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.BLOCKS, 0.5F, 1.4F);
			}
			this.setChanged();
			return;
		}
	}

	/**
	 * The pour meets the strainer on top of the table. True if it came through clean.
	 *
	 * <p>A strainer that cannot take this metal breaks right there — the block is gone, in a burst of its
	 * own pieces and a spit of the melt — exactly as it used to vanish from the box's slot, except that
	 * now you see it go.
	 */
	private boolean strain(Level level, ForgeMaterial melt) {
		StrainerBlockEntity strainer = this.strainer();
		if (strainer == null) {
			return false;
		}
		if (strainer.survives(melt)) {
			return true;
		}
		BlockPos at = strainer.getBlockPos();
		// No drop: it went with the pour. destroyBlock plays its breaking sound and throws its pieces.
		level.destroyBlock(at, false);
		if (level instanceof ServerLevel server) {
			server.playSound(null, at, SoundEvents.DECORATED_POT_SHATTER, SoundSource.BLOCKS, 0.9F, 0.8F);
			server.playSound(null, at, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.8F, 1.0F);
			server.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA,
				at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 6, 0.2, 0.1, 0.2, 0.0);
			server.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
				at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 6, 0.2, 0.1, 0.2, 0.01);
		}
		return false;
	}

	/** The part or the tool comes off the mould, better or worse than it went in. */
	private void finish(Level level, BlockPos pos) {
		ItemStack pattern = this.frame();
		ForgeMaterial material = this.metal == null ? null : ForgeMaterial.fromInput(new ItemStack(this.metal));
		this.lastPour = this.metal;
		this.progress = 0;
		this.metal = null;
		this.amount = 0;
		boolean wasRough = this.rough;
		this.rough = false;
		if (pearl(pattern) && this.lastPour == dev.forja.registry.ModItems.ORICALCO) {
			// The pearl is gone into the metal: the frame slot empties and the oricalco pearl comes out.
			this.items.set(SLOT_FRAME, ItemStack.EMPTY);
			this.items.set(SLOT_OUTPUT, new ItemStack(dev.forja.registry.ModItems.PERLA_DE_ORICALCO));
			this.heat = Math.max(0, this.heat - SPEND);
			if (level instanceof ServerLevel server) {
				server.playSound(null, pos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 0.6F, 1.3F);
				server.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
					pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 12, 0.25, 0.1, 0.25, 0.02);
			}
			this.setChanged();
			return;
		}
		if (material == null) {
			return;
		}
		ForgeType type = CastingFrameItem.typeOf(pattern);
		PartType part = CastingMouldItem.partOf(pattern);
		ItemStack cast;
		boolean steady = false;
		boolean quenched = false;
		if (type != null) {
			// Forge breath against the table steadies the hand further, and it is paid for per tool.
			dev.forja.forge.HeatSources.Supply line = dev.forja.forge.HeatSources.at(level, pos);
			float breath = !wasRough && line.steadyBonus() > 0.0F
				&& line.draw(level, line.fluid().draw * dev.forja.forge.HeatFluid.TABLE_DRAW_TICKS) > 0 ? line.steadyBonus() : 0.0F;
			// The steady hand of good stone, which only a whole tool can show: see castTool.
			steady = !wasRough && level.getRandom().nextFloat() < this.tier().luck + breath;
			cast = castTool(level, type, material, wasRough, steady);
			// Ice brine against the table: the tool is quenched in water the moment it sets, for good.
			if (line.quench() && !cast.has(ModComponents.TEMPLE) && line.spendQuench(level)) {
				cast.set(ModComponents.TEMPLE, dev.forja.forge.Temple.AGUA.id());
				quenched = true;
			}
		} else if (part != null) {
			cast = castPart(level, part, material, wasRough);
		} else {
			// The mould was taken off mid-pour (a hopper, a mod): the metal it was holding is lost with it.
			return;
		}
		this.items.set(SLOT_OUTPUT, cast);
		this.heat = Math.max(0, this.heat - SPEND);
		if (level instanceof ServerLevel server) {
			server.playSound(null, pos, wasRough ? SoundEvents.LAVA_EXTINGUISH : SoundEvents.ANVIL_LAND,
				SoundSource.BLOCKS, 0.5F, wasRough ? 0.7F : 1.4F);
			server.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA,
				pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 4, 0.2, 0.02, 0.2, 0.0);
			if (steady) {
				server.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.4F, 1.8F);
				server.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
					pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 12, 0.25, 0.1, 0.25, 0.02);
			}
			if (quenched) {
				server.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7F, 1.0F);
				server.sendParticles(dev.forja.registry.ModParticles.VAPOR,
					pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 20, 0.25, 0.15, 0.25, 0.05);
			}
		}
		this.setChanged();
	}

	/** A whole tool off a frame: every slot of it cast, and the stone's steady hand on a clean one. */
	private static ItemStack castTool(Level level, ForgeType type, ForgeMaterial material, boolean wasRough, boolean steady) {
		ItemStack cast = Assembler.create(type, java.util.Collections.nCopies(type.slots.size(), material),
			level.registryAccess());
		// Nobody forged this, so nothing gave it a potential: the table does, and every slot of it is cast.
		cast.set(ModComponents.POTENCIAL, dev.forja.forge.Potential.atCasting(wasRough, steady));
		if (!wasRough) {
			cast.set(ModComponents.COLADAS, (1 << type.slots.size()) - 1);
		}
		if (wasRough) {
			// The metal set in the frame before it was done, and the piece carries it for good.
			cast.set(ModComponents.ROUGH, true);
			Assembler.rewrite(cast, BuiltInRegistries.BLOCK, BuiltInRegistries.ITEM);
		} else if (steady) {
			// The steady hand of good stone: the same +5% a smith gets for stopping the hammer dead centre.
			Quality.markPerfect(cast);
			// Every number of the piece goes up, so the components have to be written again.
			Assembler.rewrite(cast, BuiltInRegistries.BLOCK, BuiltInRegistries.ITEM);
		}
		return cast;
	}

	/**
	 * One part off a mould, exactly as the casting box used to turn it out: rough, or cast and carrying
	 * one upgrade at {@link #CAST_PERCENT}.
	 */
	public static ItemStack castPart(Level level, PartType part, ForgeMaterial material, boolean rough) {
		ItemStack cast = Assembler.createPart(part, material);
		if (rough) {
			cast.set(ModComponents.ROUGH, true);
		} else {
			// Poured, and poured well: whatever is built with it has more room for upgrades than the same
			// thing cut at the bench (forge/Potential), and it comes with an upgrade of its own.
			cast.set(ModComponents.COLADA, true);
			bless(cast, part, level.getRandom());
		}
		return cast;
	}

	/**
	 * Puts one upgrade on a cleanly poured part, chosen from the ones anything built with that part
	 * could actually use.
	 *
	 * <p>A part has no {@link ForgeType} of its own, so the choice is made over every type this part
	 * goes into: a blade could become a sword, a dagger or a scythe, and an upgrade that suits any of
	 * them suits the blade.
	 */
	private static void bless(ItemStack cast, PartType part, net.minecraft.util.RandomSource random) {
		java.util.List<dev.forja.upgrade.Upgrade> possible = new java.util.ArrayList<>();
		for (dev.forja.upgrade.Upgrade upgrade : dev.forja.upgrade.Upgrade.values()) {
			for (ForgeType type : ForgeType.values()) {
				if (type.slots.contains(part.base()) && upgrade.appliesTo(type)) {
					possible.add(upgrade);
					break;
				}
			}
		}
		if (possible.isEmpty()) {
			return;
		}
		dev.forja.upgrade.Upgrade chosen = possible.get(random.nextInt(possible.size()));
		cast.set(ModComponents.UPGRADES, dev.forja.upgrade.Upgrades.EMPTY.with(chosen, CAST_PERCENT));
	}

	// ------------------------------------------------------------------ by hand

	/**
	 * A mould or a frame goes down with a full hand and the finished thing comes up with an empty one.
	 *
	 * <p>Taking the finished piece off by hand holds the next pour back for {@link #HAND_PAUSE} ticks. A
	 * table with a mould on it and metal to hand pours again the moment it is empty, and the next tick is
	 * sooner than any hand: the smith who came to change the mould for a frame found the table already
	 * pouring another part, and could not touch it until that one had set. A hopper taking from below
	 * never gets the pause, so automation runs at full speed.
	 */
	public boolean hand(Player player, ItemStack held) {
		if (this.level == null) {
			return false;
		}
		if (!this.result().isEmpty()) {
			// Whatever is on the table, the finished thing comes off first.
			player.getInventory().placeItemBackInInventory(this.result().copy());
			this.items.set(SLOT_OUTPUT, ItemStack.EMPTY);
			this.pauseUntil = this.level.getGameTime() + HAND_PAUSE;
			this.setChanged();
			return true;
		}
		if (!held.isEmpty() && pattern(held) && this.metal == null && !this.frame().isEmpty()
			&& !ItemStack.isSameItemSameComponents(held, this.frame())) {
			// A different mould or frame on a table at rest: they change places.
			ItemStack was = this.frame().copy();
			this.items.set(SLOT_FRAME, held.split(1));
			player.getInventory().placeItemBackInInventory(was);
			this.level.playSound(null, this.worldPosition, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 0.6F, 1.0F);
			this.setChanged();
			return true;
		}
		if (!held.isEmpty() && pattern(held) && this.frame().isEmpty()) {
			this.items.set(SLOT_FRAME, held.split(1));
			this.level.playSound(null, this.worldPosition, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 0.6F, 1.0F);
			this.setChanged();
			return true;
		}
		if (held.isEmpty() && !this.frame().isEmpty()) {
			if (this.metal != null) {
				// Not while there is metal in it: that is how you get molten iron on the floor.
				this.say(player, Component.translatable("gui.forja.mesa_colada.colando"));
				return false;
			}
			player.getInventory().placeItemBackInInventory(this.frame().copy());
			this.items.set(SLOT_FRAME, ItemStack.EMPTY);
			this.setChanged();
			return true;
		}
		this.say(player, this.describe());
		return false;
	}

	/** What the table is doing, in one line, and what the strainer on it will do about it. */
	public Component describe() {
		ItemStack pattern = this.frame();
		ForgeType type = CastingFrameItem.typeOf(pattern);
		PartType part = CastingMouldItem.partOf(pattern);
		if (type == null && part == null) {
			return Component.translatable("gui.forja.mesa_colada.vacia");
		}
		if (this.metal != null) {
			return Component.translatable("gui.forja.mesa_colada.colando_de",
				new ItemStack(this.metal).getHoverName(), Math.round(this.progress() * 100.0F));
		}
		Component waiting = type != null
			? Component.translatable("gui.forja.mesa_colada.espera",
				type.displayName(), CastingFrameItem.cost(type), this.heat * 100 / HOT)
			: Component.translatable("gui.forja.mesa_colada.espera_molde",
				part.displayName(), part.cost, this.heat * 100 / HOT);
		StrainerBlockEntity strainer = this.strainer();
		Component gate = strainer == null
			? Component.translatable("gui.forja.mesa_colada.sin_colador")
			: Component.translatable("gui.forja.mesa_colada.con_colador", strainer.asItem().getHoverName(), strainer.holds());
		return Component.empty().append(waiting).append(" · ").append(gate);
	}

	private void say(Player player, Component line) {
		if (player instanceof net.minecraft.server.level.ServerPlayer smith) {
			smith.sendSystemMessage(line);
		}
	}

	// ------------------------------------------------------------------ container

	/**
	 * Moulds and frames go in from the top and from the sides, the finished piece comes out of the bottom.
	 *
	 * <p>The sides are new: the top of a table is where its strainer stands now, and a hopper cannot sit
	 * there as well, so an automated table is fed from beside and emptied from below.
	 */
	@Override
	public int[] getSlotsForFace(Direction side) {
		return side == Direction.DOWN ? BOTTOM : TOP;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
		return slot == SLOT_FRAME && side != Direction.DOWN && this.canPlaceItem(slot, stack);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
		return slot == SLOT_OUTPUT && side == Direction.DOWN;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		// A mould or a frame goes in and nothing else does, and only onto an empty table.
		return slot == SLOT_FRAME && pattern(stack) && this.metal == null && this.frame().isEmpty();
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}

	@Override
	public int getContainerSize() {
		return SIZE;
	}

	@Override
	public boolean isEmpty() {
		return this.items.stream().allMatch(ItemStack::isEmpty);
	}

	@Override
	public ItemStack getItem(int slot) {
		return this.items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		ItemStack taken = ContainerHelper.removeItem(this.items, slot, count);
		if (!taken.isEmpty()) {
			this.setChanged();
		}
		return taken;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return ContainerHelper.takeItem(this.items, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		this.items.set(slot, stack);
		this.setChanged();
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player);
	}

	@Override
	public void clearContent() {
		this.items.clear();
	}

	/**
	 * Breaking the table spills the mould, the finished piece, and the metal that was setting in the mould
	 * — which the tank it came out of no longer has. None of it dropped: the block did this in
	 * affectNeighborsAfterRemoval, which only runs once this block entity is gone.
	 */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (this.level == null) {
			return;
		}
		net.minecraft.world.Containers.dropContents(this.level, pos, this);
		if (this.metal != null && this.amount > 0) {
			ItemStack spilled = new ItemStack(this.metal, this.amount);
			while (!spilled.isEmpty()) {
				net.minecraft.world.Containers.dropItemStack(this.level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
					spilled.split(spilled.getMaxStackSize()));
			}
		}
	}

	/**
	 * Placed, broken, loaded or unloaded: the network around the table — the deposits it pulls from, however
	 * far down the pipe they are — is worked out again when next asked (MeltNetwork).
	 */
	@Override
	public void clearRemoved() {
		super.clearRemoved();
		MeltNetwork.changed(this.level, this.worldPosition);
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		MeltNetwork.changed(this.level, this.worldPosition);
	}

	// ------------------------------------------------------------------ saving and syncing

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, this.items);
		this.progress = input.getIntOr("Progress", 0);
		this.heat = input.getIntOr("Heat", 0);
		this.amount = input.getIntOr("Amount", 0);
		this.rough = input.getBooleanOr("Rough", false);
		this.startedAt = input.getLongOr("Started", 0L);
		String id = input.getStringOr("Metal", "");
		this.metal = id.isEmpty() ? null
			: BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.Identifier.parse(id)).orElse(null);
		if (this.metal == null) {
			this.amount = 0;
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, this.items);
		output.putInt("Progress", this.progress);
		output.putInt("Heat", this.heat);
		output.putInt("Amount", this.amount);
		output.putBoolean("Rough", this.rough);
		output.putLong("Started", this.startedAt);
		if (this.metal != null) {
			output.putString("Metal", BuiltInRegistries.ITEM.getKey(this.metal).toString());
		}
	}

	@Override
	public void setChanged() {
		super.setChanged();
		if (this.level != null && !this.level.isClientSide()) {
			// The mould and the melt are drawn on top of the block, so the client needs both.
			this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_ALL);
		}
	}

	@Override
	public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
		return this.saveCustomOnly(registries);
	}
}
