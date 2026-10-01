"""Makes a run of the asset generators leave files alone when what they would write has not changed.

A clean ``python tools/generate_assets.py`` used to rewrite about 240 committed files that it had not
really changed:

- text files (JSON, Java) committed with CRLF came back with LF, because the writers emit "\\n";
- structure .nbt files came back with another byte in the gzip header (the "operating system" byte
  differs between zlib builds), the same blocks underneath;
- PNGs came back with the same pixels but other encoder bytes (Pillow version, optimize flags);
- files under a folder the generator wipes first (models/item, the trades, the tags) lost their line
  endings because there was no old file left to compare against.

``install()`` patches the few ways the generators write (``Path.write_text``, ``Path.write_bytes``,
``Image.save`` to a path) so that each write is first compared with what is already there, by content
rather than bytes: text ignoring line endings, .nbt by its decompressed payload, .png by its pixels.
When nothing changed the committed bytes stay (or are put back, after a wipe). When something did
change, a text file keeps the line endings it had. New files are written exactly as asked.

It also patches ``shutil.rmtree`` and ``Path.unlink`` to remember what they delete under the project,
so a folder that is wiped and rebuilt can still be compared file by file.
"""

import gzip
import io
import os
import shutil
from pathlib import Path

TEXT = {".json", ".java", ".mcfunction", ".txt", ".md", ".mcmeta", ".properties", ".fsh", ".vsh", ".glsl"}

_root = None
_removed = {}
_installed = False
stats = {"kept": 0, "written": 0}


def _inside(path):
    try:
        Path(path).resolve().relative_to(_root)
        return True
    except ValueError:
        return False


def _previous(path):
    """What the file held before this run: on disk, or remembered from a wipe earlier in the run."""
    path = Path(path)
    if path.is_file():
        return path.read_bytes()
    return _removed.get(path.resolve())


def _line_ending(old):
    crlf = old.count(b"\r\n")
    return b"\r\n" if crlf and crlf * 2 >= old.count(b"\n") else b"\n"


def _read_nbt(data):
    """A gzipped NBT file as plain Python values (enough of the format for structure files)."""
    import struct
    stream = io.BytesIO(data)

    def take(fmt):
        return struct.unpack(">" + fmt, stream.read(struct.calcsize(fmt)))[0]

    def text():
        return stream.read(take("H")).decode("utf-8")

    def payload(kind):
        if kind in (1, 2, 3, 4, 5, 6):
            return take("bhiqfd"[kind - 1])
        if kind == 7:
            return stream.read(take("i"))
        if kind == 8:
            return text()
        if kind == 9:
            inner, count = take("b"), take("i")
            return [payload(inner) for _ in range(count)]
        if kind == 10:
            out = {}
            while (inner := take("b")) != 0:
                key = text()
                out[key] = payload(inner)
            return out
        if kind in (11, 12):
            count = take("i")
            return [take("i" if kind == 11 else "q") for _ in range(count)]
        raise ValueError(f"NBT tag {kind}")

    kind = take("b")
    text()
    return payload(kind)


def _structure_meaning(tree):
    """A structure template without the order of its palette: which block, with which data, sits where."""
    if not isinstance(tree, dict) or "palette" not in tree or "blocks" not in tree:
        return tree
    palette = [repr(entry) for entry in tree["palette"]]
    blocks = sorted((tuple(b["pos"]), palette[b["state"]], repr(b.get("nbt"))) for b in tree["blocks"])
    rest = {key: value for key, value in tree.items() if key not in ("palette", "blocks")}
    return blocks, repr(rest)


def _same_nbt(old, new):
    """The same structure: byte for byte once unzipped, or the same blocks in the same places. A piece patched
    by hand (a block renamed in its palette, a lantern taken out) keeps its palette in another order than the
    generator writes it, so its bytes differ when its blocks do not."""
    try:
        old_raw, new_raw = gzip.decompress(old), gzip.decompress(new)
        if old_raw == new_raw:
            return True
        return _structure_meaning(_read_nbt(old_raw)) == _structure_meaning(_read_nbt(new_raw))
    except Exception:                      # not a file this reader understands: compare it as changed
        return False


def _same_png(old, new_image):
    from PIL import Image
    try:
        before = Image.open(io.BytesIO(old))
        before.load()
    except OSError:
        return False
    if before.size != new_image.size:
        return False
    return before.convert("RGBA").tobytes() == new_image.convert("RGBA").tobytes()


def _settle(path, old, new):
    """Decides the bytes for a write whose content is ``new``: the old bytes when they mean the same."""
    suffix = Path(path).suffix.lower()
    if suffix in TEXT:
        if old.replace(b"\r\n", b"\n") == new.replace(b"\r\n", b"\n"):
            return old
        return new.replace(b"\r\n", b"\n").replace(b"\n", _line_ending(old))
    if suffix == ".nbt" and _same_nbt(old, new):
        return old
    return new


def _put(original_write_bytes, path, old, data):
    path = Path(path)
    if old is not None and data == old and path.is_file():
        stats["kept"] += 1
        return len(data)
    stats["written" if data != old else "kept"] += 1
    path.parent.mkdir(parents=True, exist_ok=True)
    return original_write_bytes(path, data)


def install(root):
    """Patches the writers. ``root`` bounds which files are compared and remembered (the project)."""
    global _root, _installed
    if _installed:
        return
    _installed = True
    _root = Path(root).resolve()
    from PIL import Image

    original_write_bytes = Path.write_bytes
    original_write_text = Path.write_text
    original_rmtree = shutil.rmtree
    original_unlink = Path.unlink
    original_save = Image.Image.save

    def write_bytes(self, data):
        if not _inside(self):
            return original_write_bytes(self, data)
        data = bytes(data)
        old = _previous(self)
        if old is None:
            return original_write_bytes(self, data)
        return _put(original_write_bytes, self, old, _settle(self, old, data))

    def write_text(self, data, encoding=None, errors=None, newline=None):
        if not _inside(self):
            if newline is None:
                return original_write_text(self, data, encoding=encoding, errors=errors)
            with open(self, "w", encoding=encoding, errors=errors, newline=newline) as f:
                return f.write(data)
        text = data if newline is None else data.replace("\n", newline)
        encoded = text.encode(encoding or "utf-8", errors or "strict")
        old = _previous(self)
        if old is None:
            if newline is None and os.linesep != "\n":
                # what a plain write_text would have written on this platform
                encoded = encoded.replace(b"\n", os.linesep.encode())
            original_write_bytes(self, encoded)
            return len(data)
        _put(original_write_bytes, self, old, _settle(self, old, encoded))
        return len(data)

    def save(self, fp, format=None, **params):
        if isinstance(fp, (str, os.PathLike)) and _inside(fp) and str(fp).lower().endswith(".png"):
            old = _previous(fp)
            if old is not None and _same_png(old, self):
                return _put(original_write_bytes, Path(fp), old, old)
            stats["written"] += 1
        return original_save(self, fp, format, **params)

    def rmtree(path, *args, **kwargs):
        target = Path(path)
        if target.is_dir() and _inside(target):
            for file in target.rglob("*"):
                if file.is_file():
                    _removed[file.resolve()] = file.read_bytes()
        return original_rmtree(path, *args, **kwargs)

    def unlink(self, missing_ok=False):
        if self.is_file() and _inside(self):
            _removed[self.resolve()] = self.read_bytes()
        return original_unlink(self, missing_ok=missing_ok)

    Path.write_bytes = write_bytes
    Path.write_text = write_text
    shutil.rmtree = rmtree
    Path.unlink = unlink
    Image.Image.save = save
