"""Forja's visual pass on screens, HUD and particles (2026-10-01): the textures it draws.

Run on its own from the project root (python tools/visual_gui.py) or from the end of generate_assets.py,
which hands itself over as `gen` so the helpers and paths are the same ones every other texture uses.
Everything here is drawn after generate_assets has written its own files, so what it redraws wins.

What it draws:
  - destello: the glint every spell throws now (client/ForjaParticles.Glint). A white-hot core and a soft
    falloff, four frames from full to a last ember; grey-white so the particle can take the spell's colour.
  - chispa: the spark off struck metal, redrawn the same way. It was a flat peach square tinted a second
    time by the particle, which came out as a brown dot on the ground at night.
"""

import math
import sys
from pathlib import Path


def _gen():
    """generate_assets as a module, for a standalone run."""
    tools = Path(__file__).resolve().parent
    if str(tools) not in sys.path:
        sys.path.insert(0, str(tools))
    import generate_assets
    return generate_assets


def glow_sprite(Image, size, core, reach, softness=1.6):
    """A round glow: fully lit inside `core` pixels of the centre, falling off to nothing at `reach`.

    White, with the light in the alpha, so whatever colour the particle is given is the colour it shows;
    the very middle is left a touch brighter than the rest only through alpha, never through hue.
    """
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    centre = (size - 1) / 2.0
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - centre, y - centre)
            if d <= core:
                a = 1.0
            elif d >= reach:
                continue
            else:
                t = (d - core) / max(0.01, reach - core)
                a = (1.0 - t) ** softness
            # Nothing may touch the sprite's edge, or the quad shows as a square.
            if min(x, y, size - 1 - x, size - 1 - y) == 0:
                a *= 0.0
            alpha = int(round(255 * a))
            if alpha <= 4:
                continue
            light = 255 if d <= core + 0.6 else 236
            img.putpixel((x, y), (light, light, light, alpha))
    return img


def generate_particles(gen):
    from PIL import Image

    folder = gen.ASSETS / "textures/particle"
    folder.mkdir(parents=True, exist_ok=True)
    # The glint: sixteen pixels, so the falloff has room to be soft. Frame 0 is the flash it is born in,
    # frame 3 the last coal of it.
    for frame, (core, reach) in enumerate(((2.2, 7.4), (1.6, 6.4), (1.1, 5.2), (0.6, 3.8))):
        glow_sprite(Image, 16, core, reach).save(folder / f"destello_{frame}.png")
    gen.write_json(gen.ASSETS / "particles/destello.json", {"textures": [f"forja:destello_{i}" for i in range(4)]})

    # The spark: eight pixels as before (it is small in the world), a hot point and a short glow round it,
    # in three sizes. The particle's own colour ramp (white-gold to red) does the rest.
    for frame, (core, reach) in enumerate(((0.9, 3.4), (0.6, 2.9), (0.4, 2.4))):
        glow_sprite(Image, 8, core, reach, softness=1.7).save(folder / f"chispa_{frame}.png")


def generate(gen):
    generate_particles(gen)


if __name__ == "__main__":
    generate(_gen())
    print("visual_gui textures written")
