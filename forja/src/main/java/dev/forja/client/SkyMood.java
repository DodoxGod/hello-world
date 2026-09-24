package dev.forja.client;

import dev.forja.world.EventSky;
import dev.forja.world.WorldEvents;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * What the sky is doing tonight, on the client.
 *
 * <p>The server sends one packet when an event starts and another when it ends. Everything else —
 * how strongly the colour shows, whether it comes on at dusk, how the stars behave — is worked out
 * here from the clock, because it has to change every frame and there is no sense sending sixty
 * packets a second to say "slightly more".
 *
 * <p>Two things matter about how it is applied. It <b>eases</b>, over four seconds, so an event
 * arriving does not snap the world to another colour; and most of them are <b>strongest at night</b>
 * and nearly gone at noon, because a coloured daytime sky reads as a broken shader while a coloured
 * night sky reads as something happening.
 *
 * <p>It used to stop there: nine events were nine flat colours poured over the whole sky, a blood moon
 * was "everything is red" and an arcane storm was "everything is mustard". The colour is now the
 * <b>backdrop</b> — darker and deeper than it was, so that something can stand in front of it — and
 * what stands in front of it is drawn by {@link EventSkyRenderer}: curtains of aurora, a red moon with
 * a halo, a sun with a disc across it, shooting stars, a sigil turning overhead. This class keeps the
 * state those need (where the sun and moon are, how far the fog lets you see, the lightning, the
 * streaks) because it is the one place that ticks.
 */
public final class SkyMood {
	/** How long the colour takes to come up or go down, in ticks. */
	private static final int EASE = 80;

	/** The most of the event's colour that ever reaches the sky, at midnight, at full strength. */
	private static final float MAX_SKY = 0.72F;

	/** And the most that reaches the fog, which is lower: fog this close to the eye tires quickly. */
	private static final float MAX_FOG = 0.45F;

	/**
	 * One generator, made once.
	 *
	 * <p>`Level.random` is protected and not ours to reach into, and building a fresh `RandomSource`
	 * inside the tick allocates one every tick of every event for the sake of a few coordinates.
	 */
	private static final net.minecraft.util.RandomSource RANDOM = net.minecraft.util.RandomSource.create();

	/** Where the game put the sun and the moon this frame, in radians, as the sky renderer worked them out. */
	public static float sunAngle;
	public static float moonAngle;

	/** How far the fog lets anything be seen before it starts to take it, as of the last frame. */
	public static float clearTo = 128.0F;

	/** How much of the open sky is over the player's head, eased: what the closing fog is scaled by. */
	private static float exposure = 1.0F;

	public static float exposure() {
		return exposure;
	}

	/** Sheet lightning under the arcane storm: one for a frame or two, then gone. */
	private static float flash;
	private static float flashBefore;
	private static int thunderIn;

	/** A light crossing the sky: a shooting star, or a piece of the sky on fire coming down. */
	public static final class Streak {
		/** Where on the sky it starts and the way it travels, both unit vectors, and how far round it goes. */
		public float x, y, z, tx, ty, tz, sweep;
		public int age;
		public int life;
		public float length;
		public float width;
		public int colour;
		public boolean fireball;
	}

	public static final java.util.List<Streak> STREAKS = new java.util.ArrayList<>();

	private static @Nullable WorldEvents event;
	/** Counts down on its own, so a missed "it ended" packet cannot leave the sky lit for ever. */
	private static int ticksLeft;
	/** Rises to one while an event runs and falls back to zero when it ends. */
	private static float blend;
	/** Kept so the ease can finish after the event itself is gone. */
	private static @Nullable WorldEvents fading;

	private SkyMood() {
	}

