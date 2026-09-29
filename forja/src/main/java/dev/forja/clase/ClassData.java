package dev.forja.clase;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

/**
 * What a player carries of the class system: which class ("" for none yet), its level and experience, the
 * talents learned (a bit per node, by its place in its class's tree), when each of the two skills is ready
 * again (game time), and how many times the class was changed.
 *
 * @param skillReady game time at which the first skill can be used again
 * @param secondReady the same for the second
 */
public record ClassData(String clazz, int level, int xp, int mask, long skillReady, long secondReady, int changes) {
	public static final ClassData NONE = new ClassData("", 0, 0, 0, 0L, 0L, 0);

	public static final Codec<ClassData> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.optionalFieldOf("clase", "").forGetter(ClassData::clazz),
		Codec.INT.optionalFieldOf("nivel", 0).forGetter(ClassData::level),
		Codec.INT.optionalFieldOf("xp", 0).forGetter(ClassData::xp),
		Codec.INT.optionalFieldOf("talentos", 0).forGetter(ClassData::mask),
		Codec.LONG.optionalFieldOf("habilidad_1", 0L).forGetter(ClassData::skillReady),
		Codec.LONG.optionalFieldOf("habilidad_2", 0L).forGetter(ClassData::secondReady),
		Codec.INT.optionalFieldOf("cambios", 0).forGetter(ClassData::changes)
	).apply(i, ClassData::new));

	public static final StreamCodec<ByteBuf, ClassData> STREAM_CODEC = StreamCodec.of(
		(buf, data) -> {
			ByteBufCodecs.STRING_UTF8.encode(buf, data.clazz);
			ByteBufCodecs.VAR_INT.encode(buf, data.level);
			ByteBufCodecs.VAR_INT.encode(buf, data.xp);
			ByteBufCodecs.VAR_INT.encode(buf, data.mask);
			ByteBufCodecs.VAR_LONG.encode(buf, data.skillReady);
			ByteBufCodecs.VAR_LONG.encode(buf, data.secondReady);
			ByteBufCodecs.VAR_INT.encode(buf, data.changes);
		},
		buf -> new ClassData(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
			ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_INT.decode(buf))
	);

	public @Nullable PlayerClass playerClass() {
		return PlayerClass.byId(this.clazz);
	}

	public boolean has(Talent talent) {
		return talent.owner == this.playerClass() && (this.mask & 1 << talent.index()) != 0;
	}

	public ClassData withLevel(int level, int xp) {
		return new ClassData(this.clazz, level, xp, this.mask, this.skillReady, this.secondReady, this.changes);
	}

	public ClassData withMask(int mask) {
		return new ClassData(this.clazz, this.level, this.xp, mask, this.skillReady, this.secondReady, this.changes);
	}

	public ClassData withReady(int slot, long when) {
		return slot == 1
			? new ClassData(this.clazz, this.level, this.xp, this.mask, when, this.secondReady, this.changes)
			: new ClassData(this.clazz, this.level, this.xp, this.mask, this.skillReady, when, this.changes);
	}

	/** Points spent on the tree. */
	public int spent() {
		PlayerClass owner = this.playerClass();
		if (owner == null) {
			return 0;
		}
		int total = 0;
		for (Talent talent : owner.talents()) {
			if ((this.mask & 1 << talent.index()) != 0) {
				total += talent.cost();
			}
		}
		return total;
	}

	/** Points still to spend: one per level, less what is spent. */
	public int points() {
		return Math.max(0, ClassProgress.pointsAt(this.level) - this.spent());
	}
}
