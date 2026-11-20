"""Generator for "steam_villager": a villager clad in steampunk armor with a Create-style backpack
(GeckoLib 4 entity, Forge 1.20.1). Reuses the armor generator's model / UV packing / painter / exporters.

Outputs (into ../output/ unless --out <assets/modid dir> is given), GeckoLib DefaultedEntityGeoModel layout
for ResourceLocation(modid, "steam_villager"):
  geo/entity/steam_villager.geo.json
  animations/entity/steam_villager.animation.json   (idle, walk, work, head_shake)
  textures/entity/steam_villager.png + steam_villager_glowmask.png (AutoGlowingGeoLayer)
and preview renders into ./preview/villager_*.png  (--anim also renders animation sheets).

Bones (vanilla villager proportions, 1 block = 16 px, faces -Z, wearer's right = -X):
  head > nose (respirator snout), gear_helmet*, gear_helmet_s*
  body > backpack > pack_cog (big cogwheel), pack_cog_s (small cog), gauge_needle, steam_l*, steam_r*
       > wrench_cog
  arms (crossed, vanilla pivot/tilt) > gear_rarm*, gear_larm*
  right_leg, left_leg
"""
import math
import os
import random
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_armor as ga  # noqa: E402
from gen_armor import Model, gear, shade, mix, clamp, bone_matrices, render  # noqa: E402

NAME = "steam_villager"

# ============================================================================ materials
ROBE = (106, 74, 50)
ga.PAL.update({"robe": ROBE, "robe_dark": (78, 52, 36)})
ga.NO_BEVEL |= {"robe", "robe_dark"}
_paint_base = ga.paint_face