	public static void register() {
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
			EventSky.TYPE, (payload, context) -> context.client().execute(() -> {
				WorldEvents arriving = payload.event();
				if (arriving != null) {
					// The card comes down for an event that is news: one just begun, or one already
					// running when you arrive. Not for the same one being told to you twice.
					if (arriving != event) {
						EventBannerHud.show(arriving, true);
					}
					event = arriving;
					fading = arriving;
					ticksLeft = payload.ticksLeft();
				} else {
					if (event != null) {
						EventBannerHud.show(event, false);
					}
					event = null;
					ticksLeft = 0;
				}
			}));
		// The ease runs on the client tick rather than on the render frame, so it takes the same time
		// on any machine.
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// The clock runs here too. The server says "it ended" when it can, but a packet that never
			// arrives — a disconnect, a test that stops an event by hand, a world unloading — would
			// otherwise leave the sky the colour of a blood moon until the game was restarted. The
			// event's own length is the backstop, and it is authoritative enough for a colour.
			if (event != null && --ticksLeft <= 0) {
				EventBannerHud.show(event, false);
				event = null;
			}
			EventBannerHud.tick();
			if (client.level == null) {
				event = null;
				blend = 0.0F;
				fading = null;
				STREAKS.clear();
				flash = 0.0F;
				return;
			}
			float target = event == null ? 0.0F : 1.0F;
			blend = Mth.approach(blend, target, 1.0F / EASE);
			if (blend <= 0.0F) {
				fading = null;
			}
			// Eased, like everything else here: walking in under a roof must not switch a blizzard off.
			boolean open = client.player != null && client.level.canSeeSky(client.player.blockPosition().above(2));
			exposure = Mth.approach(exposure, open ? 1.0F : 0.0F, 0.04F);
			if (!client.isPaused()) {
				heavens(client);
			}
			weather(client);
		});
		EventSkyRenderer.register();
	}

	/**
	 * The things in the sky that have a life of their own: lightning, and whatever is crossing it.
	 *
	 * <p>Ticked rather than worked out per frame because they are events, not functions of the clock —
	 * a shooting star starts somewhere, goes somewhere and is gone — and the renderer only has to
	 * carry each one the part of a tick further.
	 */
	private static void heavens(net.minecraft.client.Minecraft client) {
		flashBefore = flash;
		flash *= 0.72F;
		if (flash < 0.02F) {
			flash = 0.0F;
		}
		STREAKS.removeIf(streak -> ++streak.age >= streak.life);
		WorldEvents showing = showing();
		if (showing == null) {
			return;
		}
		float weight = weight(client.level.getOverworldClockTime());
		if (thunderIn > 0 && --thunderIn == 0 && client.player != null) {
			client.level.playLocalSound(client.player.getX(), client.player.getY() + 40.0, client.player.getZ(),
				net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER, net.minecraft.sounds.SoundSource.WEATHER,
				0.5F * weight, 0.6F + RANDOM.nextFloat() * 0.4F, false);
		}
		if (weight < 0.2F) {
			return;
		}
		ambience(client, showing, weight);
		switch (showing) {
			case TORMENTA_ARCANA -> {
				// About every four seconds, and sometimes twice in a row the way sheet lightning does.
				if (RANDOM.nextInt(85) == 0 || (flashBefore > 0.3F && flashBefore < 0.5F && RANDOM.nextInt(3) == 0)) {
					flash = 0.7F + RANDOM.nextFloat() * 0.3F;
					if (thunderIn == 0) {
						thunderIn = 8 + RANDOM.nextInt(24);
					}
				}
			}
			case METEORITOS -> {
				if (RANDOM.nextFloat() < 0.22F * weight) {
					STREAKS.add(shootingStar(RANDOM.nextInt(14) == 0));
				}
			}
			case LLUVIA_DE_PAVESAS -> {
				if (RANDOM.nextFloat() < 0.05F * weight) {
					STREAKS.add(fallingFire());
				}
			}
			default -> {
			}
		}
	}

	/**
	 * What the night sounds like.
	 *
	 * <p>Every one of these was silent apart from the chime when it started, and a blizzard you cannot
	 * hear is a white screen. Nothing here is loud or constant: a gust of wind every few seconds, fire
	 * catching somewhere out in the dark, a voice in the fog. Only under the open sky, and quieter the
	 * less of the event there is, so that walking indoors shuts the door on it.
	 */
	private static void ambience(net.minecraft.client.Minecraft client, WorldEvents showing, float weight) {
		if (client.player == null || exposure < 0.4F) {
			return;
		}
		float loud = weight * exposure;
		switch (showing) {
			// The elytra's wind is a long loop, so it is laid end to end rather than rolled for.
			case VENTISCA -> {
				if (client.level.getGameTime() % 150L == 0L) {
					play(client, net.minecraft.sounds.SoundEvents.ELYTRA_FLYING, 0.32F * loud, 0.6F + RANDOM.nextFloat() * 0.25F);
				}
			}
			case LLUVIA_DE_PAVESAS -> {
				if (RANDOM.nextInt(60) == 0) {
					play(client, net.minecraft.sounds.SoundEvents.FIRE_AMBIENT, 0.7F * loud, 0.5F + RANDOM.nextFloat() * 0.6F);
				}
			}
			case NIEBLA_DE_ALMAS -> {
				if (RANDOM.nextInt(150) == 0) {
					play(client, net.minecraft.sounds.SoundEvents.SOUL_ESCAPE.value(), 0.55F * loud, 0.5F + RANDOM.nextFloat() * 0.3F);
				}
			}
			case AURORA -> {
				if (RANDOM.nextInt(190) == 0) {
					play(client, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 0.3F * loud, 0.4F + RANDOM.nextFloat() * 0.25F);
				}
			}
			case MAREA_VIVA -> {
				if (RANDOM.nextInt(110) == 0) {
					play(client, net.minecraft.sounds.SoundEvents.WATER_AMBIENT, 0.5F * loud, 0.6F + RANDOM.nextFloat() * 0.3F);
				}
			}
			default -> {
			}
		}
	}

	/** Somewhere off to one side and a little way out, never in the same place twice. */
	private static void play(net.minecraft.client.Minecraft client, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
		double angle = RANDOM.nextDouble() * Math.PI * 2.0;
		client.level.playLocalSound(client.player.getX() + Math.cos(angle) * 6.0, client.player.getY() + 3.0, client.player.getZ() + Math.sin(angle) * 6.0,
			sound, net.minecraft.sounds.SoundSource.AMBIENT, volume, pitch, false);
	}

	/** One shooting star: from somewhere high, a short way across, quickly. One in fourteen is a fireball. */
	private static Streak shootingStar(boolean fireball) {
		Streak streak = new Streak();
		double azimuth = RANDOM.nextDouble() * Math.PI * 2.0;
		double elevation = Math.toRadians(28.0 + RANDOM.nextDouble() * 50.0);
		streak.x = (float) (Math.cos(azimuth) * Math.cos(elevation));
		streak.y = (float) Math.sin(elevation);
		streak.z = (float) (Math.sin(azimuth) * Math.cos(elevation));
		// Mostly downwards and a little across: they all fall out of the same part of the sky.
		double heading = azimuth + Math.PI / 2.0 + (RANDOM.nextDouble() - 0.5) * 0.8;
		tangent(streak, (float) Math.cos(heading), -0.75F - RANDOM.nextFloat() * 0.5F, (float) Math.sin(heading));
		streak.life = fireball ? 34 + RANDOM.nextInt(14) : 9 + RANDOM.nextInt(8);
		streak.sweep = fireball ? 0.75F : 0.32F + RANDOM.nextFloat() * 0.2F;
		streak.length = fireball ? 0.32F : 0.2F;
		streak.width = fireball ? 0.011F : 0.0045F;
		streak.colour = fireball ? 0xFFD9A0 : 0xDDF2FF;
		streak.fireball = fireball;
		return streak;
	}

	/** A piece of the burning sky on its way down: slow, steep, and it leaves a long tail. */
	private static Streak fallingFire() {
		Streak streak = new Streak();
		double azimuth = RANDOM.nextDouble() * Math.PI * 2.0;
		double elevation = Math.toRadians(35.0 + RANDOM.nextDouble() * 40.0);
		streak.x = (float) (Math.cos(azimuth) * Math.cos(elevation));
		streak.y = (float) Math.sin(elevation);
		streak.z = (float) (Math.sin(azimuth) * Math.cos(elevation));
		tangent(streak, (RANDOM.nextFloat() - 0.5F) * 0.3F, -1.0F, (RANDOM.nextFloat() - 0.5F) * 0.3F);
		streak.life = 50 + RANDOM.nextInt(30);
		streak.sweep = 0.5F + RANDOM.nextFloat() * 0.2F;
		streak.length = 0.22F;
		streak.width = 0.007F;
		streak.colour = 0xFF9A3C;
		streak.fireball = true;
		return streak;
	}

	/** Makes a direction of travel lie along the sky at the streak's start rather than through it. */
	private static void tangent(Streak streak, float tx, float ty, float tz) {
		float along = tx * streak.x + ty * streak.y + tz * streak.z;
		tx -= along * streak.x;
		ty -= along * streak.y;
		tz -= along * streak.z;
		float size = Mth.sqrt(tx * tx + ty * ty + tz * tz);
		if (size < 1.0E-4F) {
			tx = 1.0F;
			ty = 0.0F;
			tz = 0.0F;
			size = 1.0F;
		}
		streak.tx = tx / size;
		streak.ty = ty / size;
		streak.tz = tz / size;
	}

	/** The lightning as of this frame, eased between ticks so a flash has a shape rather than a step. */
	public static float flash(float partialTick) {
		return Mth.lerp(partialTick, flashBefore, flash);
	}

	/**
	 * The weather of the event, thrown around the player every tick.
	 *
	 * <p>The server already sprinkled thirty particles six blocks over everyone's head once a minute,
	 * which is a gesture rather than weather. This is done on the client instead, which means it can be
	 * as thick as it likes for nothing: no packets, no server tick, and it stops the moment the player
	 * turns particles down because it goes through the same setting everything else does.
	 *
	 * <p>Each event throws something that matches what it is rather than a recoloured version of the
	 * same thing — embers fall, snow drives sideways, souls drift at knee height and an aurora hangs
	 * high and still. The point of nine events is that they are nine different nights.
	 */
	private static void weather(net.minecraft.client.Minecraft client) {
		WorldEvents showing = showing();
		if (showing == null || client.player == null || client.level == null) {
			return;
		}
		float weight = weight(client.level.getOverworldClockTime());
		if (weight < 0.15F) {
			return;
		}
		// Only under the open sky: an event you can see through a stone roof is a bug, not a mood.
		net.minecraft.core.BlockPos head = client.player.blockPosition().above(2);
		if (!client.level.canSeeSky(head)) {
			return;
		}
		var random = RANDOM;
		int count = (int) (weight * switch (showing) {
			case VENTISCA -> 16;
			case LLUVIA_DE_PAVESAS -> 9;
			case NIEBLA_DE_ALMAS, LUNA_DE_SANGRE -> 5;
			// These two are in the sky now, drawn, not thrown about as particles at head height.
			// And an eclipse has no weather at all. It had squid ink, which was black blobs at head height.
			case METEORITOS, AURORA, ECLIPSE -> 0;
			default -> 4;
		});
		for (int i = 0; i < count; i++) {
			double x = client.player.getX() + (random.nextDouble() - 0.5) * 34.0;
			double z = client.player.getZ() + (random.nextDouble() - 0.5) * 34.0;
			switch (showing) {
				// Embers come down out of the sky and keep falling.
				case LLUVIA_DE_PAVESAS -> client.level.addParticle(dev.forja.registry.ModParticles.CHISPA,
					x, client.player.getY() + 14.0 + random.nextDouble() * 6.0, z,
					(random.nextDouble() - 0.5) * 0.02, -0.18 - random.nextDouble() * 0.1, (random.nextDouble() - 0.5) * 0.02);
				// Snow drives sideways, which is what makes a blizzard a blizzard and not a snowfall.
				case VENTISCA -> client.level.addParticle(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE,
					x, client.player.getY() + random.nextDouble() * 12.0, z,
					0.35 + random.nextDouble() * 0.25, -0.05, 0.12 * (random.nextDouble() - 0.5));
				// Souls at knee height, going nowhere in particular.
				case NIEBLA_DE_ALMAS -> client.level.addParticle(dev.forja.registry.ModParticles.ALMA,
					x, client.player.getY() + random.nextDouble() * 2.5, z,
					(random.nextDouble() - 0.5) * 0.02, 0.01, (random.nextDouble() - 0.5) * 0.02);
				// The aurora gets its own thing entirely; see ribbons().
				case AURORA -> {
				}
				case TORMENTA_ARCANA -> client.level.addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
					x, client.player.getY() + 4.0 + random.nextDouble() * 14.0, z,
					(random.nextDouble() - 0.5) * 0.3, (random.nextDouble() - 0.5) * 0.2, (random.nextDouble() - 0.5) * 0.3);
				// Ash, and the sky raining it.
				case LUNA_DE_SANGRE -> client.level.addParticle(dev.forja.registry.ModParticles.CENIZA,
					x, client.player.getY() + 8.0 + random.nextDouble() * 10.0, z,
					(random.nextDouble() - 0.5) * 0.04, -0.03, (random.nextDouble() - 0.5) * 0.04);
				case ECLIPSE -> {
				}
				case MAREA_VIVA -> client.level.addParticle(net.minecraft.core.particles.ParticleTypes.BUBBLE_POP,
					x, client.player.getY() + random.nextDouble() * 6.0, z,
					(random.nextDouble() - 0.5) * 0.05, 0.03, (random.nextDouble() - 0.5) * 0.05);
				// Shooting stars are streaks across the sky now; see heavens().
				case METEORITOS -> {
				}
			}
		}
	}

	/** The event whose colour is on screen, which outlives the event itself while it fades out. */
	public static @Nullable WorldEvents showing() {
		return blend > 0.0F ? fading : null;
	}

	/** Zero to one, eased. */
	public static float blend() {
		return blend;
	}

	/**
	 * How much of the event's colour applies at this moment of the day.
	 *
	 * <p>Full at midnight, nothing around noon, and a long ramp through dusk and dawn so it arrives
	 * with the dark rather than at a particular tick.
	 */
	public static float byTime(long dayTime) {
		float phase = (dayTime % 24000L) / 24000.0F;
		// Midnight is 0.5 of the day in Minecraft's clock; cosine gives the ramp for free.
		float night = (float) ((1.0 - Math.cos((phase - 0.25) * Math.PI * 2.0)) * 0.5);
		return Mth.clamp((night - 0.25F) / 0.75F, 0.0F, 1.0F);
	}

	/**
	 * How much of the running event is showing right now, zero to one: the ease, times the hour.
	 *
	 * <p>The hour is not the same for all of them. An aurora at noon is nothing, but an eclipse <i>is</i>
	 * a thing that happens at noon, and a blizzard, a fog or a sky full of embers does not wait for
	 * dark either — weather is weather. Those keep most of their strength by day.
	 */
	public static float weight(long dayTime) {
		WorldEvents showing = showing();
		if (showing == null) {
			return 0.0F;
		}
		float hour = switch (showing) {
			case ECLIPSE -> 1.0F;
			case VENTISCA, NIEBLA_DE_ALMAS -> Math.max(byTime(dayTime), 0.85F);
			case LLUVIA_DE_PAVESAS -> Math.max(byTime(dayTime), 0.6F);
			default -> byTime(dayTime);
		};
		return blend * hour;
	}

	/**
	 * What the sky itself leans towards under each event.
	 *
	 * <p>Not the event's own colour, which is for its name in chat and the glass of its jar: that is a
	 * bright, friendly colour, and a whole sky of it was a poster. These are what is <i>behind</i> the
	 * aurora, the moon and the sigil, so they are dark, and they are different from each other in
	 * more than hue — a blizzard's sky is pale and close, an eclipse's is nearly black.
	 */
	private static int skyColour(WorldEvents showing) {
		return switch (showing) {
			case METEORITOS -> 0x0B1230;
			case TORMENTA_ARCANA -> 0x2A1B4D;
			case NIEBLA_DE_ALMAS -> 0x2C5A5C;
			case AURORA -> 0x061A24;
			case LUNA_DE_SANGRE -> 0x3A0A0C;
			case ECLIPSE -> 0x120C22;
			case VENTISCA -> 0xB8C8D6;
			case MAREA_VIVA -> 0x0C2C48;
			case LLUVIA_DE_PAVESAS -> 0x3A1606;
		};
	}

	private static int fogColour(WorldEvents showing) {
		return switch (showing) {
			case METEORITOS -> 0x101A38;
			case TORMENTA_ARCANA -> 0x3A2A66;
			case NIEBLA_DE_ALMAS -> 0x3E7F80;
			case AURORA -> 0x0E2A30;
			case LUNA_DE_SANGRE -> 0x5A1412;
			case ECLIPSE -> 0x1A1230;
			case VENTISCA -> 0xD0DCE6;
			case MAREA_VIVA -> 0x14405A;
			case LLUVIA_DE_PAVESAS -> 0x6A3210;
		};
	}

	/** The strength to mix the backdrop into the sky with, all told. */
	public static float skyStrength(long dayTime) {
		WorldEvents showing = showing();
		if (showing == null) {
			return 0.0F;
		}
		float share = switch (showing) {
			case ECLIPSE -> 0.9F;
			case VENTISCA, NIEBLA_DE_ALMAS -> 0.8F;
			case METEORITOS -> 0.55F;
			case AURORA, MAREA_VIVA -> 0.6F;
			default -> MAX_SKY;
		};
		return weight(dayTime) * share;
	}

	/** And into the fog. */
	public static float fogStrength(long dayTime) {
		WorldEvents showing = showing();
		if (showing == null) {
			return 0.0F;
		}
		float share = switch (showing) {
			case VENTISCA, NIEBLA_DE_ALMAS, ECLIPSE -> 0.85F;
			case METEORITOS -> 0.25F;
			case AURORA, MAREA_VIVA -> 0.35F;
			default -> MAX_FOG;
		};
		return weight(dayTime) * share;
	}

	/**
	 * How close the fog comes, in blocks, or zero to leave it where the game put it.
	 *
	 * <p>The one thing a colour could never do. A blizzard you can see two hundred blocks through is a
	 * white sky with snow in front of it; a blizzard is <i>not being able to see</i>. The same for the
	 * soul fog. The ember rain keeps its distance: its sky has a burning horizon, and fog would take it.
	 */
	public static float fogReach(WorldEvents showing) {
		return switch (showing) {
			case VENTISCA -> 30.0F;
			case NIEBLA_DE_ALMAS -> 44.0F;
			default -> 0.0F;
		};
	}

	/** How far away the things in the sky are hung: inside what the fog leaves clear, or it would take them. */
	public static float skyRadius() {
		return Mth.clamp(clearTo * 0.72F, 48.0F, 150.0F);
	}

	/**
	 * How much brighter the stars burn under this event.
	 *
	 * <p>Not all of them touch the stars. A meteor shower and an aurora put more light up there; an
	 * eclipse and a soul fog take it away, which is a more useful thing for a sky to be able to say
	 * than simply being a different colour.
	 */
	public static float starFactor(long dayTime) {
		WorldEvents showing = showing();
		if (showing == null) {
			return 1.0F;
		}
		float weight = weight(dayTime);
		float factor = switch (showing) {
			case METEORITOS -> 1.9F;
			case AURORA -> 1.5F;
			case TORMENTA_ARCANA -> 1.3F;
			case ECLIPSE -> 1.0F;
			case NIEBLA_DE_ALMAS -> 0.2F;
			case VENTISCA -> 0.1F;
			case LUNA_DE_SANGRE -> 0.8F;
			default -> 1.0F;
		};
		return Mth.lerp(weight, 1.0F, factor);
	}

	/**
	 * The least the stars shine under this event, whatever the hour: the eclipse brings them out at noon,
	 * which is the oldest thing anyone knows about eclipses.
	 */
	public static float starFloor(long dayTime) {
		return showing() == WorldEvents.ECLIPSE ? weight(dayTime) * 0.75F : 0.0F;
	}

	/**
	 * What the event does to the light on the ground.
	 *
	 * <p>The sky changing and the world under it not was half of why the events looked painted on: an
	 * eclipse with noon still blazing on the grass, a blood moon over a field lit the ordinary blue of
	 * night. The light the sky gives is the sky's, so it takes the event's cast — and under an eclipse
	 * most of it simply goes. Lightning is the other half of this: the flash is on the land as well as
	 * behind the clouds, for the frame or two it lasts.
	 */
	public static void light(net.minecraft.client.renderer.state.LightmapRenderState state, float partialTick) {
		WorldEvents showing = showing();
		net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
		if (showing == null || level == null || level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
			return;
		}
		float weight = weight(level.getOverworldClockTime());
		if (weight <= 0.0F) {
			return;
		}
		int cast = switch (showing) {
			case LUNA_DE_SANGRE -> 0xFF6A5A;
			case AURORA -> 0x9CFFD0;
			case MAREA_VIVA -> 0xA8DCFF;
			case LLUVIA_DE_PAVESAS -> 0xFFA860;
			case TORMENTA_ARCANA -> 0xC4A8FF;
			case NIEBLA_DE_ALMAS -> 0x9CF0E8;
			case ECLIPSE -> 0xB8A8E0;
			default -> 0xFFFFFF;
		};
		float share = weight * (cast == 0xFFFFFF ? 0.0F : 0.55F);
		org.joml.Vector3fc was = state.skyLightColor;
		state.skyLightColor = new org.joml.Vector3f(
			Mth.lerp(share, was.x(), (cast >> 16 & 255) / 255.0F),
			Mth.lerp(share, was.y(), (cast >> 8 & 255) / 255.0F),
			Mth.lerp(share, was.z(), (cast & 255) / 255.0F));
		float much = switch (showing) {
			// Noon goes down to deep dusk, which is about what totality does.
			case ECLIPSE -> 0.3F;
			// The moon is twice the size: the night under it is a bright one.
			case MAREA_VIVA -> 1.5F;
			case NIEBLA_DE_ALMAS, VENTISCA -> 0.8F;
			default -> 1.0F;
		};
		state.skyFactor *= Mth.lerp(weight, 1.0F, much);
		float lit = flash(partialTick) * weight;
		if (lit > 0.0F) {
			state.skyFactor = Math.max(state.skyFactor, lit * 0.95F);
		}
		state.needsUpdate = true;
	}

	/** Mixes the event's backdrop into the sky colour the game was going to use, and the lightning over that. */
	public static int tintSky(int original, long dayTime, float partialTick) {
		WorldEvents showing = showing();
		if (showing == null) {
			return original;
		}
		int mixed = mix(original, skyColour(showing), skyStrength(dayTime));
		float lit = flash(partialTick) * weight(dayTime);
		return lit > 0.0F ? mix(mixed, 0xCFC2FF, lit * 0.75F) : mixed;
	}

	/** The same for the fog, as three floats because that is how the fog keeps its colour. */
	public static void tintFog(org.joml.Vector4f colour, long dayTime, float partialTick) {
		WorldEvents showing = showing();
		if (showing == null) {
			return;
		}
		int target = fogColour(showing);
		float strength = fogStrength(dayTime);
		float lit = flash(partialTick) * weight(dayTime) * 0.4F;
		colour.x = Mth.lerp(lit, Mth.lerp(strength, colour.x, (target >> 16 & 255) / 255.0F), 0.81F);
		colour.y = Mth.lerp(lit, Mth.lerp(strength, colour.y, (target >> 8 & 255) / 255.0F), 0.76F);
		colour.z = Mth.lerp(lit, Mth.lerp(strength, colour.z, (target & 255) / 255.0F), 1.0F);
	}

	private static int mix(int from, int to, float strength) {
		if (strength <= 0.0F) {
			return from;
		}
		int red = Mth.lerpInt(strength, from >> 16 & 255, to >> 16 & 255);
		int green = Mth.lerpInt(strength, from >> 8 & 255, to >> 8 & 255);
		int blue = Mth.lerpInt(strength, from & 255, to & 255);
		return from & 0xFF000000 | red << 16 | green << 8 | blue;
	}
}
