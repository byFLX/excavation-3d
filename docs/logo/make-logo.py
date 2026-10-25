"""Original Excavation 3D icon. Python standard library only.

Draw directly on a 32 x 32 pixel grid, then replicate every pixel 16x or 8x.
Run: python make-logo.py
"""

from pathlib import Path
import struct
import zlib


SIZE = 32
PIXELS = [["#192129" for _ in range(SIZE)] for _ in range(SIZE)]


def dot(x, y, color):
    if 0 <= x < SIZE and 0 <= y < SIZE:
        PIXELS[y][x] = color


def rect(x0, y0, x1, y1, color):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            dot(x, y, color)


def sprite(x0, y0, rows, palette):
    for y, row in enumerate(rows):
        for x, char in enumerate(row):
            if char != ".":
                dot(x0 + x, y0 + y, palette[char])


def draw():
    # A restrained, opaque charcoal field; all shading uses hard pixel steps.
    rect(1, 1, 30, 30, "#1D2730")
    rect(2, 2, 29, 29, "#202B34")

    # Front-facing nine-block selection, with a slight bottom/right depth.
    rect(4, 7, 28, 30, "#11191F")
    rect(3, 6, 27, 29, "#705C27")
    rect(3, 5, 27, 28, "#E0B020")
    rect(4, 6, 26, 27, "#303B42")

    # Original hand-placed stone clusters: no sampled game textures.
    patterns = [
        ["hhhhmmm", "hmmmmlm", "mmlmmll", "mmlmmmm", "lmmhmmm", "lmmmhml"],
        ["hhmmmmm", "hmmllmm", "mmlmmmm", "mmmhhml", "mlmmmml", "mlllmmm"],
        ["hmhhhmm", "hmmmmml", "mmmllml", "mlmlmmm", "mmmhhmm", "llmmmmm"],
        ["hhmmmhm", "hmmmlmm", "mmmllmm", "mlmmmhh", "mlhmmmm", "mmmmlml"],
        ["hhhhmmm", "hmmmlmm", "mmmmlmm", "mhhmmmm", "mmmmllm", "mmlmmmm"],
        ["hhmmmmm", "hmmmllm", "mmhmmmm", "mmmhmml", "llmmmml", "mmmmhmm"],
        ["hhhmmhm", "hmmmlmm", "mmlmlmm", "mmlmmmm", "mmmmhhm", "llmmmmm"],
        ["hhmmmhm", "hmmmmmm", "mllmmhm", "mmmmmhm", "mmmllmm", "mlmmmmm"],
        ["hhmmmmm", "hmmmllm", "mmhmmmm", "mmhmmml", "lmmmmlm", "mmmmlmm"],
    ]
    for row in range(3):
        for col in range(3):
            x, y = 4 + col * 8, 6 + row * 7
            center = row == col == 1
            colors = ({"h": "#A5A08A", "m": "#878574", "l": "#6C7066"}
                      if center else
                      {"h": "#8A9499", "m": "#727E85", "l": "#5C6870"})
            sprite(x, y, patterns[row * 3 + col], colors)
            rect(x, y + 6, x + 6, y + 6, "#424F56")
    # Four small selection corners distinguish the initial block.
    for x, y, dx, dy in [(12, 13, 1, 1), (18, 13, -1, 1),
                          (12, 19, 1, -1), (18, 19, -1, -1)]:
        dot(x, y, "#E0B020")
        dot(x + dx, y, "#E0B020")
        dot(x, y + dy, "#E0B020")

    # Straight diagonal wooden shaft. Outline first, then two wooden faces.
    shaft = [(x, 32 - x) for x in range(5, 23)]
    for x, y in shaft:
        rect(x - 1, y - 1, x + 2, y + 2, "#102329")
    for x, y in shaft:
        dot(x, y, "#C29553")
        dot(x + 1, y, "#80532D")
        dot(x, y + 1, "#80532D")
    for x, y in [(6, 26), (10, 22), (14, 18), (18, 14)]:
        dot(x, y, "#DAAB66")

    # A slender, double-ended diamond pick head: a long upper-left tine,
    # central socket, and matching lower-right tine. This is a new sprite,
    # deliberately drawn from geometry rather than copied game pixel data.
    head = [
        "....oooo...........",
        "..oohhhho..........",
        ".ohhmmmhho.........",
        "ohmdddmmhho........",
        "odooo.odmhho.......",
        ".o.....odmhho......",
        "........odmhho.....",
        ".........odmhho....",
        "..........odmhho...",
        "...........odmhho..",
        "............odmhho.",
        ".............odmho.",
        "..............odmo.",
        "..............odmo.",
        "...............odo.",
        "................o..",
    ]
    sprite(10, 3, head, {"o": "#102D32", "h": "#A0F5E8",
                          "m": "#40CCCA", "d": "#21888E"})


def png_chunk(kind, data):
    return (struct.pack(">I", len(data)) + kind + data
            + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF))


def write_png(path, width):
    assert width % SIZE == 0
    scale = width // SIZE
    raw = bytearray()
    for row in PIXELS:
        expanded = b"".join(bytes.fromhex(c[1:]) + b"\xff" for c in row
                            for _ in range(scale))
        for _ in range(scale):
            raw.extend(b"\x00" + expanded)
    # PNG color type 6: true-color RGBA, every alpha byte explicitly 255.
    header = struct.pack(">IIBBBBB", width, width, 8, 6, 0, 0, 0)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + png_chunk(b"IHDR", header)
                     + png_chunk(b"IDAT", zlib.compress(bytes(raw), 9))
                     + png_chunk(b"IEND", b""))


if __name__ == "__main__":
    draw()
    for size in (512, 256):
        path = Path(__file__).resolve().parent / f"logo-{size}.png"
        write_png(path, size)
        print(f"Wrote {path.name}: {size} x {size}, opaque RGBA, 32 x 32 grid")
