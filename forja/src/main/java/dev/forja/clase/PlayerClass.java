package dev.forja.clase;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.material.ForgeMaterial;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import static dev.forja.clase.ClassStat.*;

/**
 * The six classes (docs/CLASES.md). A class is its base numbers, the active skill it comes with, and its big
 * tree ({@link ClassTree}, docs/ARBOLES.md), which holds its other two skills. The order here is the order of the
 * choice screen, and the ids are what the attachment saves, so a class is never renamed. There was a seventh, the
 * Herrero: Andy (2026-09-30) took it out and put the forge in every tree; a save that still says "herrero"
 * reads as no class (ClassProgress.migrate).
 */
public enum PlayerClass {
	/** Melee and endurance: stamina, posture, parries. */
	GUERRERO(0xC0463A, () -> Assembler.create(ForgeType.ESPADA, List.of(ForgeMaterial.HIERRO, ForgeMaterial.MADERA, ForgeMaterial.HIERRO)),
		List.of(MAX_HEALTH.of(0.10F), MELEE_DAMAGE.of(0.05F), STAMINA_MAX.of(0.20F), STAMINA_REGEN.of(0.10F), POSTURE.of(0.15F), PARRY_WINDOW.of(1)),
		ActiveSkill.GRITO_DE_GUERRA),
	/** Andy: "más stamina, más esquive, un poquito más de daño, bastante menos vida". */
	ASESINO(0x8A6BC8, () -> Assembler.create(ForgeType.DAGA, List.of(ForgeMaterial.ACERO, ForgeMaterial.CUERO)),
		List.of(MAX_HEALTH.of(-0.30F), MELEE_DAMAGE.of(0.10F), MOVE_SPEED.of(0.05F), STAMINA_MAX.of(0.30F), DODGE_DISTANCE.of(0.35F),
			DODGE_COOLDOWN.of(-0.30F), DODGE_COST.of(-0.20F), DODGE_IFRAMES.of(2)),
		ActiveSkill.PASO_SOMBRIO),
	/** Andy: "mucha vida, stamina normal, menos movilidad, esquive más reducido, más lento". */
	TANQUE(0x8C99A6, () -> new ItemStack(Items.SHIELD),
		List.of(MAX_HEALTH.of(0.60F), ARMOR.of(2), KNOCKBACK.of(0.30F), MOVE_SPEED.of(-0.12F), DODGE_DISTANCE.of(-0.35F),
			DODGE_COST.of(0.20F), BLOCK_COST.of(-0.25F)),
		ActiveSkill.PROVOCAR),
	/**
	 * Magic: the staff and the tome, harder and sooner, for health and stamina. Andy (2026-09-30): mana comes back
	 * "lentísimo si no tienes la clase", so the Mago's bar refills six times as fast as anyone else's.
	 */
	MAGO(0x4F7FE8, () -> Assembler.create(ForgeType.BACULO, List.of(ForgeMaterial.AMATISTA, ForgeMaterial.ORO, ForgeMaterial.MADERA)),
		List.of(MAX_HEALTH.of(-0.10F), STAMINA_MAX.of(-0.10F), SPELL_DAMAGE.of(0.10F), SPELL_COOLDOWN.of(-0.10F),
			MANA_MAX.of(0.25F), MANA_REGEN.of(5.0F)),
		ActiveSkill.NOVA_ARCANA),
	/**
	 * Andy: "cambia el daño de las armas mágicas por curación (sólo cura 1/10 parte del daño)", and later
	 * (2026-09-29): its magic hurts foes at a third and still mends allies. The rule lives in magic/Healing and
	 * the damage factors (melee x0.5, magic x1/3) in {@link ClassDamage}; here are only its other numbers. Its
	 * mana comes back four times as fast as a player's without a magic class (Andy, 2026-09-30).
	 */
	CURANDERO(0x5CC46A, () -> Assembler.create(ForgeType.FAROL, List.of(ForgeMaterial.ESMERALDA, ForgeMaterial.ORO, ForgeMaterial.MADERA)),
		List.of(HEALING.of(0.50F), MANA_MAX.of(0.15F), MANA_REGEN.of(3.0F), STAMINA_REGEN.of(0.10F)),
		ActiveSkill.PULSO_SANADOR),
	/** Bows and crossbows, mobility, dodge. */
	ARQUERO(0x8DBF4A, () -> Assembler.create(ForgeType.ARCO, Assembler.defaultMaterials(ForgeType.ARCO)),
		List.of(MAX_HEALTH.of(-0.10F), MOVE_SPEED.of(0.08F), PROJECTILE_DAMAGE.of(0.15F), DRAW_SPEED.of(0.10F), DODGE_DISTANCE.of(0.20F),
			DODGE_COOLDOWN.of(-0.15F), FALL_DAMAGE.of(-0.25F)),
		ActiveSkill.SALTO_ATRAS);

	public final int color;
	private final Supplier<ItemStack> icon;
	public final List<ClassStat.Mod> base;
	/** The skill the class comes with, on the first key. The second is a node of the tree. */
	public final ActiveSkill firstSkill;

	PlayerClass(int color, Supplier<ItemStack> icon, List<ClassStat.Mod> base, ActiveSkill firstSkill) {
		this.color = color;
		this.icon = icon;
		this.base = base;
		this.firstSkill = firstSkill;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	public static @Nullable PlayerClass byId(@Nullable String id) {
		if (id == null || id.isEmpty()) {
			return null;
		}
		for (PlayerClass value : values()) {
			if (value.id().equals(id)) {
				return value;
			}
		}
		return null;
	}

	public ItemStack icon() {
		return this.icon.get();
	}

	public Component displayName() {
		return Component.translatable("gui.forja.clase." + this.id()).withColor(0xFF000000 | this.color);
	}

	public Component description() {
		return Component.translatable("gui.forja.clase." + this.id() + ".desc");
	}

	/** The class's own number for one stat, 0 if the class does not touch it. */
	public float base(ClassStat stat) {
		float total = 0.0F;
		for (ClassStat.Mod mod : this.base) {
			if (mod.stat() == stat) {
				total += mod.value();
			}
		}
		return total;
	}

	/** What this class does to the damage of one kind of blow, multiplied on top (Andy's factors, {@link ClassDamage}). */
	public float damageFactor(ClassDamage.Blow blow) {
		return ClassDamage.factor(this, blow);
	}

	/** This class's tree. */
	public ClassTree.Tree tree() {
		return ClassTree.tree(this);
	}

	/** The skill on the second key (B), a node of the tree. */
	public ActiveSkill secondSkill() {
		return ActiveSkill.byId(this.tree().skill(2).id);
	}

	/** The skill on the third key (N), deep in the tree. */
	public ActiveSkill thirdSkill() {
		return ActiveSkill.byId(this.tree().skill(3).id);
	}

	/** The skill on a key: 1 V, 2 B, 3 N. */
	public ActiveSkill skill(int key) {
		return key == 1 ? this.firstSkill : key == 2 ? this.secondSkill() : this.thirdSkill();
	}
}
