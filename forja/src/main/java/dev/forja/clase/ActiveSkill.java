package dev.forja.clase;

import java.util.Locale;
import java.util.function.Supplier;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The three active skills of every class: the first comes with the class (key V), the second and the third are
 * nodes of its tree (keys B and N), and each has a II that is a node too (docs/ARBOLES.md). What each one does is
 * in {@link ClassSkills}; its numbers and its wait are in the tree's file ({@link ClassTree.SkillDef}), where the
 * tooltips read them too. The II replaces the numbers whole: {@link #numbers(Player)} is one or the other.
 */
public enum ActiveSkill {
	GRITO_DE_GUERRA("guerrero", () -> new ItemStack(Items.GOAT_HORN)),
	POSTURA_DE_HIERRO("guerrero", () -> new ItemStack(Items.IRON_BLOCK)),
	TORBELLINO("guerrero", () -> new ItemStack(Items.IRON_AXE)),
	PASO_SOMBRIO("asesino", () -> new ItemStack(Items.INK_SAC)),
	MARCA_DE_MUERTE("asesino", () -> new ItemStack(Items.WITHER_ROSE)),
	ABANICO_DE_DAGAS("asesino", () -> new ItemStack(Items.IRON_SWORD)),
	PROVOCAR("tanque", () -> new ItemStack(Items.BELL)),
	BALUARTE("tanque", () -> new ItemStack(Items.NETHERITE_CHESTPLATE)),
	EMBESTIDA_DE_ESCUDO("tanque", () -> new ItemStack(Items.SHIELD)),
	NOVA_ARCANA("mago", () -> new ItemStack(Items.FIRE_CHARGE)),
	CONCENTRACION("mago", () -> new ItemStack(Items.ENDER_EYE)),
	METEORO("mago", () -> new ItemStack(Items.MAGMA_BLOCK)),
	PULSO_SANADOR("curandero", () -> new ItemStack(Items.GLISTERING_MELON_SLICE)),
	RESURGIR("curandero", () -> new ItemStack(Items.TOTEM_OF_UNDYING)),
	ESCUDO_DE_LUZ("curandero", () -> new ItemStack(Items.GOLDEN_APPLE)),
	SALTO_ATRAS("arquero", () -> new ItemStack(Items.RABBIT_FOOT)),
	LLUVIA_DE_FLECHAS("arquero", () -> new ItemStack(Items.TIPPED_ARROW)),
	FLECHA_DE_RED("arquero", () -> new ItemStack(Items.COBWEB));

	/**
	 * Whose skill it is, by id: PlayerClass names its first skill from here, so naming the class back would make
	 * whichever enum loads second see the other half-built.
	 */
	private final String owner;
	private final Supplier<ItemStack> icon;

	ActiveSkill(String owner, Supplier<ItemStack> icon) {
		this.owner = owner;
		this.icon = icon;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	public static ActiveSkill byId(String id) {
		return valueOf(id.toUpperCase(Locale.ROOT));
	}

	public PlayerClass owner() {
		return PlayerClass.byId(this.owner);
	}

	/** Its numbers and its wait, as the tree's file has them. */
	public ClassTree.SkillDef def() {
		ClassTree.Tree tree = ClassTree.tree(this.owner());
		for (int key = 1; key <= 3; key++) {
			if (tree.skill(key).id.equals(this.id())) {
				return tree.skill(key);
			}
		}
		throw new IllegalStateException(this + " is not in " + this.owner().id() + "'s tree");
	}

	/** The key it is on: 1 V, 2 B, 3 N. */
	public int key() {
		ClassTree.Tree tree = ClassTree.tree(this.owner());
		for (int key = 1; key <= 3; key++) {
			if (tree.skill(key).id.equals(this.id())) {
				return key;
			}
		}
		return 1;
	}

	/** Whether this player has its II. */
	public boolean upgraded(@Nullable Player player) {
		ClassTree.Slot slot = switch (this.key()) {
			case 1 -> ClassTree.Slot.V2;
			case 2 -> ClassTree.Slot.B2;
			default -> ClassTree.Slot.N2;
		};
		ClassTree.Node node = ClassTree.tree(this.owner()).bySlot(slot);
		return node != null && ClassProgress.data(player).has(node.id);
	}

	/** The numbers this player casts it with: the II's once learned. */
	public float[] numbers(@Nullable Player player) {
		return this.def().numbers(this.upgraded(player));
	}

	/** Seconds of wait for this player, before any keystone that stretches it (ClassEffects.skillCooldownMultiplier). */
	public int cooldownSeconds(@Nullable Player player) {
		return this.def().cooldown(this.upgraded(player));
	}

	public int cooldownTicks(@Nullable Player player) {
		return Math.round(this.cooldownSeconds(player) * 20.0F * ClassEffects.skillCooldownMultiplier(player, this));
	}

	/** A number in whole ticks: {@code numbers[i]} seconds. */
	public static int ticks(float seconds) {
		return Math.round(seconds * 20.0F);
	}

	public ItemStack icon() {
		return this.icon.get();
	}

	public Component displayName() {
		return Component.translatable("gui.forja.habilidad." + this.id());
	}

	/** Its name as this player has it: with " II" once upgraded. */
	public Component displayName(@Nullable Player player) {
		return Component.translatable("gui.forja.habilidad." + this.id() + (this.upgraded(player) ? ".ii" : ""));
	}
}
