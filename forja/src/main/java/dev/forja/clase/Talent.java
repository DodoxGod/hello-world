package dev.forja.clase;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import static dev.forja.clase.ClassStat.*;

/**
 * The talent trees (docs/CLASES.md): three branches of three nodes per class and a last node that is the
 * second active skill. A node of level 2 or 3 needs the one above it in its branch; the skill node needs
 * any node of level 2. They cost 1, 2 and 3 points, and the skill 3.
 *
 * <p>A node is its numbers ({@link #mods}, which {@link ClassEffects} adds up) and, for the few that do
 * something a number cannot say, its {@link #numbers}: what the effect text prints and what the code that
 * does it reads, from the same place, so the tooltip cannot drift from the game.
 *
 * <p>Saved as bits of a mask by their place within their class, so a node is never moved or removed; new
 * ones go after the skill node of their class.
 */
public enum Talent {
	// ---------------------------------------------------------------- Guerrero
	GUERRERO_SEGUNDO_ALIENTO(PlayerClass.GUERRERO, 0, 1, () -> new ItemStack(Items.RABBIT_FOOT), List.of(STAMINA_REGEN.of(0.20F))),
	GUERRERO_PIEL_CURTIDA(PlayerClass.GUERRERO, 0, 2, () -> new ItemStack(Items.LEATHER), List.of(MAX_HEALTH.of(0.10F), KNOCKBACK.of(0.10F))),
	/** Under {@code numbers[0]} of your health, blows take {@code numbers[1]} less. */
	GUERRERO_INQUEBRANTABLE(PlayerClass.GUERRERO, 0, 3, () -> new ItemStack(Items.OBSIDIAN), List.of(), 0.30F, 0.25F),
	GUERRERO_GUARDIA_ALTA(PlayerClass.GUERRERO, 1, 1, () -> new ItemStack(Items.SHIELD), List.of(PARRY_WINDOW.of(2))),
	GUERRERO_PARADA_FIRME(PlayerClass.GUERRERO, 1, 2, () -> new ItemStack(Items.IRON_BLOCK), List.of(BLOCK_COST.of(-0.35F))),
	/** A parry gives back {@code numbers[0]} stamina, and the next blow within {@code numbers[1]} seconds does {@code numbers[2]} more. */
	GUERRERO_REPLICA(PlayerClass.GUERRERO, 1, 3, () -> new ItemStack(Items.IRON_SWORD), List.of(), 15.0F, 3.0F, 0.40F),
	GUERRERO_GOLPE_PESADO(PlayerClass.GUERRERO, 2, 1, () -> new ItemStack(Items.MACE), List.of(POSTURE.of(0.20F))),
	GUERRERO_ROMPEGUARDIAS(PlayerClass.GUERRERO, 2, 2, () -> new ItemStack(Items.CRACKED_STONE_BRICKS), List.of(STAGGERED_BONUS.of(0.25F))),
	GUERRERO_VERDUGO(PlayerClass.GUERRERO, 2, 3, () -> new ItemStack(Items.WITHER_SKELETON_SKULL), List.of(FINISHER.of(0.40F))),
	GUERRERO_POSTURA_DE_HIERRO(PlayerClass.GUERRERO, ActiveSkill.POSTURA_DE_HIERRO),

