"""Generates the particle textures and particle definitions of the Avatar Bending mod.

All particles are white/grey so the game can tint them with any element color.

Run from the AvatarBending folder:  python3 tools/gen_particles.py
Requires numpy and Pillow.
"""
import json
import math
import os

import numpy as np
from PIL import Image, ImageFilter

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "avatarbending")
rng = np.random.default_rng(7)


def save(img, name):
    p = os.path.join(ROOT, "textures", "particle", name + ".png")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


def grid(size):
    c = (size - 1) / 2
    y, x = np.mgrid[0:size, 0:size]
    return (x - c) / c, (y - c) / c


def rgba(gray, alpha):
    g = np.clip(gray, 0, 1)
    a = np.clip(alpha, 0, 1)
    arr = np.stack([g, g, g, a], axis=-1)
    return Image.fromarray((arr * 255).astype(np.uint8), "RGBA")


def down(img, size):
    return img.resize((size, size), Image.LANCZOS)


def smooth_noise(size, scale, octaves=4):
    out = np.zeros((size, size))
    amp = 1.0
    total = 0
    for o in range(octaves):
        cells = max(2, int(scale * 2 ** o))
        base = rng.random((cells + 1, cells + 1))
        img = Image.fromarray((base * 255).astype(np.uint8)).resize((size, size), Image.BICUBIC)
        out += np.asarray(img) / 255 * amp
        total += amp
        amp *= 0.5
    return out / total


def glow():
    x, y = grid(128)
    r = np.sqrt(x * x + y * y)
    a = np.clip(1 - r, 0, 1) ** 2.2
    core = np.clip(1 - r * 2.2, 0, 1) ** 2
    save(down(rgba(np.ones_like(a) * 0.85 + core * 0.15, a), 32), "glow")


def spark():
    x, y = grid(128)
    r = np.sqrt(x * x + y * y)
    rays = np.exp(-np.abs(x) * 22) * np.exp(-np.abs(y) * 2.2) + np.exp(-np.abs(y) * 22) * np.exp(-np.abs(x) * 2.2)
    diag = (np.exp(-np.abs(x - y) * 30) + np.exp(-np.abs(x + y) * 30)) * np.exp(-r * 4) * 0.5
    core = np.clip(1 - r * 3, 0, 1) ** 1.5
    a = np.clip(rays + diag + core, 0, 1)
    save(down(rgba(np.ones_like(a), a), 32), "spark")


def wind(i):
    size = 128
    x, y = grid(size)
    r = np.sqrt(x * x + y * y)
    ang = np.arctan2(y, x)
    start = rng.uniform(0, 2 * np.pi)
    length = rng.uniform(3.0, 4.2)
    rel = (ang - start) % (2 * np.pi)
    along = np.where(rel < length, rel / length, -1)
    radius = 0.55 + 0.25 * along  # spiral outwards
    thickness = 0.10 * np.sin(np.clip(along, 0, 1) * np.pi) + 0.01
    band = np.exp(-((r - radius) / np.maximum(thickness, 1e-3)) ** 2)
    a = np.where(along >= 0, band * np.sin(np.clip(along, 0, 1) * np.pi) ** 0.7, 0)
    img = rgba(np.ones_like(a), a * 0.95).filter(ImageFilter.GaussianBlur(1.2))
    save(down(img, 32), f"wind_{i}")


def ring():
    x, y = grid(256)
    r = np.sqrt(x * x + y * y)
    band = np.exp(-((r - 0.86) / 0.07) ** 2)
    inner = np.exp(-((r - 0.86) / 0.3) ** 2) * 0.25 * (r < 0.86)
    save(down(rgba(np.ones_like(r), np.clip(band + inner, 0, 1)), 64), "ring")


def sigil():
    size = 512
    x, y = grid(size)
    r = np.sqrt(x * x + y * y)
    ang = np.arctan2(y, x)
    a = np.zeros_like(r)
    for rad, w in ((0.95, 0.012), (0.88, 0.006), (0.62, 0.01), (0.56, 0.005), (0.25, 0.008)):
        a += np.exp(-((r - rad) / w) ** 2)
    # Rune ticks between the outer rings.
    ticks = (np.cos(ang * 48) > 0.92) & (r > 0.885) & (r < 0.945)
    a += ticks * 0.9
    # Four element symbols at the cardinal points (small circles) and connecting square.
    for k in range(4):
        cx = 0.75 * math.cos(k * math.pi / 2 + math.pi / 4)
        cy = 0.75 * math.sin(k * math.pi / 2 + math.pi / 4)
        d = np.sqrt((x - cx) ** 2 + (y - cy) ** 2)
        a += np.exp(-((d - 0.1) / 0.01) ** 2) + (d < 0.04) * 0.9
    # Two overlapping squares (8-point star).
    for rot in (0, math.pi / 4):
        xr = x * math.cos(rot) - y * math.sin(rot)
        yr = x * math.sin(rot) + y * math.cos(rot)
        edge = np.maximum(np.abs(xr), np.abs(yr))
        a += np.exp(-((edge - 0.44) / 0.008) ** 2) * (r < 0.64)
    a = np.clip(a, 0, 1)
    glow_img = rgba(np.ones_like(a), a)
    blurred = glow_img.filter(ImageFilter.GaussianBlur(6))
    combined = Image.alpha_composite(blurred, glow_img)
    save(down(combined, 128), "sigil")


