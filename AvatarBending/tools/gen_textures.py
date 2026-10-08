"""Generates every texture of the Avatar Bending mod.

Run from the AvatarBending folder:  python3 tools/gen_textures.py
Requires Pillow.
"""
import math
import os

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "avatarbending")

AIR = (220, 235, 255)
WATER = (46, 155, 255)
EARTH = (93, 187, 63)
FIRE = (255, 90, 31)
AVATAR = (125, 249, 255)


def path(*parts):
    p = os.path.join(ROOT, *parts)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    return p


def darken(c, f):
    return tuple(int(v * f) for v in c[:3])


def lighten(c, f):
    return tuple(int(v + (255 - v) * f) for v in c[:3])


# --------------------------------------------------------------------------- emblems

S = 256  # supersampled canvas, downscaled to 64


def badge(color):
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse((6, 6, S - 6, S - 6), fill=darken(color, 0.35) + (255,))
    d.ellipse((6, 6, S - 6, S - 6), outline=lighten(color, 0.2) + (255,), width=14)
    d.ellipse((26, 26, S - 26, S - 26), outline=darken(color, 0.6) + (255,), width=4)
    return img, d


def thick_polyline(d, pts, width, fill):
    for a, b in zip(pts, pts[1:]):
        d.line([a, b], fill=fill, width=width)
    r = width / 2
    for x, y in pts:
        d.ellipse((x - r, y - r, x + r, y + r), fill=fill)


def emblem_air():
    img, d = badge((120, 170, 230))
    c = S / 2
    fill = lighten(AIR, 0.6) + (255,)
    for k in range(3):
        base = k * 2 * math.pi / 3 + math.pi / 2
        pts = []
        for i in range(60):
            t = i / 59
            ang = base + t * 3.6
            r = 18 + t * 70
            pts.append((c + math.cos(ang) * r, c - math.sin(ang) * r))
        thick_polyline(d, pts, 16, fill)
    d.ellipse((c - 16, c - 16, c + 16, c + 16), fill=fill)
    return img


def emblem_water():
    img, d = badge(WATER)
    c = S / 2
    fill = lighten(WATER, 0.75) + (255,)
    # Crescent moon.
    moon = Image.new("L", (S, S), 0)
    md = ImageDraw.Draw(moon)
    md.ellipse((c - 52, 44, c + 52, 148), fill=255)
    md.ellipse((c - 30, 30, c + 66, 126), fill=0)
    img.paste(Image.new("RGBA", (S, S), fill), (0, 0), moon)
    # Three waves.
    for row in range(3):
        y0 = 158 + row * 22
        pts = []
        for i in range(80):
            x = 54 + i * (148 / 79)
            pts.append((x, y0 + math.sin(i / 79 * math.pi * 3) * 8))
        thick_polyline(d, pts, 10, fill)
    return img


def emblem_earth():
    img, d = badge(EARTH)
    c = S / 2
    fill = lighten(EARTH, 0.7) + (255,)
    dark = darken(EARTH, 0.35) + (255,)
    # Earth Kingdom style: square with a circle and an inner square.
    d.rectangle((c - 66, c - 66, c + 66, c + 66), fill=fill)
    d.ellipse((c - 52, c - 52, c + 52, c + 52), fill=dark)
    d.rectangle((c - 30, c - 30, c + 30, c + 30), fill=fill)
    d.rectangle((c - 14, c - 14, c + 14, c + 14), fill=dark)
    return img


def emblem_fire():
    img, d = badge(FIRE)
    c = S / 2
    outer = lighten((255, 150, 40), 0.25) + (255,)
    inner = (255, 240, 190, 255)

    def flame(scale, color, cy):
        # Rounded base with three flickering tongues (the middle one tallest).
        pts = []
        for i in range(31):
            a = math.pi + i / 30 * math.pi
            pts.append((math.cos(a) * 0.62, -0.3 + math.sin(a) * 0.62))
        pts += [(0.64, 0.0), (0.52, 0.32), (0.62, 0.8), (0.32, 0.45), (0.0, 1.3),
                (-0.32, 0.45), (-0.62, 0.8), (-0.52, 0.32), (-0.64, 0.0)]
        d.polygon([(c + x * scale, cy - y * scale) for x, y in pts], fill=color)

    flame(80, outer, c + 22)
    flame(42, inner, c + 44)
    return img