	// ---------------------------------------------------------------- Asesino
	ASESINO_PIES_LIGEROS(PlayerClass.ASESINO, 0, 1, () -> new ItemStack(Items.FEATHER), List.of(DODGE_COST.of(-0.20F))),
	ASESINO_CONTRAATAQUE(PlayerClass.ASESINO, 0, 2, () -> new ItemStack(Items.ARROW), List.of(COUNTER.of(0.50F))),
	ASESINO_DANZA(PlayerClass.ASESINO, 0, 3, () -> new ItemStack(Items.PHANTOM_MEMBRANE), List.of(DODGE_IFRAMES.of(2), DODGE_COOLDOWN.of(-0.20F))),
	ASESINO_PUNALADA(PlayerClass.ASESINO, 1, 1, () -> new ItemStack(Items.IRON_SWORD), List.of(BACKSTAB.of(0.40F))),
	ASESINO_EJECUTOR(PlayerClass.ASESINO, 1, 2, () -> new ItemStack(Items.SKELETON_SKULL), List.of(EXECUTE.of(0.30F))),
	/** A melee kill gives back {@code numbers[0]} stamina and Speed II for {@code numbers[1]} seconds. */
	ASESINO_GOLPE_LETAL(PlayerClass.ASESINO, 1, 3, () -> new ItemStack(Items.REDSTONE), List.of(), 25.0F, 3.0F),
	ASESINO_PASO_QUEDO(PlayerClass.ASESINO, 2, 1, () -> new ItemStack(Items.LEATHER_BOOTS), List.of(SNEAK_SPEED.of(0.30F))),
	ASESINO_ACROBATA(PlayerClass.ASESINO, 2, 2, () -> new ItemStack(Items.SLIME_BALL), List.of(FALL_DAMAGE.of(-0.40F), JUMP.of(0.10F))),
	/** A projectile has {@code numbers[0]} chance of doing nothing at all. */
	ASESINO_EVASION(PlayerClass.ASESINO, 2, 3, () -> new ItemStack(Items.ENDER_PEARL), List.of(), 0.15F),
	ASESINO_MARCA_DE_MUERTE(PlayerClass.ASESINO, ActiveSkill.MARCA_DE_MUERTE),

	// ---------------------------------------------------------------- Tanque
	TANQUE_ESCUDO_PESADO(PlayerClass.TANQUE, 0, 1, () -> new ItemStack(Items.SHIELD), List.of(BLOCK_COST.of(-0.25F))),
	/** Whoever strikes your raised shield takes {@code numbers[0]}. */
	TANQUE_REPRESALIA(PlayerClass.TANQUE, 0, 2, () -> new ItemStack(Items.CACTUS), List.of(), 3.0F),
	TANQUE_BASTION(PlayerClass.TANQUE, 0, 3, () -> new ItemStack(Items.STONE_BRICK_WALL), List.of(PARRY_WINDOW.of(3))),
	TANQUE_PIEL_DE_HIERRO(PlayerClass.TANQUE, 1, 1, () -> new ItemStack(Items.IRON_CHESTPLATE), List.of(ARMOR.of(2))),
	TANQUE_DUREZA(PlayerClass.TANQUE, 1, 2, () -> new ItemStack(Items.DIAMOND_CHESTPLATE), List.of(TOUGHNESS.of(2), DAMAGE_TAKEN.of(-0.10F))),
	TANQUE_COLOSO(PlayerClass.TANQUE, 1, 3, () -> new ItemStack(Items.GOLDEN_APPLE), List.of(MAX_HEALTH.of(0.20F))),
	/** {@code numbers[0]} health every {@code numbers[1]} seconds. */
	TANQUE_RECUPERACION(PlayerClass.TANQUE, 2, 1, () -> new ItemStack(Items.GLISTERING_MELON_SLICE), List.of(), 1.0F, 5.0F),
	TANQUE_RAICES(PlayerClass.TANQUE, 2, 2, () -> new ItemStack(Items.OAK_SAPLING), List.of(KNOCKBACK.of(0.30F), STAMINA_MAX.of(0.10F))),
	/** Once every {@code numbers[0]} minutes, a lethal blow leaves you at 1 with Resistance III for {@code numbers[1]} seconds. */
	TANQUE_ULTIMO_BASTION(PlayerClass.TANQUE, 2, 3, () -> new ItemStack(Items.TOTEM_OF_UNDYING), List.of(), 5.0F, 3.0F),
	TANQUE_BALUARTE(PlayerClass.TANQUE, ActiveSkill.BALUARTE),