def paint_face(px, gx, rect, mat, face, cube, rng):
    _paint_base(px, gx, rect, mat, face, cube, rng)
    x0, y0, w, h = rect

    def get(u, v):
        return px[x0 + u, y0 + v][:3]

    def put(u, v, c):
        if 0 <= u < w and 0 <= v < h:
            px[x0 + u, y0 + v] = tuple(clamp(t) for t in c) + (255,)

    if mat in ("robe", "robe_dark"):                     # twill weave, soft seams, darker hem line
        for v in range(h):
            for u in range(w):
                c = get(u, v)
                if (u + v) % 4 == 0:
                    c = shade(c, 0.9)
                if face not in ("up", "down") and (u in (0, w - 1) or v in (0, h - 1)):
                    c = shade(c, 0.82)
                put(u, v, c)
        if mat == "robe" and face == "north" and w >= 8 and h >= 8:
            for v in range(h):                            # front opening of the robe
                put(w // 2, v, shade(get(w // 2, v), 0.72))

ga.paint_face = paint_face


# ============================================================================ model
def build():
    m = Model()
    Model._mirrored = set()
    GEARS, STEAM = [], []

    # ------------------------------------------------------------------ HEAD: closed helmet, full face mask,
    # goggle eyes (glowing), respirator snout over the villager nose - no skin visible
    H = "head"
    m.bone(H, None, (0, 24, 0))
    m.box(H, -4.5, 30.4, -4.6, 4.5, 34.5, 4.6, "iron_plate")                  # helmet shell
    m.box(H, -4.0, 34.5, -4.0, 4.0, 35.1, 4.0, "iron")                        # dome
    m.box(H, -0.8, 34.5, -4.9, 0.8, 35.8, 4.8, "brass_plate")                 # crest
    m.box(H, -4.75, 30.1, -4.85, 4.75, 30.8, 4.75, "copper")                  # brim
    m.box(H, -4.5, 23.4, 4.0, 4.5, 30.1, 4.7, "iron_plate")                   # neck guard
    m.box(H, -4.45, 23.4, -4.6, 4.45, 30.1, -4.1, "brass_plate")              # face mask
    m.box(H, -4.6, 23.0, -4.75, 4.6, 23.8, 4.75, "copper")                    # mask rim / chin band
    m.box(H, -4.65, 28.3, -4.7, 4.65, 29.5, 4.75, "leather")                  # goggle strap
    m.box(H, -0.7, 28.6, -5.1, 0.7, 29.2, -4.6, "copper")                     # goggle bridge
    for sx in (-1, 1):
        x0, x1 = sorted((sx * 4.1, sx * 4.6))
        m.box(H, x0, 23.4, -4.3, x1, 30.1, 4.6, "brass_plate")                 # cheek guards
        x0, x1 = sorted((sx * 0.7, sx * 3.7))
        m.box(H, x0, 27.8, -5.2, x1, 30.0, -4.6, "brass")                      # goggle rings
        x0, x1 = sorted((sx * 1.05, sx * 3.35))
        m.box(H, x0, 28.15, -5.3, x1, 29.65, -5.2, "glass")                    # lenses (glow)
        x0, x1 = sorted((sx * 1.6, sx * 3.4))
        m.box(H, x0, 24.0, -6.0, x1, 26.4, -4.6, "copper")                     # filter canisters
        x0, x1 = sorted((sx * 1.5, sx * 1.7))
        m.box(H, x0, 24.7, -5.6, x1, 25.7, -5.0, "copper")
    GEARS.append((gear(m, "gear_helmet", H, (-4.95, 27.2, 1.2), "x", 1.7), "x", 90))
    GEARS.append((gear(m, "gear_helmet_s", H, (4.95, 27.8, 1.8), "x", 1.2), "x", -127.5))
    N = "nose"                                                                # respirator snout over the nose
    m.bone(N, H, (0, 26, -4))
    m.box(N, -1.5, 22.6, -6.6, 1.5, 27.6, -4.4, "iron")
    m.box(N, -1.7, 27.0, -6.8, 1.7, 27.6, -4.4, "iron_dark")                  # snout brow
    m.box(N, -1.15, 23.0, -6.9, 1.15, 25.6, -6.6, "grill")                    # breathing grill
    m.box(N, -1.7, 22.4, -6.8, 1.7, 23.0, -4.4, "copper")                     # snout rim

    # ------------------------------------------------------------------ BODY: robe under brass + iron plate
    B = "body"
    m.bone(B, None, (0, 24, 0))
    m.box(B, -4, 12, -3, 4, 24, 3, "robe")                                    # torso
    m.box(B, -4.4, 6.0, -3.3, 4.4, 13.0, 3.3, "robe")                         # robe skirt
    m.box(B, -4.55, 6.0, -3.45, 4.55, 6.9, 3.45, "robe_dark")                 # hem
    m.box(B, -4.6, 15.6, -3.7, 4.6, 24.0, -3.0, "brass_plate")                # breastplate
    m.box(B, -4.6, 15.0, 3.0, 4.6, 24.0, 3.7, "iron_plate")                   # back plate
    m.box(B, -4.4, 23.2, -3.8, 4.4, 24.6, 3.8, "iron_dark")                   # gorget
    m.box(B, -4.7, 12.8, -3.7, 4.7, 14.6, 3.7, "leather")                     # belt
    m.box(B, -1.2, 12.6, -3.95, 1.2, 14.8, -3.7, "copper_plate")              # buckle
    m.box(B, -4.0, 8.6, 3.3, 4.0, 12.8, 3.75, "iron_plate")                   # back tasset
    for sx in (-1, 1):
        x0, x1 = sorted((sx * 0.3, sx * 4.4))
        m.box(B, x0, 8.0, -3.75, x1, 12.8, -3.3, "iron_plate")                 # front tassets
        x0, x1 = sorted((sx * 4.4, sx * 4.8))
        m.box(B, x0, 8.0, -2.8, x1, 12.8, 2.8, "brass_plate")                  # side tassets
        x0, x1 = sorted((sx * 1.6, sx * 3.0))
        m.box(B, x0, 15.6, -3.9, x1, 24.0, -3.7, "leather")                    # backpack straps
        m.box(B, x0 - 0.1, 19.6, -4.05, x1 + 0.1, 20.4, -3.6, "brass")         # strap buckles
    m.box(B, 4.7, 11.4, -1.5, 5.6, 14.2, 1.5, "leather")                      # hip pouch (left)
    m.box(B, 4.6, 13.4, -1.6, 5.7, 14.4, 1.6, "leather_dark")
    # Create wrench hanging on the right hip: brass handle + little cog head
    m.box(B, -5.2, 8.9, -0.3, -4.7, 13.0, 0.3, "brass")
    m.box(B, -5.3, 12.0, -0.4, -4.6, 12.6, 0.4, "leather")
    GEARS.append((gear(m, "wrench_cog", B, (-4.95, 8.3, 0.0), "x", 0.9, thick=0.5), "x", 0))

    # ------------------------------------------------------------------ BACKPACK: Create brass casing + copper tanks
    P = "backpack"
    m.bone(P, B, (0, 18, 4))
    m.box(P, -3.7, 12.8, 3.7, 3.7, 23.6, 7.6, "brass_casing")                 # housing
    m.box(P, -3.95, 23.6, 3.5, 3.95, 24.4, 7.85, "andesite")                  # top trim
    m.box(P, -3.95, 12.2, 3.5, 3.95, 13.0, 7.85, "andesite")                  # bottom trim
    m.box(P, -0.45, 17.75, 7.6, 0.45, 18.65, 8.8, "shaft")                    # cog shaft
    for sx in (-1, 1):
        x0, x1 = sorted((sx * 3.7, sx * 5.9))
        m.box(P, x0, 13.0, 4.2, x1, 24.6, 7.2, "copper")                       # copper tanks
        x0, x1 = sorted((sx * 3.55, sx * 6.05))
        m.box(P, x0, 14.4, 4.05, x1, 15.0, 7.35, "brass")                      # tank bands
        m.box(P, x0, 22.4, 4.05, x1, 23.0, 7.35, "brass")
        x0, x1 = sorted((sx * 4.1, sx * 5.5))
        m.box(P, x0, 24.6, 5.0, x1, 25.3, 6.4, "iron_dark")                    # tank caps
    m.box(P, 4.2, 25.3, 5.2, 5.4, 30.0, 6.2, "chimney", faces={"up": "chimney_hole"})    # exhaust (left)
    m.box(P, 4.0, 27.6, 5.0, 5.6, 28.2, 6.4, "copper")
    m.box(P, -5.4, 25.3, 5.2, -4.2, 28.2, 6.2, "chimney", faces={"up": "chimney_hole"})  # whistle (right)
    m.box(P, -5.6, 26.6, 5.0, -4.0, 27.2, 6.4, "copper")
    m.box(P, -5.3, 11.6, 4.8, -4.5, 13.0, 5.6, "pipe")                        # feed hose down...
    m.box(P, -5.3, 11.6, 1.0, -4.5, 12.4, 4.8, "pipe")                        # ...and forward to the belt
    # pressure gauge on the right tank
    m.box(P, -5.7, 19.4, 7.2, -3.9, 21.2, 7.45, "copper_plate")
    m.box(P, -5.5, 19.6, 7.45, -4.1, 21.0, 7.5, "gauge")
    m.bone("gauge_needle", P, (-4.8, 20.3, 7.5))
    m.box("gauge_needle", -4.86, 20.3, 7.5, -4.74, 20.9, 7.56, "needle")
    # big Create cogwheel driving a small one
    GEARS.append((gear(m, "pack_cog", P, (0, 18.2, 8.1), "z", 3.6, thick=0.6, mat="cog_wood", hub="cog_hub"), "z", -90))
    GEARS.append((gear(m, "pack_cog_s", P, (3.2, 22.0, 8.0), "z", 1.4, mat="cog_wood", hub="cog_hub"), "z", 231))
    for tag, bx, top in (("l", 4.8, 30.0), ("r", -4.8, 28.2)):
        m.bone(f"steam_{tag}", P, (bx, top, 5.7))
        for i in range(3):
            n, s = f"steam_{tag}{i + 1}", (0.9, 1.2, 1.5)[i]
            m.bone(n, f"steam_{tag}", (bx, top + 0.5, 5.7))
            m.box(n, bx - s / 2, top + 0.2, 5.7 - s / 2, bx + s / 2, top + 0.2 + s, 5.7 + s / 2, "steam")
            STEAM.append(n)

    # ------------------------------------------------------------------ ARMS: crossed (vanilla pivot + tilt)
    A = "arms"
    m.bone(A, None, (0, 21, -1), (-43, 0, 0))
    m.box(A, -4, 15, -3, 4, 19, 1, "iron_dark")                               # mail under the gauntlets
    for sx, tag, speed in ((-1, "r", 112.5), (1, "l", -112.5)):
        x0, x1 = sorted((sx * 4, sx * 8))
        m.box(A, x0, 15, -3, x1, 23, 1, "robe")                                # sleeves
        x0, x1 = sorted((sx * 3.6, sx * 8.6))
        m.box(A, x0, 20.0, -3.6, x1, 23.6, 1.6, "brass_plate")                 # pauldrons
        x0, x1 = sorted((sx * 3.4, sx * 8.8))
        m.box(A, x0, 19.6, -3.8, x1, 20.4, 1.8, "copper")                      # pauldron rim
        x0, x1 = sorted((sx * 3.65, sx * 8.35))
        m.box(A, x0, 15.6, -3.35, x1, 18.8, 1.35, "iron_plate")                # bracers
        x0, x1 = sorted((sx * 3.5, sx * 8.5))
        m.box(A, x0, 18.6, -3.5, x1, 19.2, 1.5, "copper")                      # bracer band
        x0, x1 = sorted((sx * 0.0, sx * 4.3))
        m.box(A, x0, 14.7, -3.3, x1, 19.3, 1.3, "iron")                        # gauntlets (fully cover hands)
        x0, x1 = sorted((sx * 0.3, sx * 4.0))
        m.box(A, x0, 14.5, -3.55, x1, 15.4, -2.9, "iron_dark")                 # knuckles
        GEARS.append((gear(m, f"gear_{tag}arm", A, (sx * 8.85, 21.8, -1.0), "x", 1.4), "x", speed))

    # ------------------------------------------------------------------ LEGS: greaves + boots
    L = "right_leg"
    m.bone(L, None, (-2, 12, 0))
    m.box(L, -4, 0, -2, 0, 12, 2, "robe_dark")                                # trousers
    m.box(L, -4.3, 3.8, -2.3, 0.3, 7.4, 2.3, "iron_plate")                    # greave
    m.box(L, -3.8, 4.6, -2.8, -0.2, 7.0, -2.3, "brass_plate")                 # knee cop
    m.box(L, -4.4, 0.4, -2.6, 0.4, 3.8, 2.5, "iron_plate")                    # boot
    m.box(L, -4.6, -0.4, -3.4, 0.6, 0.5, 2.7, "sole")                         # sole
    m.box(L, -4.2, 0.5, -3.5, 0.2, 2.2, -2.6, "copper_plate")                 # toe cap
    m.box(L, -4.6, 3.4, -2.75, 0.6, 4.1, 2.65, "copper")                      # boot band
    m.box(L, -2.5, 0.5, 2.5, -1.5, 3.5, 3.1, "rod")                           # heel piston
    m.mirror(L, lambda n: n.replace("right", "left"))

    m.variant = "villager"
    m.gears, m.steam = GEARS, STEAM
    return m


# ============================================================================ animations
def make_animations(m):
    TAU = math.tau

    def add(out, bone, rot=(0, 0, 0), pos=(0, 0, 0), scl=None):
        r, p, s = out.get(bone, ([0, 0, 0], [0, 0, 0], [1, 1, 1]))
        out[bone] = ([r[i] + rot[i] for i in range(3)], [p[i] + pos[i] for i in range(3)],
                     [s[i] * scl[i] for i in range(3)] if scl else s)

    def gears(out, t, mult=1.0):
        for bone, axis, speed in m.gears:
            a = speed * t * mult
            add(out, bone, {"x": (a, 0, 0), "y": (0, a, 0), "z": (0, 0, a)}[axis])

    def steam(out, t, period, rise):
        for i, n in enumerate(m.steam):
            ph = ((t / period) + (i % 3) / 3.0) % 1.0
            k = math.sin(math.pi * ph) * 1.3
            add(out, n, pos=(0.8 * math.sin(TAU * ph + i), rise * ph, 0.8 * ph), scl=(k, k, k))

    def idle(t):            # loop 4s: cogs turn, needle trembles, exhaust puffs, slow breathing
        out = {}
        gears(out, t)
        add(out, "gauge_needle", (0, 0, -25 + 10 * math.sin(TAU * t / 4) + 3 * math.sin(TAU * t * 3)))
        steam(out, t, 2.0, 6.0)
        b = 0.15 * math.sin(TAU * t / 4)
        add(out, "arms", pos=(0, b, 0))
        add(out, "backpack", pos=(0, b * 0.6, 0))
        return out

    def walk(t):            # loop 1s: legs swing, pack bounces, cogs faster
        out = {}
        gears(out, t, 2.0)
        sw = 28 * math.sin(TAU * t)
        add(out, "right_leg", (sw, 0, 0))
        add(out, "left_leg", (-sw, 0, 0))
        bob = 0.35 * abs(math.sin(TAU * t))
        add(out, "head", pos=(0, bob, 0))
        add(out, "arms", (2 * math.sin(TAU * t * 2), 0, 0), pos=(0, bob, 0))
        add(out, "body", pos=(0, bob, 0))
        add(out, "backpack", (1.5 * math.sin(TAU * t * 2), 0, 0))
        add(out, "gauge_needle", (0, 0, 5 + 8 * math.sin(TAU * t * 2)))
        steam(out, t, 0.8, 7.0)
        return out

    def work(t):            # loop 2s: busy at a workstation - cogs race, needle in the red, thick steam
        out = {}
        gears(out, t, 3.0)
        add(out, "gauge_needle", (0, 0, 55 + 6 * math.sin(TAU * t * 4)))
        steam(out, t, 0.5, 9.0)
        add(out, "head", (8 + 4 * math.sin(TAU * t), 0, 0))
        add(out, "arms", (-6 * abs(math.sin(TAU * t)), 0, 0))
        return out

    def head_shake(t):      # 1s: the villager "no" when a trade is refused
        out = {}
        gears(out, t)
        add(out, "head", (0, 0, 18 * math.sin(TAU * t * 2.5) * (1 - t)))
        add(out, "gauge_needle", (0, 0, -25))
        return out

    return {
        "idle": dict(fn=idle, length=4.0, dt=0.1, loop=True),
        "walk": dict(fn=walk, length=1.0, dt=0.05, loop=True),
        "work": dict(fn=work, length=2.0, dt=0.05, loop=True),
        "head_shake": dict(fn=head_shake, length=1.0, dt=0.05, loop=False),
    }


ga.make_animations = make_animations


# ============================================================================ main
def main():
    ga.NAME, ga.TEX = NAME, 256
    m = build()
    tex, glow, uv, used = ga.pack_and_paint(m, random.Random(77))
    mats = bone_matrices(m)
    print(f"bones {len(m.bones)}, cubes {sum(len(b['cubes']) for b in m.bones)}, texture {ga.TEX}, rows used {used}")

    out = ga.OUT
    dirs = {k: os.path.join(out, k, "entity") for k in ("geo", "animations", "textures")}
    for d in dirs.values():
        os.makedirs(d, exist_ok=True)
    os.makedirs(ga.PREVIEW, exist_ok=True)
    ga.export_geo(m, uv, os.path.join(dirs["geo"], f"{NAME}.geo.json"))
    tex.save(os.path.join(dirs["textures"], f"{NAME}.png"))
    glow.save(os.path.join(dirs["textures"], f"{NAME}_glowmask.png"))
    anims = ga.export_animations(m, os.path.join(dirs["animations"], f"{NAME}.animation.json"))

    P = ga.PREVIEW
    for vname, eye in (("front", (0, 0.15, -1)), ("34", (-0.8, 0.35, -1)), ("back", (0, 0.2, 1)),
                       ("back34", (0.8, 0.3, 1)), ("side", (1, 0.05, 0))):
        render(m, tex, glow, uv, mats, eye, scale=16, title=vname).save(os.path.join(P, f"villager_{vname}.png"))
    render(m, tex, glow, uv, bone_matrices(m, anims["walk"]["fn"](0.25)), (-0.8, 0.35, -1), scale=16,
           title="walk").save(os.path.join(P, "villager_walk.png"))
    tex.resize((ga.TEX * 3, ga.TEX * 3), Image.NEAREST).save(os.path.join(P, "villager_texture_x3.png"))
    if "--anim" in sys.argv:
        bounds = (np.array([-14, -2, 0]), np.array([14, 40, 0]))
        for name, a in anims.items():
            n = 8
            times = [a["length"] * i / n for i in range(n)] if a["loop"] is True else \
                [a["length"] * i / (n - 1) for i in range(n)]
            eye = (0.8, 0.3, 1) if name == "work" else (-0.8, 0.35, -1)
            frames = [render(m, tex, glow, uv, bone_matrices(m, a["fn"](t)), eye, scale=8,
                             title=f"{name} t={t:.2f}", bounds=bounds) for t in times]
            W, Hh = frames[0].size
            sh = Image.new("RGB", (W * 4, Hh * 2))
            for i, f in enumerate(frames):
                sh.paste(f, ((i % 4) * W, (i // 4) * Hh))
            sh.save(os.path.join(P, f"anim_villager_{name}.png"))


if __name__ == "__main__":
    main()
