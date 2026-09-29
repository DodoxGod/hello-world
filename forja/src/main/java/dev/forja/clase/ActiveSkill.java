package dev.forja.clase;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The two active skills of every class: the first comes with the class (key V), the second is the last node
 * of its tree (key B). What each one does is in {@link ClassSkills}; its numbers are here, where the tooltip
 * reads them too.
 */
public enum ActiveSkill {
	/** Stamina back ({@code numbers[0]}), and Strength I for you and players within {@code numbers[1]} blocks for {@code numbers[2]} s. */
	GRITO_DE_GUERRA(45, () -> new ItemStack(Items.GOAT_HORN), 40.0F, 8.0F, 8.0F),
	/** Resistance II and Slowness I for {@code numbers[0]} s. */
	POSTURA_DE_HIERRO(50, () -> new ItemStack(Items.IRON_BLOCK), 6.0F),
	/** Invisible and Speed II for {@code numbers[0]} s; the next melee blow in that time does {@code numbers[1]} more. */
	PASO_SOMBRIO(30, () -> new ItemStack(Items.INK_SAC), 4.0F, 0.60F),
	/** The mob you look at within {@code numbers[0]} blocks glows for {@code numbers[1]} s and takes {@code numbers[2]} more from you. */
	MARCA_DE_MUERTE(40, () -> new ItemStack(Items.WITHER_ROSE), 16.0F, 10.0F, 0.30F),
	/** Hostile mobs within {@code numbers[0]} blocks turn on you; Resistance I for {@code numbers[1]} s. */
	PROVOCAR(25, () -> new ItemStack(Items.BELL), 10.0F, 6.0F),
	/** Resistance II for you and Resistance I for players within {@code numbers[1]} blocks, {@code numbers[0]} s. */
	BALUARTE(60, () -> new ItemStack(Items.NETHERITE_CHESTPLATE), 8.0F, 6.0F),
	/** A ring of {@code numbers[0]} blocks: {@code numbers[1]} magic damage to hostile mobs, and a push. */
	NOVA_ARCANA(20, () -> new ItemStack(Items.FIRE_CHARGE), 5.0F, 6.0F),
	/** For {@code numbers[0]} s your spells wait {@code numbers[1]} as long (and, with mana, cost nothing). */
	CONCENTRACION(60, () -> new ItemStack(Items.ENDER_EYE), 8.0F, 0.50F),
	/** {@code numbers[1]} health to you and your allies within {@code numbers[0]} blocks. */
	PULSO_SANADOR(30, () -> new ItemStack(Items.GLISTERING_MELON_SLICE), 8.0F, 4.0F),
	/** The ally you look at within {@code numbers[0]} blocks gets back {@code numbers[1]} of their missing health and Regeneration II for {@code numbers[2]} s. */
	RESURGIR(90, () -> new ItemStack(Items.TOTEM_OF_UNDYING), 16.0F, 0.50F, 5.0F),
	/** A leap of about {@code numbers[0]} blocks backwards, and Slow Falling for {@code numbers[1]} s. */
	SALTO_ATRAS(12, () -> new ItemStack(Items.RABBIT_FOOT), 6.0F, 2.0F),
	/** {@code numbers[0]} arrows over {@code numbers[1]} s on a circle of {@code numbers[2]} blocks where you look (within {@code numbers[3]}), {@code numbers[4]} damage each. */
	LLUVIA_DE_FLECHAS(45, () -> new ItemStack(Items.TIPPED_ARROW), 12.0F, 2.0F, 3.0F, 32.0F, 4.0F),
	/** The forged piece in your hand mends {@code numbers[0]} of its durability; Haste II for {@code numbers[1]} s. */
	TEMPLE_DE_CAMPANA(60, () -> new ItemStack(Items.ANVIL), 0.15F, 10.0F),
	/** For {@code numbers[0]} s your melee blows set fire for {@code numbers[1]} s and do {@code numbers[2]} more. */
	FORJA_AL_ROJO(60, () -> new ItemStack(Items.BLAZE_ROD), 10.0F, 4.0F, 0.20F);

	public final int cooldownSeconds;
	private final Supplier<ItemStack> icon;
	public final float[] numbers;

	ActiveSkill(int cooldownSeconds, Supplier<ItemStack> icon, float... numbers) {
		this.cooldownSeconds = cooldownSeconds;
		this.icon = icon;
		this.numbers = numbers;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	public int cooldownTicks() {
		return this.cooldownSeconds * 20;
	}

	/** A number in whole ticks: {@code numbers[i]} seconds. */
	public int ticks(int i) {
		return Math.round(this.numbers[i] * 20.0F);
	}

	public ItemStack icon() {
		return this.icon.get();
	}

	public Component displayName() {
		return Component.translatable("gui.forja.habilidad." + this.id());
	}

	/** What it does, with its numbers, and how long it waits. */
	public List<Component> lines() {
		Object[] args = new Object[this.numbers.length];
		for (int i = 0; i < this.numbers.length; i++) {
			args[i] = Talent.shown(this.numbers[i]);
		}
		return List.of(
			Component.translatable("gui.forja.habilidad." + this.id() + ".efecto", args).withColor(0xFF7FD34E),
			Component.translatable("gui.forja.habilidad.espera", this.cooldownSeconds).withColor(0xFF9A9A9A)
		);
	}
}