	// ---------------------------------------------------------------- Mago
	MAGO_NUCLEO_AFINADO(PlayerClass.MAGO, 0, 1, () -> new ItemStack(Items.AMETHYST_SHARD), List.of(SPELL_DAMAGE.of(0.10F))),
	MAGO_SOBRECARGA_ARCANA(PlayerClass.MAGO, 0, 2, () -> new ItemStack(Items.REDSTONE_BLOCK), List.of(CHARGE_BONUS.of(0.25F))),
	MAGO_CATALIZADOR(PlayerClass.MAGO, 0, 3, () -> new ItemStack(Items.END_CRYSTAL), List.of(SPELL_DAMAGE.of(0.10F))),
	MAGO_MENTE_CLARA(PlayerClass.MAGO, 1, 1, () -> new ItemStack(Items.GLOWSTONE_DUST), List.of(SPELL_COOLDOWN.of(-0.05F), MANA_REGEN.of(1.0F))),
	MAGO_CANALIZACION(PlayerClass.MAGO, 1, 2, () -> new ItemStack(Items.LAPIS_LAZULI), List.of(SPELL_CHARGE.of(-0.25F), MANA_MAX.of(0.30F))),
	MAGO_ECONOMIA(PlayerClass.MAGO, 1, 3, () -> new ItemStack(Items.EXPERIENCE_BOTTLE), List.of(SPELL_COST.of(-0.25F))),
	MAGO_BARRERA(PlayerClass.MAGO, 2, 1, () -> new ItemStack(Items.GLASS), List.of(MAGIC_TAKEN.of(-0.25F))),
	MAGO_PASO_ETEREO(PlayerClass.MAGO, 2, 2, () -> new ItemStack(Items.CHORUS_FRUIT), List.of(DODGE_DISTANCE.of(0.20F), DODGE_COOLDOWN.of(-0.10F))),
	/** Every {@code numbers[0]} seconds, {@code numbers[1]} absorption. */
	MAGO_EGIDA(PlayerClass.MAGO, 2, 3, () -> new ItemStack(Items.HEART_OF_THE_SEA), List.of(), 30.0F, 4.0F),
	MAGO_CONCENTRACION(PlayerClass.MAGO, ActiveSkill.CONCENTRACION),

