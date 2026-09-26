"""The mod's trailer: title cards over embers, the game's own screenshots and filmed sequences with a slow
push-in, captions, crossfades. Frames are drawn with PIL and piped to ffmpeg; nothing else is needed.

    python docs/video/hacer_video.py [salida.mp4]
"""
import math
import random
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H, FPS = 1280, 720, 30
SHOTS = Path(r"E:\IA\Claude\Forja_capturas_mejoras\capturas_del_test")
CASTLE = Path(__file__).resolve().parent.parent / "castillo"
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(r"E:\IA\Claude\Forja_video\forja_trailer.mp4")
FFMPEG = r"C:\Users\andye\AppData\Local\Microsoft\WinGet\Packages\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe\ffmpeg-8.1.2-full_build\bin\ffmpeg.exe"

TITLE = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", 120)
HEAD = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", 58)
SUB = ImageFont.truetype(r"C:\Windows\Fonts\segoeui.ttf", 30)
CAPTION = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", 30)
SMALL = ImageFont.truetype(r"C:\Windows\Fonts\segoeui.ttf", 22)
EMBER = (255, 176, 88)
PALE = (240, 228, 204)

random.seed(7)


def shot(name):
    """One screenshot by the end of its name (the test numbers some of them)."""
    hits = sorted(SHOTS.glob(f"*{name}.png"))
    if not hits:
        raise FileNotFoundError(name)
    return Image.open(hits[0]).convert("RGB")


def film(prefix):
    return [Image.open(p).convert("RGB") for p in sorted(SHOTS.glob(f"{prefix}_*.png"))]


# ------------------------------------------------------------------------------------------ what is laid over every frame

def _vignette():
    mask = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(mask)
    for i in range(60):
        d.rectangle((i * 5, i * 3, W - i * 5, H - i * 3), outline=None, fill=min(255, i * 5))
    mask = mask.filter(ImageFilter.GaussianBlur(60))
    dark = Image.new("RGB", (W, H), (0, 0, 0))
    return dark, mask.point(lambda v: 150 - int(v * 150 / 255))


DARK, VIGNETTE = _vignette()