def beam():
    w, h = 32, 32
    u = np.linspace(-1, 1, w)
    profile = np.exp(-(u / 0.35) ** 2) * 0.8 + np.exp(-(u / 0.12) ** 2) * 0.4
    a = np.tile(np.clip(profile, 0, 1), (h, 1))
    core = np.tile(np.exp(-(u / 0.18) ** 2), (h, 1))
    save(rgba(0.75 + 0.25 * core, a), "beam")


def ember():
    x, y = grid(64)
    r = np.sqrt(x * x + y * y)
    a = np.clip(1 - r, 0, 1) ** 3 + np.clip(1 - r * 2.5, 0, 1)
    save(down(rgba(np.ones_like(r), np.clip(a, 0, 1)), 16), "ember")


def flame(i, frames):
    size = 128
    x, y = grid(size)
    phase = i / frames
    n = smooth_noise(size, 3, 4)
    # Teardrop flame: wide at the bottom, pointy at the top, wobbling with the frame phase.
    yy = (y + 1) / 2  # 0 top .. 1 bottom
    sway = 0.12 * np.sin(yy * 6 + phase * 2 * np.pi) * (1 - yy)
    width = 0.15 + 0.6 * yy ** 0.8
    shape = 1 - np.abs(x - sway) / np.maximum(width, 1e-3)
    bottom = np.clip((1 - yy) * 6, 0, 1) if False else 1
    taper = np.clip((yy - 0.05 - 0.1 * np.sin(phase * 2 * np.pi + x * 4)) * 2.5, 0, 1)
    body = np.clip(shape, 0, 1) * taper * np.clip((1.0 - yy) * 4 + 0.3, 0, 1) ** 0.5
    body *= 0.75 + 0.5 * n
    body = np.clip(body * 1.6, 0, 1)
    round_bottom = np.clip(1 - np.sqrt(x ** 2 + ((y - 0.45) * 1.6) ** 2), 0, 1)
    a = np.clip(np.maximum(body, round_bottom * 0.9) ** 1.2, 0, 1)
    gray = np.clip(0.55 + 0.45 * (a ** 2) + 0.3 * yy, 0, 1)
    img = rgba(gray, a).filter(ImageFilter.GaussianBlur(1.5))
    save(down(img, 32), f"flame_{i}")


def droplet():
    size = 64
    x, y = grid(size)
    yy = y + 0.25
    d = np.sqrt(x ** 2 + np.clip(yy, 0, None) ** 2)
    top = np.abs(x) < np.clip(-yy, 0, 1) * 0 + (1 + yy) * 0.55
    shape = np.where(yy > 0, d < 0.6, top & (yy > -0.85))
    a = shape.astype(float)
    hl = np.exp(-(((x + 0.2) / 0.12) ** 2 + ((y - 0.05) / 0.18) ** 2))
    gray = np.clip(0.65 + 0.35 * hl, 0, 1)
    img = rgba(gray, a * 0.9).filter(ImageFilter.GaussianBlur(1.0))
    save(down(img, 16), "droplet")


def dust(i):
    size = 128
    x, y = grid(size)
    r = np.sqrt(x * x + y * y)
    n = smooth_noise(size, 2.5, 5)
    a = np.clip(1 - r * (0.9 + 0.4 * n), 0, 1) ** 1.3 * (0.6 + 0.6 * n)
    gray = 0.75 + 0.25 * n
    img = rgba(gray, np.clip(a, 0, 1)).filter(ImageFilter.GaussianBlur(2))
    save(down(img, 32), f"dust_{i}")


def shard(i):
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    from PIL import ImageDraw
    d = ImageDraw.Draw(img)
    pts = []
    for k in range(5):
        ang = k * 2 * math.pi / 5 + rng.uniform(-0.4, 0.4)
        rad = rng.uniform(14, 30)
        pts.append((32 + math.cos(ang) * rad, 32 + math.sin(ang) * rad * 0.7))
    d.polygon(pts, fill=(235, 235, 235, 255))
    d.line(pts + [pts[0]], fill=(255, 255, 255, 255), width=3)
    save(down(img, 16), f"shard_{i}")


PARTICLES = {
    "glow": ["glow"],
    "spark": ["spark"],
    "wind": [f"wind_{i}" for i in range(4)],
    "ring": ["ring"],
    "sigil": ["sigil"],
    "beam": ["beam"],
    "ember": ["ember"],
    "flame": [f"flame_{i}" for i in range(6)],
    "droplet": ["droplet"],
    "dust": [f"dust_{i}" for i in range(3)],
    "shard": [f"shard_{i}" for i in range(3)],
}


def main():
    glow()
    spark()
    for i in range(4):
        wind(i)
    ring()
    sigil()
    beam()
    ember()
    for i in range(6):
        flame(i, 6)
    droplet()
    for i in range(3):
        dust(i)
    for i in range(3):
        shard(i)
    for name, textures in PARTICLES.items():
        p = os.path.join(ROOT, "particles", name + ".json")
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, "w") as f:
            json.dump({"textures": [f"avatarbending:{t}" for t in textures]}, f, indent=2)
    print("Particles written to", os.path.normpath(ROOT))


if __name__ == "__main__":
    main()
