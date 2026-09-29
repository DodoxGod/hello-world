package dev.forja.block.entity;

import java.util.List;

import dev.forja.block.CrucibleBlock;
import dev.forja.forge.Alloys;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.ForgedParts;
import dev.forja.part.PartType;
import dev.forja.registry.ModComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * What the crucible actually does, once a tick.
 *
 * <p>Two jobs, in this order. If the two input slots hold an alloy's ingredients and the crucible runs
 * hot enough, it pours that alloy. If one of them holds something a smith made, it melts it back down
 * into the material it was cut from, at a share that depends on what the crucible is built of — which is
 * the reason to keep upgrading it long after the alloys have stopped being a problem.
 *
 * <p>It is a {@link WorldlyContainer} on the furnace's own convention: in from the top, fuel from the
 * sides, out from the bottom. That is the whole automation story, and it is deliberately the one every
 * player already knows.
 */
public class CrucibleBlockEntity extends BlockEntity implements WorldlyContainer, net.minecraft.world.MenuProvider {
	public static final int SLOT_FIRST = 0;
	public static final int SLOT_SECOND = 1;
	public static final int SLOT_FUEL = 2;
	public static final int SLOT_OUTPUT = 3;
	public static final int SIZE = 4;

	private static final int[] TOP = {SLOT_FIRST, SLOT_SECOND};
	private static final int[] SIDES = {SLOT_FUEL};
	private static final int[] BOTTOM = {SLOT_OUTPUT};

	private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
	private int burning;
	private int ticks;
	private int remelted;
	private int burnLength;
	private int progress;

