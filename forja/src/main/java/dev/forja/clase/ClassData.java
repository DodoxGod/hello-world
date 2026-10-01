package dev.forja.clase;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

/**
 * What a player carries of the class system: which class ("" for none yet), its level and experience, the
 * nodes of its tree learned (by id, docs/ARBOLES.md), when each of the three skills is ready again (game
 * time), how many times the class was changed, and the milestones reached (the player's, not the class's:
 * they stay through a change).
 *
 * <p>{@code version} is the tree's save version: 2 since the big trees, 3 since the three ultimates (some nodes were
 * renamed). A save from before the big trees carries the old small tree's mask in {@code legacyMask};
 * {@link ClassProgress#migrate} reads it once and clears it, and brings a version 2 save's node names up to date.
 *
 * @param skillReady game time at which the first skill (V) can be used again
 * @param secondReady the same for the second (B)
 * @param thirdReady the same for the third (N)
 */
public record ClassData(String clazz, int level, int xp, List<String> nodes, long skillReady, long secondReady, long thirdReady, int changes,
	List<String> milestones, int version, int legacyMask) {
	public static final int VERSION = 3;
	public static final ClassData NONE = new ClassData("", 0, 0, List.of(), 0L, 0L, 0L, 0, List.of(), VERSION, 0);

	public static final Codec<ClassData> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.optionalFieldOf("clase", "").forGetter(ClassData::clazz),
		Codec.INT.optionalFieldOf("nivel", 0).forGetter(ClassData::level),
		Codec.INT.optionalFieldOf("xp", 0).forGetter(ClassData::xp),
		Codec.STRING.listOf().optionalFieldOf("nodos", List.of()).forGetter(ClassData::nodes),
		Codec.LONG.optionalFieldOf("habilidad_1", 0L).forGetter(ClassData::skillReady),
		Codec.LONG.optionalFieldOf("habilidad_2", 0L).forGetter(ClassData::secondReady),
		Codec.LONG.optionalFieldOf("habilidad_3", 0L).forGetter(ClassData::thirdReady),
		Codec.INT.optionalFieldOf("cambios", 0).forGetter(ClassData::changes),
		Codec.STRING.listOf().optionalFieldOf("hitos", List.of()).forGetter(ClassData::milestones),
		// A save without this field is from the small trees (version 1).
		Codec.INT.optionalFieldOf("arbol", 1).forGetter(ClassData::version),
		Codec.INT.optionalFieldOf("talentos", 0).forGetter(ClassData::legacyMask)
	).apply(i, ClassData::new));

	private static final StreamCodec<ByteBuf, List<String>> STRINGS = ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());

	public static final StreamCodec<ByteBuf, ClassData> STREAM_CODEC = StreamCodec.of(
		(buf, data) -> {
			ByteBufCodecs.STRING_UTF8.encode(buf, data.clazz);
			ByteBufCodecs.VAR_INT.encode(buf, data.level);
			ByteBufCodecs.VAR_INT.encode(buf, data.xp);
			STRINGS.encode(buf, data.nodes);
			ByteBufCodecs.VAR_LONG.encode(buf, data.skillReady);
			ByteBufCodecs.VAR_LONG.encode(buf, data.secondReady);
			ByteBufCodecs.VAR_LONG.encode(buf, data.thirdReady);
			ByteBufCodecs.VAR_INT.encode(buf, data.changes);
			STRINGS.encode(buf, data.milestones);
			ByteBufCodecs.VAR_INT.encode(buf, data.version);
		},
		buf -> new ClassData(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
			STRINGS.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
			ByteBufCodecs.VAR_INT.decode(buf), STRINGS.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), 0)
	);

	public ClassData {
		nodes = List.copyOf(nodes);
		milestones = List.copyOf(milestones);
	}

	public @Nullable PlayerClass playerClass() {
		return PlayerClass.byId(this.clazz);
	}

	public ClassTree.@Nullable Tree tree() {
		PlayerClass owner = this.playerClass();
		return owner == null ? null : ClassTree.tree(owner);
	}

	public boolean has(String node) {
		return this.nodes.contains(node);
	}

	public boolean hasMilestone(String milestone) {
		return this.milestones.contains(milestone);
	}

	public ClassData withLevel(int level, int xp) {
		return new ClassData(this.clazz, level, xp, this.nodes, this.skillReady, this.secondReady, this.thirdReady, this.changes, this.milestones,
			this.version, this.legacyMask);
	}

	public ClassData withNodes(List<String> nodes) {
		return new ClassData(this.clazz, this.level, this.xp, nodes, this.skillReady, this.secondReady, this.thirdReady, this.changes, this.milestones,
			this.version, this.legacyMask);
	}

	public ClassData plus(String node) {
		List<String> next = new ArrayList<>(this.nodes);
		next.add(node);
		return this.withNodes(next);
	}

	public ClassData withVersion(int version) {
		return new ClassData(this.clazz, this.level, this.xp, this.nodes, this.skillReady, this.secondReady, this.thirdReady, this.changes, this.milestones,
			version, this.legacyMask);
	}

	public ClassData withMilestone(String milestone) {
		if (this.milestones.contains(milestone)) {
			return this;
		}
		List<String> next = new ArrayList<>(this.milestones);
		next.add(milestone);
		return new ClassData(this.clazz, this.level, this.xp, this.nodes, this.skillReady, this.secondReady, this.thirdReady, this.changes, next,
			this.version, this.legacyMask);
	}

	public ClassData withReady(int slot, long when) {
		return new ClassData(this.clazz, this.level, this.xp, this.nodes, slot == 1 ? when : this.skillReady, slot == 2 ? when : this.secondReady,
			slot == 3 ? when : this.thirdReady, this.changes, this.milestones, this.version, this.legacyMask);
	}

	public long ready(int slot) {
		return slot == 1 ? this.skillReady : slot == 2 ? this.secondReady : this.thirdReady;
	}

	/** Another class, or none: level, experience, nodes and waits are the new ones; the changes and the milestones stay. */
	public ClassData withClass(String clazz, int level, int xp, int changes) {
		return new ClassData(clazz, level, xp, List.of(), 0L, 0L, 0L, changes, this.milestones, VERSION, 0);
	}

	/** The save brought up to the big trees: old talents dropped (their points come back by themselves). */
	public ClassData migrated(String clazz, int level, int xp) {
		return new ClassData(clazz, level, xp, List.of(), this.skillReady, this.secondReady, this.thirdReady, this.changes, this.milestones,
			VERSION, 0);
	}

	/** Points spent on the tree. */
	public int spent() {
		ClassTree.Tree tree = this.tree();
		if (tree == null) {
			return 0;
		}
		int total = 0;
		for (String id : this.nodes) {
			ClassTree.Node node = tree.node(id);
			if (node != null) {
				total += node.cost;
			}
		}
		return total;
	}

	/** Every milestone point reached, spendable or not. */
	public int milestonePoints() {
		int total = 0;
		for (String id : this.milestones) {
			ClassTree.Milestone milestone = ClassTree.milestone(id);
			if (milestone != null) {
				total += milestone.points();
			}
		}
		return total;
	}

	/** The milestone points this level lets you spend: at most a few per class level (ClassTree.milestoneCapPerLevel). */
	public int usableMilestonePoints() {
		return Math.min(this.milestonePoints(), Math.max(0, this.level) * ClassTree.milestoneCapPerLevel());
	}

	/** Milestone points reached that wait for more levels. */
	public int waitingMilestonePoints() {
		return this.milestonePoints() - this.usableMilestonePoints();
	}

	/** Every point there is to spend at this level: the levels', plus the milestones' the level allows. */
	public int earned() {
		return ClassTree.levelPoints(this.level) + this.usableMilestonePoints();
	}

	/** Points still to spend. */
	public int points() {
		return Math.max(0, this.earned() - this.spent());
	}
}
