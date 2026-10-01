package dev.forja.clase;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The active skills of every class: the first comes with the class (key V), the second is a node of its tree (key
 * B), and three ultimates end its three sendas, of which the player learns one (key N); each has a II that is a node
 * too (docs/ARBOLES.md). What each one does is in {@link ClassSkills}; its numbers and its wait are in the tree's
 * file ({@link ClassTree.SkillDef}), where the tooltips read them too. The II replaces the numbers whole:
 * {@link #numbers(Player)} is one or the other.
 */
public enum ActiveSkill {
	GRITO_DE_GUERRA("guerrero", () -> new ItemStack(Items.GOAT_HORN)),
	POSTURA_DE_HIERRO("guerrero", () -> new ItemStack(Items.IRON_BLOCK)),
	BRAMIDO("guerrero", () -> new ItemStack(Items.SCULK_SHRIEKER)),
	HENDEDURA("guerrero", () -> new ItemStack(Items.DIAMOND_AXE)),
	TORBELLINO("guerrero", () -> new ItemStack(Items.IRON_AXE)),
	PASO_SOMBRIO("asesino", () -> new ItemStack(Items.INK_SAC)),
	MARCA_DE_MUERTE("asesino", () -> new ItemStack(Items.WITHER_ROSE)),
	DANZA_DE_SOMBRAS("asesino", () -> new ItemStack(Items.ECHO_SHARD)),
	EJECUCION("asesino", () -> new ItemStack(Items.DIAMOND_SWORD)),
	ABANICO_DE_DAGAS("asesino", () -> new ItemStack(Items.IRON_SWORD)),
	PROVOCAR("tanque", () -> new ItemStack(Items.BELL)),
	BALUARTE("tanque", () -> new ItemStack(Items.NETHERITE_CHESTPLATE)),
	GOLPE_SISMICO("tanque", () -> new ItemStack(Items.HEAVY_CORE)),
	SANTUARIO_DE_ACERO("tanque", () -> new ItemStack(Items.LODESTONE)),
	EMBESTIDA_DE_ESCUDO("tanque", () -> new ItemStack(Items.SHIELD)),
	NOVA_ARCANA("mago", () -> new ItemStack(Items.FIRE_CHARGE)),
	CONCENTRACION("mago", () -> new ItemStack(Items.ENDER_EYE)),
	// The lightning rod is a family of copper items in 26.2: the plain one, by its id.
	RELAMPAGO_EN_CADENA("mago", () -> new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
		net.minecraft.resources.Identifier.withDefaultNamespace("lightning_rod")))),
	PRISION_DE_HIELO("mago", () -> new ItemStack(Items.PACKED_ICE)),
	METEORO("mago", () -> new ItemStack(Items.MAGMA_BLOCK)),
	PULSO_SANADOR("curandero", () -> new ItemStack(Items.GLISTERING_MELON_SLICE)),
	RESURGIR("curandero", () -> new ItemStack(Items.TOTEM_OF_UNDYING)),
	OLEADA_DE_VIDA("curandero", () -> new ItemStack(Items.HEART_OF_THE_SEA)),
	SEGUNDA_VIDA("curandero", () -> new ItemStack(Items.RESPAWN_ANCHOR)),
	ESCUDO_DE_LUZ("curandero", () -> new ItemStack(Items.GOLDEN_APPLE)),
	SALTO_ATRAS("arquero", () -> new ItemStack(Items.RABBIT_FOOT)),
	LLUVIA_DE_FLECHAS("arquero", () -> new ItemStack(Items.TIPPED_ARROW)),
	SAETA_LETAL("arquero", () -> new ItemStack(Items.TRIDENT)),
	FLECHA_EXPLOSIVA("arquero", () -> new ItemStack(Items.TNT)),
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
		for (int key = 1; key <= 2; key++) {
			if (tree.skill(key).id.equals(this.id())) {
				return tree.skill(key);
			}
		}
		int ultimate = this.ultimate();
		if (ultimate > 0) {
			return tree.ultimates.get(ultimate - 1);
		}
		throw new IllegalStateException(this + " is not in " + this.owner().id() + "'s tree");
	}

	/** Which of its class's three ultimates it is (the senda it ends, 1 to 3), or 0 for V and B. */
	public int ultimate() {
		List<ClassTree.SkillDef> ultimates = ClassTree.tree(this.owner()).ultimates;
		for (int i = 0; i < ultimates.size(); i++) {
			if (ultimates.get(i).id.equals(this.id())) {
				return i + 1;
			}
		}
		return 0;
	}

	/** The key it is on: 1 V, 2 B, 3 N (every ultimate). */
	public int key() {
		if (this.ultimate() > 0) {
			return 3;
		}
		return ClassTree.tree(this.owner()).skill(2).id.equals(this.id()) ? 2 : 1;
	}

	/** The node that holds its II. */
	public ClassTree.@Nullable Node upgradeNode() {
		ClassTree.Tree tree = ClassTree.tree(this.owner());
		return switch (this.key()) {
			case 1 -> tree.bySlot(ClassTree.Slot.V2);
			case 2 -> tree.bySlot(ClassTree.Slot.B2);
			default -> tree.ultimateNode(this.ultimate(), true);
		};
	}

	/** Whether this player has its II. */
	public boolean upgraded(@Nullable Player player) {
		ClassTree.Node node = this.upgradeNode();
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