	public CrucibleBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CRISOL, pos, state);
	}

	/**
	 * The colour of the metal in the pot — the material's own, not yet molten — or -1 if there is none.
	 *
	 * <p>On the client this is what the server last said. It has to be: the client's copy of a crucible
	 * has nothing in it, because what a container holds is only ever sent to somebody with its screen
	 * open. The dust over the rim was written to be "the colour of the metal" and read the client's empty
	 * slots, so it never once drew; and every lit pot in a workshop glowed the same orange. The colour is
	 * now the one thing about the pot that is sent (see {@link #getUpdateTag}), when it changes.
	 */
	public int meltColour() {
		if (this.level != null && this.level.isClientSide()) {
			return this.shownColour;
		}
		return this.colourOf(this.pending());
	}

	/** What the pot is turning into if it is turning into something, else the first metal that is in it. */
	private int colourOf(@Nullable Pour pour) {
		if (pour != null) {
			ForgeMaterial made = ForgeMaterial.fromInput(pour.result());
			if (made != null) {
				return made.color;
			}
		}
		for (int slot : new int[] {SLOT_FIRST, SLOT_SECOND}) {
			ItemStack stack = this.items.get(slot);
			ForgedParts parts = stack.get(ModComponents.PARTS);
			ForgeMaterial material = parts != null && !parts.materials().isEmpty() ? parts.materials().getFirst()
				: stack.has(ModComponents.MATERIAL) ? stack.get(ModComponents.MATERIAL) : ForgeMaterial.fromInput(stack);
			if (material != null) {
				return material.color;
			}
		}
		return -1;
	}

	/** The colour the clients were last told, on the server; the colour this client was told, on a client. */
	private int shownColour = -1;

	@Override
	public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
		return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
	}

	/** The colour and nothing else: what is in the slots is nobody's business but the screen's. */
	@Override
	public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
		net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
		tag.putInt("Colour", this.colourOf(this.pending()));
		return tag;
	}

	/** Its own clock, so melting a set bank does not ride on the world's. */
	private int tickCount() {
		return ++this.ticks;
	}

	/** How much metal this crucible has burnt melting set banks back down, for the tests. */
	public int remelted() {
		return this.remelted;
	}

	public CrucibleBlock.Tier tier() {
		return this.getBlockState().getBlock() instanceof CrucibleBlock crucible ? crucible.tier : CrucibleBlock.Tier.BARRO;
	}

	// ------------------------------------------------------------------ the work

	public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, CrucibleBlockEntity crucible) {
		boolean wasLit = crucible.burning > 0;
		if (crucible.burning > 0) {
			crucible.burning--;
		}
		// The heat line first: it decides how hot the pot is before the pot decides what it can do.
		dev.forja.forge.HeatSources.Supply piped = crucible.readHeat(level);
		Pour pour = crucible.pending();
		// A bank that has set beside the pot is work too: it fires up by itself to save your metal,
		// because a smith who walked away and came back to a cold wall should not also have to be told
		// which button melts it.
		MeltTankBlockEntity frozen = pour != null ? null : crucible.setTank();
		crucible.job = pour != null ? pour.job() : frozen != null ? JOB_REMELT : crucible.idleReason;
		crucible.jobWhat = pour != null ? pour.what() : frozen != null ? 0 : crucible.idleWhat;
		if (pour == null && frozen == null) {
			crucible.progress = 0;
		} else {
			if (crucible.doused) {
				// Ice brine against the pot: the fire goes out, and no ember will light while it is there.
				crucible.burning = 0;
			} else if (crucible.pipeFire) {
				// A heat pipe is a fire of its own: it keeps the pot lit and no ember is burnt.
				if (crucible.burning <= PIPE_BURN) {
					crucible.burning = PIPE_BURN;
					crucible.burnLength = PIPE_BURN;
					crucible.pipeLit = true;
				}
			} else if (crucible.burning == 0 && crucible.light(level)) {
				// Fresh fuel: the pour keeps its place in the queue rather than starting over.
				crucible.setChanged();
			}
			if (crucible.burning > 0 && crucible.pipeFire) {
				// What the fire costs when it comes down a pipe: the fluid's own draw, every tick it works.
				piped.draw(level, piped.fluid().draw);
			}
			if (crucible.burning > 0 && frozen != null) {
				if (crucible.tickCount() % 20 == 0) {
					crucible.remelted += frozen.remelt();
				}
			} else if (crucible.burning > 0) {
				// Blaze blood works half again as fast as an ember; everything else at the ember's pace.
				crucible.speedCarry += crucible.pipeFire ? piped.meltPercent() : 100;
				crucible.progress += crucible.speedCarry / 100;
				crucible.speedCarry %= 100;
				int cook = crucible.fedByTank(pour) ? Math.max(1, crucible.tier().cook / 2) : crucible.tier().cook;
				if (crucible.progress >= cook) {
					crucible.progress = 0;
					crucible.finish(level, pos, pour);
				}
			} else {
				crucible.progress = Math.max(0, crucible.progress - 2);
			}
		}
		boolean lit = crucible.burning > 0;
		if (lit != wasLit) {
			level.setBlock(pos, state.setValue(CrucibleBlock.LIT, lit), net.minecraft.world.level.block.Block.UPDATE_ALL);
			crucible.setChanged();
		}
		// Twice a second, and a packet only when the answer is a different one: a pot changes colour when
		// somebody puts something else in it, which is rare, and a packet a tick per pot is not.
		if (level.getGameTime() % 10L == 0L) {
			int colour = crucible.colourOf(pour);
			if (colour != crucible.shownColour) {
				crucible.shownColour = colour;
				BlockState now = level.getBlockState(pos);
				level.sendBlockUpdated(pos, now, now, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
			}
		}
	}

	/** How long one ember keeps a crucible going. */
	public static final int EMBER_TICKS = 400;

	// ------------------------------------------------------------------ the heat line (FUNDICION_V2, part B)

	/**
	 * How long the fire a heat pipe lights lasts without the pipe: a few ticks, so the pot goes out almost
	 * as soon as the vessel behind the pipe runs dry.
	 */
	public static final int PIPE_BURN = 4;

	/** The heat the pot works at this tick: its own tier's, or a heat pipe's. Null until the first tick. */
	private Alloys.@Nullable Heat heatNow;
	/** Whether a heat pipe is the pot's fire this tick (no ember is burnt, the fluid is). */
	private boolean pipeFire;
	/** Whether what is burning now was lit by the pipe rather than by an ember or a lantern. */
	private boolean pipeLit;
	/** Whether ice brine is against the pot with nothing hot: it will not burn. */
	private boolean doused;
	/** The fraction of a step a faster fluid has carried over, in hundredths. */
	private int speedCarry;
	/** The hardest metal the pot melts this tick: no limit on its own fire, the fluid's on a pipe's. */
	private int meltCap = Integer.MAX_VALUE;

	/** The heat the crucible works at: its tier's own, or what the heat line brings it. */
	public Alloys.Heat heat() {
		return this.heatNow != null ? this.heatNow : this.tier().heat;
	}

	/**
	 * Decides how hot the pot is this tick, from {@link Alloys#heatAt} (the one place heat is read: the
	 * block under it or a heat pipe against it, the hotter) and the heat line's own qualities.
	 *
	 * <ul>
	 * <li>With nothing under it and no pipe it is exactly what it always was: its tier's heat, fed by embers.</li>
	 * <li>A hot <b>pipe</b> is a fire of its own: it is the pot's fire whenever it burns at least as hot as
	 * the pot's tier, or when the pot has nothing of its own to burn — so steam into an iron pot with no
	 * ember in it only melts what steam melts, and forge breath takes a clay pot to white heat. No ember is
	 * burnt while it is; the fluid is.</li>
	 * <li>What the pot <b>stands on</b> (lava, magma, a lantern...) is not a fire by itself — an ember or the
	 * lantern still has to be burning, as always — but while the pot burns it burns as hot as that.</li>
	 * <li>Ice brine with nothing hot against it puts the pot out.</li>
	 * </ul>
	 */
	private dev.forja.forge.HeatSources.Supply readHeat(net.minecraft.world.level.Level level) {
		dev.forja.forge.HeatSources.Supply piped = dev.forja.forge.HeatSources.piped(level, this.worldPosition);
		Alloys.Heat own = this.tier().heat;
		Alloys.Heat around = Alloys.heatAt(level, this.worldPosition);
		Alloys.Heat under = Alloys.heatUnder(level, this.worldPosition);
		boolean ownFire = (this.burning > 0 && !this.pipeLit) || this.items.get(SLOT_FUEL).is(dev.forja.registry.ModItems.ASCUA)
			|| level.getBlockState(this.worldPosition.below()).is(dev.forja.registry.ModBlocks.FAROL_DE_PAVESA);
		this.doused = piped.quench() && !piped.piped();
		this.pipeFire = !this.doused && piped.piped() && (piped.heat().reaches(own) || !ownFire);
		this.meltCap = Integer.MAX_VALUE;
		if (this.doused) {
			this.heatNow = Alloys.Heat.FRIA;
		} else if (this.pipeFire) {
			this.heatNow = around;
			// Steam's limit holds only while steam is what is hottest about the pot.
			if (under.ordinal() < piped.heat().ordinal()) {
				this.meltCap = piped.fluid().meltsUpTo;
			}
		} else {
			this.heatNow = ownFire && around.ordinal() > own.ordinal() ? around : own;
		}
		return piped;
	}

	/**
	 * Burns one ember, or nothing at all if there is a wisp lantern underneath.
	 *
	 * <p>A crucible does not take coal, or charcoal, or a bucket of lava. It takes **ascuas**, which come
	 * out of a pavesa and out of every tool this thing melts down, so the whole loop belongs to the mod:
	 * break something, melt it, and what you get back pays for the next melt. Catching a wisp and setting
	 * the lantern under the pot skips the fuel entirely, which is the point of catching one.
	 */
	private boolean light(net.minecraft.world.level.Level level) {
		this.pipeLit = false;
		if (level.getBlockState(this.worldPosition.below()).is(dev.forja.registry.ModBlocks.FAROL_DE_PAVESA)) {
			this.burning = EMBER_TICKS;
			this.burnLength = EMBER_TICKS;
			return true;
		}
		ItemStack fuel = this.items.get(SLOT_FUEL);
		if (!fuel.is(dev.forja.registry.ModItems.ASCUA)) {
			return false;
		}
		this.burning = EMBER_TICKS;
		this.burnLength = EMBER_TICKS;
		fuel.shrink(1);
		return true;
	}

	/**
	 * The metal this pot is about to pour, or null when it is idle.
	 *
	 * <p>Only the channels ask, and only so they can be drawn the colour of what is coming out of it.
	 */
	public net.minecraft.world.item.@Nullable Item pouring() {
		// Only while it is actually burning: a pot with ore in it and no ember is not pouring anything,
		// and the channels out of it used to run bright with a metal that was never coming.
		if (this.burning <= 0) {
			return null;
		}
		Pour pour = this.pending();
		return pour == null ? null : pour.result().getItem();
	}

	// ------------------------------------------------------------------ what it is doing, for the screen

	/** Nothing to do, or nothing the screen needs telling beyond what is in the slots. */
	public static final int JOB_NONE = 0;
	/** Pouring an alloy; the detail is its index in {@link Alloys#POURABLE}. */
	public static final int JOB_ALLOY = 1;
	/** Melting something a smith made back down; the detail is the material's ordinal. */
	public static final int JOB_RECOVER = 2;
	/** Melting ore or a metal into the tanks; the detail is the material's ordinal. */
	public static final int JOB_MELT = 3;
	/** Melting a bank that has set. */
	public static final int JOB_REMELT = 4;
	/** A metal that can only go into a tank, and no tank it reaches will take it; the detail is the material. */
	public static final int JOB_NEEDS_TANK = 5;
	/** A metal this pot does not burn hot enough to melt; the detail is the material. */
	public static final int JOB_TOO_COLD = 6;

	/** What it is doing, worked out every tick, and a number that says with what. */
	private int job;
	private int jobWhat;
	/** Why the last look found nothing to do, when the reason is worth telling the smith. */
	private int idleReason;
	private int idleWhat;

	/**
	 * What one of this melts into, or empty if the crucible has no use for it as a metal.
	 *
	 * <p>"Mena al crisol, colada a las cubas" is how the guide has always put the foundry, and the pot
	 * never melted either: a smith who followed the book and dropped raw iron or a stack of ingots in it
	 * watched it sit there. Ore melts to its metal, and a metal that has to be poured — anything the parts
	 * table will not cut — melts to itself, which in a tank is the molten metal the casting box pours.
	 */
	public static ItemStack meltsTo(ItemStack stack) {
		if (stack.isEmpty() || stack.has(ModComponents.PARTS) || stack.getItem() instanceof dev.forja.item.PartItem) {
			return ItemStack.EMPTY;
		}
		if (stack.is(net.minecraft.world.item.Items.RAW_IRON) || stack.is(net.minecraft.tags.ItemTags.IRON_ORES)) {
			return new ItemStack(net.minecraft.world.item.Items.IRON_INGOT);
		}
		if (stack.is(net.minecraft.world.item.Items.RAW_COPPER) || stack.is(net.minecraft.tags.ItemTags.COPPER_ORES)) {
			return new ItemStack(net.minecraft.world.item.Items.COPPER_INGOT);
		}
		if (stack.is(net.minecraft.world.item.Items.RAW_GOLD) || stack.is(net.minecraft.tags.ItemTags.GOLD_ORES)) {
			return new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT);
		}
		if (stack.is(net.minecraft.world.item.Items.RAW_IRON_BLOCK)) {
			return new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 9);
		}
		if (stack.is(net.minecraft.world.item.Items.RAW_COPPER_BLOCK)) {
			return new ItemStack(net.minecraft.world.item.Items.COPPER_INGOT, 9);
		}
		if (stack.is(net.minecraft.world.item.Items.RAW_GOLD_BLOCK)) {
			return new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT, 9);
		}
		ForgeMaterial material = ForgeMaterial.fromInput(stack);
		if (material != null && !material.isBasic()) {
			return new ItemStack(stack.getItem());
		}
		return ItemStack.EMPTY;
	}

	/**
	 * How hot a pot has to burn to melt this metal, read off its hardness on the casting box's own
	 * scale, so "the clay one will never touch netherite" holds for melting as it does for alloys.
	 */
	public static Alloys.Heat meltHeat(ForgeMaterial material) {
		if (material.durability <= dev.forja.block.CastingBoxBlock.Tier.BARRO.holds) {
			return Alloys.Heat.TEMPLADA;
		}
		return material.durability <= dev.forja.block.CastingBoxBlock.Tier.ACERO.holds ? Alloys.Heat.CALIENTE : Alloys.Heat.FUNDIDA;
	}

	/**
	 * Whether the pot has any use for this at all: an alloy's ingredient, something a smith made, or a
	 * metal to melt. Andy: "se puede poner cualquier objeto en los contenedores" — a stick, a sword, a
	 * block of dirt all went into the two slots and sat there, and a hopper would fill them with anything.
	 */
	public static boolean takes(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		ForgedParts parts = stack.get(ModComponents.PARTS);
		if (parts != null) {
			return !parts.materials().isEmpty();
		}
		if (stack.getItem() instanceof dev.forja.item.PartItem) {
			return stack.has(ModComponents.MATERIAL);
		}
		if (!meltsTo(stack).isEmpty()) {
			return true;
		}
		for (Alloys.Recipe recipe : Alloys.POURABLE) {
			for (Alloys.Part part : recipe.inputs()) {
				if (stack.is(part.item().get())) {
					return true;
				}
			}
		}
		return false;
	}

	/** What this crucible would pour right now, or null if it has nothing to do. */
	private @Nullable Pour pending() {
		this.idleReason = JOB_NONE;
		this.idleWhat = 0;
		List<MeltTankBlockEntity> banks = this.banks();
		Pour alloy = this.alloy(banks);
		if (alloy != null) {
			return alloy;
		}
		// Nothing to alloy: see whether one of the two is something a smith made.
		for (int slot : TOP) {
			ItemStack scrap = this.items.get(slot);
			ItemStack back = this.recovered(scrap);
			ForgeMaterial backMaterial = back.isEmpty() ? null : ForgeMaterial.fromInput(back);
			if (backMaterial != null && backMaterial.durability > this.meltCap) {
				// Steam will not take an iron tool back down, any more than it melts iron ore.
				this.idleReason = JOB_TOO_COLD;
				this.idleWhat = backMaterial.ordinal();
				continue;
			}
			if (!back.isEmpty() && this.room(banks, back.getItem(), true) >= back.getCount()) {
				ForgeMaterial material = ForgeMaterial.fromInput(back);
				return new Pour(back, null, slot, 1, null, List.of(), JOB_RECOVER, material == null ? 0 : material.ordinal());
			}
		}
		// Or ore, or a metal, to melt into the tanks.
		for (int slot : TOP) {
			ItemStack stack = this.items.get(slot);
			ItemStack one = meltsTo(stack);
			if (one.isEmpty()) {
				continue;
			}
			ForgeMaterial material = ForgeMaterial.fromInput(one);
			int what = material == null ? 0 : material.ordinal();
			if (material != null && (!this.heat().reaches(meltHeat(material)) || material.durability > this.meltCap)) {
				this.idleReason = JOB_TOO_COLD;
				this.idleWhat = what;
				continue;
			}
			// Ore comes out as bars if there is no tank to take it; a bar that is already a bar only
			// melts to go into one, or the pot would burn embers turning iron into the same iron.
			boolean ore = !one.is(stack.getItem());
			int room = this.room(banks, one.getItem(), ore);
			int count = Math.min(stack.getCount(), room / one.getCount());
			if (count <= 0) {
				if (!ore && this.idleReason == JOB_NONE) {
					this.idleReason = JOB_NEEDS_TANK;
					this.idleWhat = what;
				}
				continue;
			}
			return new Pour(one.copyWithCount(one.getCount() * count), null, slot, count, null, List.of(), JOB_MELT, what);
		}
		return null;
	}

	/**
	 * The alloy the two slots and the reachable tanks make together, or null.
	 *
	 * <p>Both slots must be spent by the recipe; the tanks only make up what the slots are missing. The
	 * tanks used to count as though every one of them were in the pot, and a recipe has to use every
	 * input it is given — so the first bar poured into a tank beside the pot was an extra ingredient
	 * that no recipe used, and the crucible stopped after one pour. A foundry with a bank of iron and a
	 * bank of gold on the same pipe could not pour bronze at all.
	 */
	private @Nullable Pour alloy(List<MeltTankBlockEntity> banks) {
		Alloys.Heat heat = this.heat();
		Pour best = null;
		for (int index = 0; index < Alloys.POURABLE.size(); index++) {
			Alloys.Recipe recipe = Alloys.POURABLE.get(index);
			if (!heat.reaches(recipe.heat()) || (best != null && recipe.heat().ordinal() <= best.recipe().heat().ordinal())) {
				continue;
			}
			List<Alloys.Part> needed = new java.util.ArrayList<>(recipe.inputs());
			int[] fromSlots = new int[2];
			boolean ok = true;
			for (int slot : TOP) {
				ItemStack stack = this.items.get(slot);
				if (stack.isEmpty()) {
					continue;
				}
				Alloys.Part paid = null;
				for (Alloys.Part part : needed) {
					if (part.test(stack)) {
						paid = part;
						break;
					}
				}
				if (paid == null) {
					ok = false;
					break;
				}
				needed.remove(paid);
				fromSlots[slot] = paid.count();
			}
			if (!ok) {
				continue;
			}
			List<Draw> draws = new java.util.ArrayList<>();
			for (Alloys.Part part : needed) {
				MeltTankBlockEntity from = null;
				for (MeltTankBlockEntity bank : banks) {
					if (bank.bankMetal() == part.item().get() && bank.bankAmount() >= part.count()
						&& draws.stream().noneMatch(draw -> draw.tank() == bank)) {
						from = bank;
						break;
					}
				}
				if (from == null) {
					ok = false;
					break;
				}
				draws.add(new Draw(from, part.count()));
			}
			if (!ok) {
				continue;
			}
			ItemStack result = recipe.result();
			result.setCount(result.getCount() + this.tier().bonus);
			if (this.room(banks, result.getItem(), true) < result.getCount()) {
				continue;
			}
			best = new Pour(result, recipe, null, 0, fromSlots, draws, JOB_ALLOY, index);
		}
		return best;
	}

	/**
	 * One tank out of every bank the pot reaches whose metal is still liquid, so a bank of four tanks
	 * touching the pot is counted once and not four times.
	 */
	private List<MeltTankBlockEntity> banks() {
		List<MeltTankBlockEntity> found = new java.util.ArrayList<>();
		java.util.Set<BlockPos> seen = new java.util.HashSet<>();
		for (MeltTankBlockEntity tank : this.tanks()) {
			if (seen.contains(tank.getBlockPos())) {
				continue;
			}
			for (MeltTankBlockEntity member : tank.bank()) {
				seen.add(member.getBlockPos());
			}
			if (!tank.isSet()) {
				found.add(tank);
			}
		}
		return found;
	}

	/**
	 * How much of this could go somewhere right now: into banks holding it or holding nothing, and, if
	 * the output slot is allowed, into that.
	 */
	private int room(List<MeltTankBlockEntity> banks, Item item, boolean slotToo) {
		int room = 0;
		for (MeltTankBlockEntity bank : MeltTankBlockEntity.holds(item) ? banks : List.<MeltTankBlockEntity>of()) {
			Item held = bank.bankMetal();
			if (held == null || held == item) {
				room += bank.bankCapacity() - bank.bankAmount();
			}
		}
		if (slotToo) {
			ItemStack out = this.items.get(SLOT_OUTPUT);
			if (out.isEmpty()) {
				room += new ItemStack(item).getMaxStackSize();
			} else if (out.is(item) && out.getComponentsPatch().isEmpty()) {
				room += out.getMaxStackSize() - out.getCount();
			}
		}
		return room;
	}

	/** What melting one forged part or one finished piece of gear gives back at this tier. */
	private ItemStack recovered(ItemStack stack) {
		// A loose part keeps its material in a component of its own, not in forja:parts, so the pot
		// looked at a pick head, saw nothing a smith made, and left it there: only finished gear melted.
		ForgeMaterial single = stack.getItem() instanceof dev.forja.item.PartItem part ? stack.get(ModComponents.MATERIAL) : null;
		if (single != null) {
			int back = Math.round(((dev.forja.item.PartItem) stack.getItem()).type.cost * this.tier().recovery);
			if (back <= 0) {
				return ItemStack.EMPTY;
			}
			ItemStack ingot = single.displayStack();
			ingot.setCount(Math.min(back, ingot.getMaxStackSize()));
			return ingot;
		}
		ForgedParts parts = stack.get(ModComponents.PARTS);
		if (parts == null || parts.materials().isEmpty()) {
			return ItemStack.EMPTY;
		}
		// Everything goes back as the material of its first part, which is the one that names the piece.
		ForgeMaterial material = parts.materials().getFirst();
		int cost = 0;
		List<PartType> needed = parts.type().slots;
		for (int i = 0; i < needed.size() && i < parts.materials().size(); i++) {
			if (parts.materials().get(i) == material) {
				cost += needed.get(i).cost;
			}
		}
		int back = Math.round(cost * this.tier().recovery);
		if (back <= 0) {
			return ItemStack.EMPTY;
		}
		ItemStack ingot = material.displayStack();
		ingot.setCount(Math.min(back, ingot.getMaxStackSize()));
		return ingot;
	}

	private void finish(net.minecraft.world.level.Level level, BlockPos pos, Pour pour) {
		if (pour.recipe() != null) {
			// An alloy eats exactly what the recipe asked for: the slots what they were matched to, and
			// the tanks the rest.
			for (int slot : TOP) {
				this.items.get(slot).shrink(pour.fromSlots()[slot]);
			}
			for (Draw draw : pour.draws()) {
				draw.tank().drain(draw.count());
			}
		} else if (pour.slot() != null) {
			this.items.get(pour.slot()).shrink(pour.used());
		}
		// A tank against the pot takes the pour: a foundry is crucibles emptying into glass, not
		// crucibles filling their own little output slot and stopping when it is full.
		int left = this.pourIntoTank(pour.result());
		if (left > 0) {
			ItemStack out = this.items.get(SLOT_OUTPUT);
			if (out.isEmpty()) {
				this.items.set(SLOT_OUTPUT, pour.result().copyWithCount(left));
			} else {
				out.grow(left);
			}
		}
		// What is left in the bottom of the pot after a tool goes in: enough fire for the next one. Only
		// a tool: ore melting paid for itself this way and a pot of raw iron never needed an ember.
		if (pour.job() == JOB_RECOVER) {
			ItemStack fuel = this.items.get(SLOT_FUEL);
			if (fuel.isEmpty()) {
				this.items.set(SLOT_FUEL, new ItemStack(dev.forja.registry.ModItems.ASCUA));
			} else if (fuel.is(dev.forja.registry.ModItems.ASCUA) && fuel.getCount() < fuel.getMaxStackSize()) {
				fuel.grow(1);
			}
		}
		if (level instanceof ServerLevel server) {
			server.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 1.6F);
			server.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA,
				pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 3, 0.2, 0.1, 0.2, 0.0);
		}
		this.setChanged();
	}

	/**
	 * Pours into the banks the crucible can reach, and says how much would not go.
	 *
	 * <p>Every bank that will take it, not just the first: a pour bigger than what the first bank had
	 * room for used to drop the rest into the pot's own slot even with an empty bank beside it. A bank
	 * that has set takes nothing — hot metal into a cold wall was a wall that half-woke — and nor does a
	 * tank take what is not a metal.
	 */
	private int pourIntoTank(ItemStack result) {
		if (this.level == null || !MeltTankBlockEntity.holds(result.getItem())) {
			return result.getCount();
		}
		int left = result.getCount();
		for (MeltTankBlockEntity tank : this.banks()) {
			if (left <= 0) {
				break;
			}
			// What the run of pipe between here and there took out of it, which is nothing when the
			// glass is built against the pot and a great deal at the end of a long bronze tendril.
			int bled = dev.forja.block.MeltPipeBlock.bleedBetween(this.level, this.worldPosition, tank.getBlockPos());
			left = tank.fill(result.getItem(), left, bled);
		}
		return left;
	}

	/**
	 * Every tank this crucible can reach: the ones it is touching, and the ones on the end of a pipe.
	 *
	 * <p>A pipe is only a connection, so reaching through one is the same as being built against the
	 * glass — which is the point of having them at all.
	 */
	private List<MeltTankBlockEntity> tanks() {
		List<MeltTankBlockEntity> found = new java.util.ArrayList<>();
		if (this.level == null) {
			return found;
		}
		boolean piped = false;
		for (Direction side : Direction.values()) {
			BlockPos at = this.worldPosition.relative(side);
			if (this.level.getBlockEntity(at) instanceof MeltTankBlockEntity tank) {
				found.add(tank);
			} else if (this.level.getBlockState(at).getBlock() instanceof dev.forja.block.MeltPipeBlock) {
				piped = true;
			}
		}
		if (piped) {
			for (BlockPos end : dev.forja.block.MeltPipeBlock.reachable(this.level, this.worldPosition)) {
				if (this.level.getBlockEntity(end) instanceof MeltTankBlockEntity tank && !found.contains(tank)) {
					found.add(tank);
				}
			}
		}
		return found;
	}

	/** The first tank it can reach whose metal has set, if any. */
	private @Nullable MeltTankBlockEntity setTank() {
		for (MeltTankBlockEntity tank : this.tanks()) {
			if (tank.isSet()) {
				return tank;
			}
		}
		return null;
	}

	/** Whether any tank is feeding this pour, which is what halves the time it takes. */
	private boolean fedByTank(Pour pour) {
		return !pour.draws().isEmpty();
	}

	/**
	 * One thing the crucible is about to pour: what comes out, and what it came from — how many out of
	 * which slot, or for an alloy how many out of each slot and which banks make up the rest — and what
	 * to tell the screen about it.
	 */
	private record Pour(ItemStack result, Alloys.@Nullable Recipe recipe, @Nullable Integer slot, int used,
		int @Nullable [] fromSlots, List<Draw> draws, int job, int what) {
	}

	/** What an alloy takes out of one bank. */
	private record Draw(MeltTankBlockEntity tank, int count) {
	}

	// ------------------------------------------------------------------ hands

	/** A full hand: the item goes into the first slot that will take it. */
	public boolean handIn(Player player, ItemStack held) {
		if (held.isEmpty() || !takes(held)) {
			return false;
		}
		int room = this.tier().capacity;
		int inside = this.items.get(SLOT_FIRST).getCount() + this.items.get(SLOT_SECOND).getCount();
		int slot = this.slotFor(held);
		if (slot < 0 || inside >= room) {
			if (player instanceof net.minecraft.server.level.ServerPlayer smith) {
				smith.sendSystemMessage(Component.translatable("gui.forja.crisol.lleno", room));
			}
			return false;
		}
		int moved = Math.min(held.getCount(), room - inside);
		ItemStack there = this.items.get(slot);
		if (there.isEmpty()) {
			this.items.set(slot, held.split(moved));
		} else {
			moved = Math.min(moved, there.getMaxStackSize() - there.getCount());
			there.grow(moved);
			held.shrink(moved);
		}
		this.setChanged();
		return true;
	}

	/** An empty hand: whatever the crucible has poured comes out, and a look at what is inside. */
	public void handOut(Player player) {
		ItemStack out = this.items.get(SLOT_OUTPUT);
		if (!out.isEmpty()) {
			player.getInventory().placeItemBackInInventory(out.copy());
			this.items.set(SLOT_OUTPUT, ItemStack.EMPTY);
			this.setChanged();
			return;
		}
		if (player instanceof net.minecraft.server.level.ServerPlayer smith) {
			smith.sendSystemMessage(this.describe());
		}
	}

	/** What it is holding and what it is doing, for the hand and for Jade. */
	public Component describe() {
		ItemStack first = this.items.get(SLOT_FIRST);
		ItemStack second = this.items.get(SLOT_SECOND);
		if (first.isEmpty() && second.isEmpty()) {
			return Component.translatable("gui.forja.crisol.vacio", this.heat().displayName());
		}
		Component load = first.isEmpty() ? second.getHoverName()
			: second.isEmpty() ? first.getHoverName()
			: Component.translatable("gui.forja.crisol.dos", first.getHoverName(), second.getHoverName());
		return Component.translatable("gui.forja.crisol.dentro", load, this.progressPercent());
	}

	public int progressPercent() {
		return Math.round(100.0F * this.progress / this.tier().cook);
	}

	public boolean isLit() {
		return this.burning > 0;
	}

	private int slotFor(ItemStack stack) {
		for (int slot : TOP) {
			ItemStack there = this.items.get(slot);
			if (!there.isEmpty() && ItemStack.isSameItemSameComponents(there, stack) && there.getCount() < there.getMaxStackSize()) {
				return slot;
			}
		}
		for (int slot : TOP) {
			if (this.items.get(slot).isEmpty()) {
				return slot;
			}
		}
		return -1;
	}

	// ------------------------------------------------------------------ the screen

	/** The six numbers the screen draws itself from. */
	private final net.minecraft.world.inventory.ContainerData data = new net.minecraft.world.inventory.ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case dev.forja.menu.CrucibleMenu.DATA_PROGRESS -> CrucibleBlockEntity.this.progress;
				case dev.forja.menu.CrucibleMenu.DATA_COOK -> CrucibleBlockEntity.this.tier().cook;
				case dev.forja.menu.CrucibleMenu.DATA_BURNING -> CrucibleBlockEntity.this.burning;
				case dev.forja.menu.CrucibleMenu.DATA_BURN_LENGTH -> CrucibleBlockEntity.this.burnLength;
				case dev.forja.menu.CrucibleMenu.DATA_HEAT -> CrucibleBlockEntity.this.heat().ordinal();
				case dev.forja.menu.CrucibleMenu.DATA_CAPACITY -> CrucibleBlockEntity.this.tier().capacity;
				// What it is doing, as the server sees it: the screen only has the two slots to go on, and
				// it could not see the tanks, the ore or the reason a pot was standing idle.
				case dev.forja.menu.CrucibleMenu.DATA_JOB -> CrucibleBlockEntity.this.job;
				case dev.forja.menu.CrucibleMenu.DATA_WHAT -> CrucibleBlockEntity.this.jobWhat;
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
		}

		@Override
		public int getCount() {
			return dev.forja.menu.CrucibleMenu.DATA_SIZE;
		}
	};

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.forja." + this.tier().id());
	}

	@Override
	public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inventory, Player player) {
		return new dev.forja.menu.CrucibleMenu(id, inventory, this, this.data);
	}

	// ------------------------------------------------------------------ container

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
	public ItemStack removeItem(int slot, int amount) {
		ItemStack taken = ContainerHelper.removeItem(this.items, slot, amount);
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
		stack.limitSize(this.getMaxStackSize(stack));
		this.setChanged();
	}

	@Override
	public boolean stillValid(Player player) {
		return this.level != null && this.level.getBlockEntity(this.worldPosition) == this
			&& player.distanceToSqr(this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.5, this.worldPosition.getZ() + 0.5) <= 64.0;
	}

	@Override
	public void clearContent() {
		this.items.clear();
		this.setChanged();
	}

	/**
	 * Breaking the pot spills what is in it. The block used to do this in affectNeighborsAfterRemoval,
	 * which the game only calls once this block entity is gone, so a broken crucible took its ore, its
	 * embers and its pour with it.
	 */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (this.level != null) {
			net.minecraft.world.Containers.dropContents(this.level, pos, this);
		}
	}

	@Override
	public int[] getSlotsForFace(Direction side) {
		return switch (side) {
			case DOWN -> BOTTOM;
			case UP -> TOP;
			default -> SIDES;
		};
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
		return this.canPlaceItem(slot, stack);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
		return slot == SLOT_OUTPUT;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		if (slot == SLOT_OUTPUT) {
			return false;
		}
		if (slot == SLOT_FUEL) {
			return stack.is(dev.forja.registry.ModItems.ASCUA);
		}
		// The capacity is the point of the tiers, so a hopper cannot walk around it; and what goes in has
		// to be something the pot can do anything with.
		int inside = this.items.get(SLOT_FIRST).getCount() + this.items.get(SLOT_SECOND).getCount();
		return inside < this.tier().capacity && takes(stack);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, this.items);
		this.burning = input.getIntOr("Burning", 0);
		this.burnLength = input.getIntOr("BurnLength", 0);
		this.progress = input.getIntOr("Progress", 0);
		// Only ever present in what the server sends a client; a saved pot has no such key and works it out.
		this.shownColour = input.getIntOr("Colour", -1);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, this.items);
		output.putInt("Burning", this.burning);
		output.putInt("BurnLength", this.burnLength);
		output.putInt("Progress", this.progress);
	}
}
