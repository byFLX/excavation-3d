"""Excavation 3D, refined concept C. Run with Python 3; no dependencies.

All artwork is drawn on a 64-pixel grid, then enlarged without filtering.
Only logo-c2-*.png files beside this script are written.
"""

import math
import struct
import zlib
from pathlib import Path

SIZE = 64
BG = (21, 26, 32)
GOLD = (224, 176, 32)
HIGHLIGHT = (246, 211, 91)
ORIGIN = (24, 29)
UNIT = 7
# An L in the y/z plane, two cells wide: six removed voxels total.
REMOVED = {(x, y, z) for x in (1, 2) for y, z in ((1, 2), (2, 2), (2, 1))}


def project(point):
    x, y, z = point
    return ORIGIN[0] + UNIT * (x-y), ORIGIN[1] + UNIT * ((x+y)/2-z)


def png(path, pixels, factor=1):
    rows = [bytes(value for pixel in row for _ in range(factor)
                  for value in (*pixel, 255)) for row in pixels]
    raw = b''.join((b'\0' + row) * factor for row in rows)

    def chunk(tag, data):
        return (struct.pack('>I', len(data)) + tag + data
                + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff))

    header = struct.pack('>IIBBBBB', SIZE*factor, SIZE*factor, 8, 6, 0, 0, 0)
    path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', header)
                     + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))


def ray_hit(base, boxes):
    best, hit = -1e9, None
    for low, high, seed, loose in boxes:
        near = [low[i]-base[i] for i in range(3)]
        far = [high[i]-base[i] for i in range(3)]
        depth = min(far)
        if depth > max(near)+1e-8 and depth > best:
            face = far.index(depth)
            point = tuple(v+depth for v in base)
            best, hit = depth, (point, face, seed, loose)
    return best, hit


def stone(point, face, seed, loose):
    # x-normal = right/dark, y-normal = left/medium, z-normal = top/light.
    color = ((79, 91, 106), (122, 135, 146), (169, 180, 187))[face]
    axes = [i for i in range(3) if i != face]
    s, t = (point[i] for i in axes)
    delta = 0
    if not loose:
        # Exposed faces inside the cutout are recessed, shadowed stone.
        interior = point[face] < 2.999
        if interior:
            color = ((49, 61, 73), (76, 91, 103), (106, 120, 129))[face]
        # Fine, low-contrast joints; no dense random stipple.
        if min(s % 1, t % 1) < .11:
            delta = -10 if not interior else -7
    # Sparse deterministic chips, with much less contrast than the silhouette.
    h = ((int(s*7)*73856093) ^ (int(t*7)*19349663) ^ (seed*83492791)) & 1023
    if h < 12:
        delta += 7
    elif h > 1016:
        delta -= 6
    return tuple(max(0, min(255, c+delta)) for c in color)


def boundary_edges(cells):
    """Unique noncoplanar union edges, with adjacent outward normals.

    Shared coplanar cell seams disappear here, before any rasterization.
    """
    edges = {}
    for cell in sorted(cells):
        for axis in range(3):
            for sign in (-1, 1):
                neighbor = list(cell)
                neighbor[axis] += sign
                if tuple(neighbor) in cells:
                    continue
                other = [i for i in range(3) if i != axis]
                corners = []
                for u, v in ((0, 0), (1, 0), (1, 1), (0, 1)):
                    point = list(cell)
                    point[axis] += int(sign == 1)
                    point[other[0]] += u
                    point[other[1]] += v
                    corners.append(tuple(point))
                for a, b in zip(corners, corners[1:]+corners[:1]):
                    edge = tuple(sorted((a, b)))
                    edges.setdefault(edge, set()).add((axis, sign))
    return {edge: normals for edge, normals in edges.items() if len(normals) > 1}


def render():
    boxes = []
    for x in range(3):
        for y in range(3):
            for z in range(3):
                if (x, y, z) not in REMOVED:
                    boxes.append(((x, y, z), (x+1, y+1, z+1), x*71+y*17+z, False))
    # Both fragments are displaced beyond the silhouette, with clear air gaps.
    fragments = [((4.9, .45, 2.15), (5.9, 1.45, 3.15), 17, True),
                 ((4.55, 2.35, .05), (5.55, 3.35, 1.05), 39, True)]
    boxes.extend(fragments)
    ghost = [(cell, tuple(v+1 for v in cell), 0, False) for cell in REMOVED]
    pixels = [[BG for _ in range(SIZE)] for _ in range(SIZE)]
    depth = [[-1e9]*SIZE for _ in range(SIZE)]

    for py in range(SIZE):
        for px in range(SIZE):
            # Very faint opaque vignette, centered on the object group.
            radial = min(1, ((px-31)/40)**2 + ((py-31)/43)**2)
            color = tuple(max(0, c-round(3*radial)) for c in BG)
            # Small soft shadows fall down/right onto the background.
            shadows = [(25, 51, 18, 3, .21)]
            for low, high, _, _ in fragments:
                cx, cy = project(tuple((a+b)/2 for a, b in zip(low, high)))
                shadows.append((cx+1.5, cy+9, 6, 2, .33))
            darkness = 0
            for cx, cy, rx, ry, strength in shadows:
                darkness += strength*math.exp(-2*((px-cx)**2/rx**2+(py-cy)**2/ry**2))
            color = tuple(round(c*(1-min(.5, darkness))) for c in color)
            sx, sy = px+.5-ORIGIN[0], py+.5-ORIGIN[1]
            base = (sy/UNIT+sx/(2*UNIT), sy/UNIT-sx/(2*UNIT), 0)
            depth[py][px], hit = ray_hit(base, boxes)
            pixels[py][px] = stone(*hit) if hit else color

    # Outline the union, never the individual missing cells. The ghost depth
    # pass removes rear edges; the stone depth pass removes occluded edges.
    # Silhouette creases plus just the front vertical and top depth crease.
    strokes = {}
    for (a, b), normals in boundary_edges(REMOVED).items():
        signs = {sign for _, sign in normals}
        silhouette = len(signs) == 2
        front_crease = all(sign == 1 for _, sign in normals)
        if not (silhouette or front_crease):
            continue
        # The upper, light-facing contour alone receives the lighter gold.
        color = HIGHLIGHT if a[2] == b[2] == 3 and silhouette else GOLD
        ax, ay = project(a)
        bx, by = project(b)
        steps = max(1, math.ceil(max(abs(bx-ax), abs(by-ay))*8))
        for i in range(steps+1):
            t = i/steps
            p = tuple(a[k]+(b[k]-a[k])*t for k in range(3))
            sx, sy = project(p)
            x, y = math.floor(sx), math.floor(sy)
            if not (0 <= x < SIZE and 0 <= y < SIZE):
                continue
            # Check true edge depth against neighboring pixel-center rays;
            # a subpixel tolerance covers the 1px contour footprint only.
            if p[2] < depth[y][x]-.12:
                continue
            base = (p[0]-p[2]-.00001, p[1]-p[2]-.00001, 0)
            ghost_depth, _ = ray_hit(base, ghost)
            if p[2] < ghost_depth-.02:
                continue
            if color == HIGHLIGHT or (x, y) not in strokes:
                strokes[x, y] = color
    for (x, y), color in strokes.items():
        pixels[y][x] = color
    return pixels


def main():
    destination = Path(__file__).resolve().parent
    pixels = render()
    for size, factor in ((512, 8), (256, 4)):
        png(destination / f'logo-c2-{size}.png', pixels, factor)
    png(destination / 'logo-c2-preview-64.png', pixels)


if __name__ == '__main__':
    main()