def emblem_avatar():
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    colors = [AIR, FIRE, EARTH, WATER]  # top, right, bottom, left
    for i, col in enumerate(colors):
        start = -135 + i * 90
        d.pieslice((6, 6, S - 6, S - 6), start, start + 90, fill=col + (255,))
    d.ellipse((6, 6, S - 6, S - 6), outline=(255, 255, 255, 255), width=10)
    d.ellipse((66, 66, S - 66, S - 66), fill=(20, 30, 50, 255), outline=(255, 255, 255, 255), width=10)
    c = S / 2
    # Air nomad arrow in the middle.
    d.polygon([(c - 22, c - 18), (c + 22, c - 18), (c, c + 26)], fill=lighten(AVATAR, 0.4) + (255,))
    d.rectangle((c - 7, c - 44, c + 7, c - 16), fill=lighten(AVATAR, 0.4) + (255,))
    return img


def save_emblem(img, name):
    small = img.resize((64, 64), Image.LANCZOS)
    small.save(path("textures", "gui", f"emblem_{name}.png"))
    return img


# --------------------------------------------------------------------------- item

def avatar_spirit():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    cx = cy = 7.5
    quad = [AIR, FIRE, EARTH, WATER]
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, y - cy
            dist = math.hypot(dx, dy)
            if dist > 7.2:
                continue
            if dist > 6.2:
                px[x, y] = (24, 30, 48, 255)
                continue
            ang = math.atan2(dx, -dy) + dist * 0.38  # swirl
            idx = int(((ang + math.pi / 4) % (2 * math.pi)) // (math.pi / 2))
            col = quad[idx]
            shade = 1.0 - 0.06 * (dx + dy)
            col = tuple(max(0, min(255, int(v * shade))) for v in col)
            if dist < 1.7:
                col = (255, 255, 255)
            elif dist < 2.8:
                col = lighten(AVATAR, 0.5)
            px[x, y] = col + (255,)
    # Specular highlight.
    px[5, 4] = (255, 255, 255, 255)
    px[4, 5] = (255, 255, 255, 230)
    img.save(path("textures", "item", "avatar_spirit.png"))


# --------------------------------------------------------------------------- glowing eyes / tattoos

def avatar_glow():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()
    eye = (255, 255, 255, 255)
    tattoo = (140, 230, 255, 255)
    # Eyes on the head's front face (8..16, 8..16).
    for x in (9, 10, 13, 14):
        px[x, 12] = eye
    # Arrow tattoo: shaft over the top of the head, head on the forehead, tail down the back.
    for y in range(0, 8):
        px[11, y] = tattoo
        px[12, y] = tattoo
    px[11, 8] = tattoo
    px[12, 8] = tattoo
    for x in range(10, 14):
        px[x, 9] = tattoo
    px[11, 10] = tattoo
    px[12, 10] = tattoo
    for y in range(8, 12):
        px[27, y] = tattoo
        px[28, y] = tattoo
    # Arrows down the outer side of both arms (x=41 is on the outer face for wide and slim arms).
    for y in range(20, 31):
        px[41, y] = tattoo
    for y in range(52, 63):
        px[41, y] = tattoo
    img.save(path("textures", "entity", "avatar_glow.png"))


# --------------------------------------------------------------------------- mod icon

def icon(emblems):
    size = 512
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    bg = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(bg)
    for r in range(size // 2, 0, -2):
        t = r / (size / 2)
        col = (int(10 + 30 * (1 - t)), int(20 + 70 * (1 - t)), int(45 + 120 * (1 - t)), 255)
        d.ellipse((size / 2 - r, size / 2 - r, size / 2 + r, size / 2 + r), fill=col)
    img.alpha_composite(bg)
    glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse((150, 150, 362, 362), fill=AVATAR + (140,))
    glow = glow.filter(ImageFilter.GaussianBlur(40))
    img.alpha_composite(glow)
    positions = {"air": (256, 92), "fire": (420, 256), "earth": (256, 420), "water": (92, 256)}
    for name, (x, y) in positions.items():
        e = emblems[name].resize((150, 150), Image.LANCZOS)
        img.alpha_composite(e, (x - 75, y - 75))
    center = emblems["avatar"].resize((200, 200), Image.LANCZOS)
    img.alpha_composite(center, (156, 156))
    img.resize((128, 128), Image.LANCZOS).save(path("icon.png"))


def main():
    emblems = {
        "air": save_emblem(emblem_air(), "air"),
        "water": save_emblem(emblem_water(), "water"),
        "earth": save_emblem(emblem_earth(), "earth"),
        "fire": save_emblem(emblem_fire(), "fire"),
        "avatar": save_emblem(emblem_avatar(), "avatar"),
    }
    avatar_spirit()
    avatar_glow()
    icon(emblems)
    print("Textures written to", os.path.normpath(ROOT))


if __name__ == "__main__":
    main()