	// ---------------------------------------------------------------- Curandero
	CURANDERO_MANOS_CALIDAS(PlayerClass.CURANDERO, 0, 1, () -> new ItemStack(Items.BLAZE_POWDER), List.of(HEALING.of(0.20F))),
	/** What you heal also gets Regeneration I for {@code numbers[0]} seconds. */
	CURANDERO_RENUEVO(PlayerClass.CURANDERO, 0, 2, () -> new ItemStack(Items.GHAST_TEAR), List.of(), 3.0F),
	CURANDERO_MILAGRO(PlayerClass.CURANDERO, 0, 3, () -> new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), List.of(HEALING.of(0.30F))),
	/** A heal on someone under {@code numbers[0]} of their health gives Resistance I for {@code numbers[1]} seconds. */
	CURANDERO_BENDICION(PlayerClass.CURANDERO, 1, 1, () -> new ItemStack(Items.GOLDEN_CARROT), List.of(), 0.50F, 4.0F),
	CURANDERO_PURIFICAR(PlayerClass.CURANDERO, 1, 2, () -> new ItemStack(Items.MILK_BUCKET), List.of()),
	/** You heal {@code numbers[0]} of what you heal others. */
	CURANDERO_VINCULO(PlayerClass.CURANDERO, 1, 3, () -> new ItemStack(Items.LEAD), List.of(), 0.25F),
	CURANDERO_SERENIDAD(PlayerClass.CURANDERO, 2, 1, () -> new ItemStack(Items.LILY_OF_THE_VALLEY), List.of(MANA_REGEN.of(1.0F), STAMINA_REGEN.of(0.15F))),
	CURANDERO_VOLUNTAD(PlayerClass.CURANDERO, 2, 2, () -> new ItemStack(Items.APPLE), List.of(MAX_HEALTH.of(0.15F))),
	/** You and your allies within {@code numbers[0]} blocks get {@code numbers[1]} health every {@code numbers[2]} seconds. */
	CURANDERO_AURA(PlayerClass.CURANDERO, 2, 3, () -> new ItemStack(Items.BEACON), List.of(), 6.0F, 1.0F, 6.0F),
	CURANDERO_RESURGIR(PlayerClass.CURANDERO, ActiveSkill.RESURGIR),

	// ---------------------------------------------------------------- Arquero
	ARQUERO_OJO_DE_HALCON(PlayerClass.ARQUERO, 0, 1, () -> new ItemStack(Items.SPYGLASS), List.of(PROJECTILE_DAMAGE.of(0.10F))),
	ARQUERO_TIRO_A_LA_CABEZA(PlayerClass.ARQUERO, 0, 2, () -> new ItemStack(Items.TARGET), List.of(HEADSHOT.of(0.25F))),
	/** {@code numbers[1]} more per block past {@code numbers[0]}, up to {@code numbers[2]}. */
	ARQUERO_TIRO_LEJANO(PlayerClass.ARQUERO, 0, 3, () -> new ItemStack(Items.SPECTRAL_ARROW), List.of(), 10.0F, 0.02F, 0.40F),
	ARQUERO_MANO_RAPIDA(PlayerClass.ARQUERO, 1, 1, () -> new ItemStack(Items.STRING), List.of(DRAW_SPEED.of(0.15F))),
	ARQUERO_FLECHA_VELOZ(PlayerClass.ARQUERO, 1, 2, () -> new ItemStack(Items.ARROW), List.of(ARROW_SPEED.of(0.20F))),
	/** A full-draw hit slows the target (Slowness II) for {@code numbers[0]} seconds. */
	ARQUERO_TIRO_CERTERO(PlayerClass.ARQUERO, 1, 3, () -> new ItemStack(Items.COBWEB), List.of(), 2.0F),
	ARQUERO_ZANCADA(PlayerClass.ARQUERO, 2, 1, () -> new ItemStack(Items.SUGAR), List.of(MOVE_SPEED.of(0.05F))),
	ARQUERO_RODAR(PlayerClass.ARQUERO, 2, 2, () -> new ItemStack(Items.RABBIT_HIDE), List.of(DODGE_COST.of(-0.25F), DODGE_IFRAMES.of(1))),
	ARQUERO_PLUMA(PlayerClass.ARQUERO, 2, 3, () -> new ItemStack(Items.FEATHER), List.of(FALL_DAMAGE.of(-0.50F), JUMP.of(0.15F))),
	ARQUERO_LLUVIA_DE_FLECHAS(PlayerClass.ARQUERO, ActiveSkill.LLUVIA_DE_FLECHAS),

	// ---------------------------------------------------------------- Herrero
	HERRERO_PULSO(PlayerClass.HERRERO, 0, 1, () -> new ItemStack(Items.ANVIL), List.of(FORGE_WINDOW.of(0.01F))),
	HERRERO_GOLPE_MAESTRO(PlayerClass.HERRERO, 0, 2, () -> new ItemStack(Items.SMITHING_TABLE), List.of(FORGE_WINDOW.of(0.01F), POTENTIAL.of(5))),
	HERRERO_MARTILLO_DE_ORO(PlayerClass.HERRERO, 0, 3, () -> new ItemStack(Items.GOLD_BLOCK), List.of(FORGE_WINDOW.of(0.02F))),
	HERRERO_OJO_DE_METAL(PlayerClass.HERRERO, 1, 1, () -> new ItemStack(Items.RAW_IRON), List.of(POTENTIAL.of(5))),
	HERRERO_MANO_FIRME(PlayerClass.HERRERO, 1, 2, () -> new ItemStack(Items.GRINDSTONE), List.of(UPGRADE_BONUS.of(1))),
	HERRERO_ALMA_DEL_METAL(PlayerClass.HERRERO, 1, 3, () -> new ItemStack(Items.NETHERITE_INGOT), List.of(POTENTIAL.of(10))),
	HERRERO_REMIENDO(PlayerClass.HERRERO, 2, 1, () -> new ItemStack(Items.IRON_INGOT), List.of(REPAIR.of(0.25F))),
	HERRERO_BRAZO_DE_HERRERO(PlayerClass.HERRERO, 2, 2, () -> new ItemStack(Items.IRON_AXE), List.of(SMITH_WEAPON.of(0.15F))),
	HERRERO_PIEL_DE_FRAGUA(PlayerClass.HERRERO, 2, 3, () -> new ItemStack(Items.MAGMA_CREAM), List.of(FIRE_TAKEN.of(-0.50F), BURNING.of(-0.50F))),
	HERRERO_FORJA_AL_ROJO(PlayerClass.HERRERO, ActiveSkill.FORJA_AL_ROJO);

	/** Branch number of the skill node, which sits under the three branches. */
	public static final int SKILL_BRANCH = 3;
	/** Points per level of a branch node, and the skill node. */
	public static final int[] COST = {0, 1, 2, 3};
	public static final int SKILL_COST = 3;

	private static final Map<PlayerClass, List<Talent>> BY_CLASS = new EnumMap<>(PlayerClass.class);

	static {
		for (Talent talent : values()) {
			BY_CLASS.computeIfAbsent(talent.owner, c -> new ArrayList<>()).add(talent);
		}
	}

	public final PlayerClass owner;
	/** 0 to 2, or {@link #SKILL_BRANCH}. */
	public final int branch;
	/** 1 to 3; the skill node counts as 4. */
	public final int tier;
	private final Supplier<ItemStack> icon;
	public final List<ClassStat.Mod> mods;
	public final float[] numbers;
	public final @Nullable ActiveSkill skill;

	Talent(PlayerClass owner, int branch, int tier, Supplier<ItemStack> icon, List<ClassStat.Mod> mods, float... numbers) {
		this.owner = owner;
		this.branch = branch;
		this.tier = tier;
		this.icon = icon;
		this.mods = mods;
		this.numbers = numbers;
		this.skill = null;
	}

	Talent(PlayerClass owner, ActiveSkill skill) {
		this.owner = owner;
		this.branch = SKILL_BRANCH;
		this.tier = 4;
		this.icon = skill::icon;
		this.mods = List.of();
		this.numbers = new float[0];
		this.skill = skill;
	}

	public static List<Talent> of(PlayerClass owner) {
		return BY_CLASS.getOrDefault(owner, List.of());
	}

	/** Its bit in the saved mask: its place among its class's talents. */
	public int index() {
		return of(this.owner).indexOf(this);
	}

	public static @Nullable Talent byIndex(PlayerClass owner, int index) {
		List<Talent> list = of(owner);
		return index >= 0 && index < list.size() ? list.get(index) : null;
	}

	public static @Nullable Talent byId(String id) {
		for (Talent talent : values()) {
			if (talent.id().equals(id)) {
				return talent;
			}
		}
		return null;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	public int cost() {
		return this.skill != null ? SKILL_COST : COST[this.tier];
	}

	public ItemStack icon() {
		return this.icon.get();
	}

	public boolean isSkill() {
		return this.skill != null;
	}

	/** The node above this one in its branch, which it needs; null for the first of a branch and for the skill. */
	public @Nullable Talent parent() {
		if (this.skill != null || this.tier <= 1) {
			return null;
		}
		for (Talent other : of(this.owner)) {
			if (other.branch == this.branch && other.tier == this.tier - 1) {
				return other;
			}
		}
		return null;
	}

	/** The skill node: any node of this level unlocks it. */
	public static final int SKILL_NEEDS_TIER = 2;

	/** Whether what is already learned lets this one be taken (points aside). */
	public boolean prerequisitesMet(int mask) {
		if (this.skill != null) {
			for (Talent other : of(this.owner)) {
				if (other.skill == null && other.tier >= SKILL_NEEDS_TIER && (mask & 1 << other.index()) != 0) {
					return true;
				}
			}
			return false;
		}
		Talent parent = this.parent();
		return parent == null || (mask & 1 << parent.index()) != 0;
	}

	public Component displayName() {
		return Component.translatable("gui.forja.talento." + this.id());
	}

	public Component branchName() {
		return Component.translatable("gui.forja.clase.rama." + this.owner.id() + "." + this.branch);
	}

	/** Every line of what it does, numbers first, then the effect in words for the nodes that have one. */
	public List<Component> effectLines() {
		List<Component> lines = new ArrayList<>();
		for (ClassStat.Mod mod : this.mods) {
			lines.add(mod.line());
		}
		if (this.skill != null) {
			lines.addAll(this.skill.lines());
		} else if (this.mods.isEmpty()) {
			Object[] args = new Object[this.numbers.length];
			for (int i = 0; i < this.numbers.length; i++) {
				args[i] = shown(this.numbers[i]);
			}
			lines.add(Component.translatable("gui.forja.talento." + this.id() + ".efecto", args).withColor(0xFF7FD34E));
		}
		return lines;
	}

	/** A number as the texts print it: shares as percentages, the rest as they are. */
	static String shown(float value) {
		if (value > 0.0F && value < 1.0F) {
			return Math.round(value * 100.0F) + " %";
		}
		return value == Math.rint(value) ? Integer.toString(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value).replace('.', ',');
	}
}