class Embers:
    """Sparks going up: what every title card is drawn over, and the thread from one scene to the next."""

    def __init__(self, count=110):
        self.sparks = [self.fresh(True) for _ in range(count)]

    def fresh(self, anywhere=False):
        return [random.uniform(0, W), random.uniform(0, H) if anywhere else H + random.uniform(0, 60), random.uniform(0.8, 3.2),
                random.uniform(0, 6.28), random.choice((2, 2, 3, 3, 4, 6)), random.uniform(0.4, 1.0)]

    def draw(self, image, strength=1.0):
        layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        for spark in self.sparks:
            spark[1] -= spark[2]
            spark[3] += 0.05
            spark[0] += math.sin(spark[3]) * 0.8
            if spark[1] < -10:
                spark[:] = self.fresh()
            life = max(0.0, min(1.0, spark[1] / H))
            colour = (255, int(90 + 140 * life), int(20 + 60 * life))
            size = spark[4]
            alpha = int(255 * spark[5] * strength * (0.35 + 0.65 * life))
            d.rectangle((spark[0] - size * 2, spark[1] - size * 2, spark[0] + size * 2, spark[1] + size * 2), fill=colour + (alpha // 6,))
            d.rectangle((spark[0], spark[1], spark[0] + size, spark[1] + size), fill=colour + (alpha,))
        image.paste(layer, (0, 0), layer)


EMBERS = Embers()


def backdrop(t):
    """A forge's dark: near-black going to a low red glow at the foot."""
    image = Image.new("RGB", (W, H), (12, 10, 10))
    d = ImageDraw.Draw(image)
    for y in range(H // 2, H):
        k = (y - H // 2) / (H / 2)
        glow = 0.75 + 0.25 * math.sin(t * 0.04)
        d.line((0, y, W, y), fill=(int(12 + 70 * k * k * glow), int(10 + 22 * k * k * glow), int(10 + 6 * k * k)))
    return image


def shadowed(d, xy, text, font, fill, anchor="la", shadow=(0, 0, 0)):
    x, y = xy
    for dx, dy in ((3, 3), (2, 2)):
        d.text((x + dx, y + dy), text, font=font, fill=shadow, anchor=anchor)
    d.text((x, y), text, font=font, fill=fill, anchor=anchor)


# ------------------------------------------------------------------------------------------ the kinds of scene

def title_card(frames, big, small, kicker=None):
    for t in range(frames):
        image = backdrop(t)
        EMBERS.draw(image)
        d = ImageDraw.Draw(image)
        rise = 1.0 - min(1.0, t / 18.0)
        y = H // 2 - 40 + int(30 * rise * rise)
        if kicker:
            shadowed(d, (W // 2, y - 96), kicker, SUB, EMBER, "mm")
        shadowed(d, (W // 2, y), big, TITLE if len(big) < 10 else HEAD, PALE, "mm")
        bar = int(min(1.0, t / 24.0) * 220)
        d.rectangle((W // 2 - bar, y + 78, W // 2 + bar, y + 81), fill=EMBER)
        shadowed(d, (W // 2, y + 120), small, SUB, (214, 200, 176), "mm")
        yield image


def heading(frames, number, text, line):
    """A short card between chapters."""
    for t in range(frames):
        image = backdrop(t + 40)
        EMBERS.draw(image, 0.8)
        d = ImageDraw.Draw(image)
        slide = int(60 * (1.0 - min(1.0, t / 12.0)) ** 2)
        shadowed(d, (120 - slide, H // 2 - 70), number, SUB, EMBER)
        shadowed(d, (120 - slide, H // 2 - 30), text, HEAD, PALE)
        d.rectangle((120, H // 2 + 52, 120 + int(min(1.0, t / 20.0) * 420), H // 2 + 55), fill=EMBER)
        shadowed(d, (120, H // 2 + 72), line, SUB, (214, 200, 176))
        yield image


def framed(source, zoom, cx=0.5, cy=0.5):
    """The screenshot filling the frame, pushed in by `zoom` about a point of it."""
    sw, sh = source.size
    scale = max(W / sw, H / sh) * zoom
    cw, ch = W / scale, H / scale
    left = min(max(0.0, cx * sw - cw / 2), sw - cw)
    top = min(max(0.0, cy * sh - ch / 2), sh - ch)
    return source.resize((W, H), Image.BICUBIC, box=(left, top, left + cw, top + ch))


def caption_layer(text, sub=None):
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for y in range(H - 150, H):
        d.line((0, y, W, y), fill=(0, 0, 0, int(210 * ((y - (H - 150)) / 150.0) ** 1.4)))
    d.rectangle((60, H - 92, 66, H - 40 if sub else H - 58), fill=EMBER + (255,))
    shadowed(d, (82, H - 96), text, CAPTION, PALE + (255,))
    if sub:
        shadowed(d, (82, H - 60), sub, SMALL, (214, 200, 176, 255))
    return layer


def still(source, frames, text=None, sub=None, zoom=(1.0, 1.08), focus=(0.5, 0.5)):
    layer = caption_layer(text, sub) if text else None
    for t in range(frames):
        k = t / max(1, frames - 1)
        image = framed(source, zoom[0] + (zoom[1] - zoom[0]) * k, *focus)
        finish(image, layer)
        yield image


def moving(sequence, frames, text=None, sub=None, rate=20, zoom=1.0, focus=(0.5, 0.5), loop=True):
    layer = caption_layer(text, sub) if text else None
    for t in range(frames):
        index = int(t * rate / FPS)
        index = index % len(sequence) if loop else min(index, len(sequence) - 1)
        image = framed(sequence[index], zoom + 0.04 * t / max(1, frames - 1), *focus)
        finish(image, layer)
        yield image


def finish(image, layer):
    image.paste(DARK, (0, 0), VIGNETTE)
    if layer is not None:
        image.paste(layer, (0, 0), layer)


def joined(*scenes, fade=10):
    """Scenes end to end, each dissolving into the next."""
    previous_tail = []
    for scene in scenes:
        frames = list(scene)
        if previous_tail:
            for i, old in enumerate(previous_tail):
                frames[i] = Image.blend(old, frames[i], (i + 1) / (len(previous_tail) + 1))
        for frame in frames[:-fade]:
            yield frame
        previous_tail = frames[-fade:]
    for frame in previous_tail:
        yield frame


# ------------------------------------------------------------------------------------------ the cut

def trailer():
    s = lambda seconds: int(seconds * FPS)
    castle_render = Image.open(CASTLE / "render_sur.png").convert("RGB")
    castle_render = castle_render.resize((castle_render.width * 2, castle_render.height * 2), Image.NEAREST)
    castle_shots = sorted((CASTLE / "capturas").glob("*.png")) if (CASTLE / "capturas").exists() else []
    scenes = [
        title_card(s(4.2), "FORJA", "Herrería por piezas para Minecraft 26.2 · Fabric", "un mod de"),

        heading(s(2.0), "01", "Todo se forja por piezas", "21 tipos de equipo · 30 materiales · cada pieza cuenta"),
        still(shot("forja_00_mesas"), s(2.4), "Tus mesas de trabajo", "piezas, forja, forja mayor, talabartería, extracción"),
        still(shot("forja_01_piezas"), s(2.4), "Cada pieza se corta con su plantilla", "y su material decide lo que aporta", focus=(0.5, 0.45)),
        moving(film("forja_02f_estrella") + film("forja_02f_golpe"), s(3.4), "La estrella de la forja", "pon las piezas, acierta el martillazo", rate=9, zoom=1.25, focus=(0.5, 0.42)),
        still(shot("forja_08_armadura_y_herramientas"), s(2.6), "Armas, herramientas y armaduras", "con el color de lo que llevan dentro"),

        heading(s(1.8), "02", "Armas con carácter", "lanzas, mazos, ballestas, escudos, alas, ganchos…"),
        still(shot("forja_15_lanza_cargando"), s(1.9), "La lanza y su embestida"),
        still(shot("forja_17_mazo_tercera"), s(1.9), "El mazo, que cae con todo su peso"),
        still(shot("forja_19b_ballesta_tercera"), s(1.9), "Ballesta modular"),
        still(shot("forja_11_escudo_bloqueando"), s(1.9), "Escudo: parada perfecta y embestida"),
        still(shot("forja_26_alas"), s(2.1), "Alas forjadas: planea según su membrana"),

        heading(s(2.0), "03", "112 mejoras que crecen", "en porcentaje, con los objetos que les das"),
        still(shot("forja_03_mejoras"), s(2.6), "Las mejoras sustituyen a los encantamientos", "suben del 0 al 100 % y su efecto con ellas", focus=(0.5, 0.42)),
        still(shot("forja_43_carga_mesa"), s(2.8), "Cada pieza tiene su potencial y su carga", "cómo la hiciste decide hasta dónde llega", zoom=(1.2, 1.3), focus=(0.5, 0.35)),
        moving([shot("forja_41_extraccion_elegida")] * 6 + film("forja_41f_vuelo") + [shot("forja_41_extraccion_hecha")] * 10, s(3.6),
               "La rueda de extracción", "saca una mejora y guárdala en un orbe", rate=10, zoom=1.22, focus=(0.5, 0.36), loop=False),

        heading(s(1.8), "04", "La fundición", "crisoles, cubas, canales y mesas de colada"),
        still(shot("forja_34_fundicion"), s(2.6), "Una línea de fundición entera", "cada metal con su color"),
        still(shot("forja_35_canal"), s(2.2), "Canales por el suelo: el metal se ve correr"),
        still(shot("forja_33_mesas_de_colada"), s(2.2), "Mesas de colada: la herramienta sale entera"),
        moving(film("forja_40_gotas"), s(2.0), "Gotas de metal fundido", rate=10),

        heading(s(1.8), "05", "El cielo manda", "nueve eventos que dejan mejoras únicas"),
        moving(film("forja_film_cielo_aurora"), s(2.4), "Aurora"),
        moving(film("forja_film_cielo_tormenta_arcana"), s(2.4), "Tormenta arcana"),
        still(shot("forja_cielo_05_luna_de_sangre"), s(1.8), "Luna de sangre"),
        still(shot("forja_cielo_06_eclipse"), s(1.8), "Eclipse"),
        moving(film("forja_film_cielo_lluvia_de_pavesas"), s(2.2), "Lluvia de pavesas"),
        moving(film("forja_film_12_meteorito"), s(2.8), "Y a veces cae hierro estelar", rate=18, loop=False),

        heading(s(1.8), "06", "Lo que te busca", "catorce criaturas propias, cada una con sus ataques"),
        still(shot("forja_30_mob_automata_de_forja"), s(1.7), "Autómata de forja"),
        moving(film("forja_film_06_automata_coz"), s(2.6), "…y su coz", rate=24, loop=False),
        still(shot("forja_30_mob_coraza_vacia"), s(1.7), "Coraza vacía"),
        moving(film("forja_film_03_coraza_embestida"), s(2.4), "…y su embestida", rate=24, loop=False),
        still(shot("forja_mob_constructos"), s(2.0), "Percutores, tenazas, cargadores, templadores"),
        still(shot("forja_mob_escoria"), s(1.6), "Escoria viviente"),
        still(shot("forja_mob_nucleo_lleno"), s(1.6), "Núcleo estelar"),

        heading(s(1.8), "07", "El Herrero Caído", "tres fases, y un martillo que hace temblar el suelo"),
        still(shot("forja_30_mob_herrero_caido"), s(2.0), "El Torcido"),
        moving(film("forja_film_01_herrero_onda"), s(4.4), "La onda de yunque", "se avisa en el suelo… y se puede saltar", rate=20, loop=False),
        moving(film("forja_film_11_herrero_ultima_fase"), s(2.8), "Su última fase", rate=20, loop=False),

        heading(s(1.8), "08", "Un mundo de herreros", "ruinas, talleres, aldeanos forjadores y un libro que lo explica todo"),
        still(shot("forja_21_forja_abandonada"), s(2.0), "Forjas abandonadas"),
        still(shot("forja_22_forjador"), s(2.0), "El Forjador"),
        still(shot("forja_04_libro_indice"), s(2.2), "La guía de forja: 216 páginas", focus=(0.5, 0.45)),

        heading(s(2.0), "EN OBRAS", "El Bastión del Gremio", "un castillo de 201 × 201 con 82 salas"),
        still(castle_render, s(4.0), "El Bastión del Gremio", "doble muralla, doce torres, torre del homenaje y dos sótanos", zoom=(1.0, 1.25), focus=(0.5, 0.45)),
    ]
    for path in castle_shots[:4]:
        scenes.append(still(Image.open(path).convert("RGB"), s(2.2), "Dentro del juego"))
    scenes.append(title_card(s(3.6), "FORJA", "hecho pieza a pieza"))
    return joined(*scenes)


def main():
    OUT.parent.mkdir(parents=True, exist_ok=True)
    encoder = subprocess.Popen([FFMPEG, "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
                                "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-pix_fmt", "yuv420p", "-movflags", "+faststart", str(OUT)],
                               stdin=subprocess.PIPE)
    count = 0
    for frame in trailer():
        encoder.stdin.write(frame.tobytes())
        count += 1
    encoder.stdin.close()
    encoder.wait()
    print(f"{count} frames, {count / FPS:.1f} s -> {OUT}")


if __name__ == "__main__":
    main()
