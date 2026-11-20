"""Generator for the steampunk "steam_armor" GeckoLib armor set (Forge 1.20.1, GeckoLib 4).

Outputs (into ../output/ unless --out <assets/modid dir> is given), in GeckoLib's
DefaultedItemGeoModel layout for ResourceLocation(modid, "armor/steam_armor"):
  geo/armor/steam_armor.geo.json
  animations/armor/steam_armor.animation.json
  textures/armor/steam_armor.png + steam_armor_glowmask.png (AutoGlowingGeoLayer)
  textures/item/steam_{helmet,chestplate,leggings,boots}.png + models/item/*.json
and preview renders into ./preview/.

Bones follow GeckoLib's armor template: armorHead, armorBody, armorRightArm, armorLeftArm,
armorRightLeg, armorLeftLeg, armorRightBoot, armorLeftBoot (vanilla pivots).
Units: 1 block = 16 px. Model faces -Z (north). Right side of the wearer = -X.
"""
import json
import math
import os
import random
import sys

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "output")
if "--out" in sys.argv:
    OUT = sys.argv[sys.argv.index("--out") + 1]
PREVIEW = os.path.join(HERE, "preview")
NAME = "steam_armor"
DENSITY = 2.0
TEX = 256  # grown automatically if the packing overflows


# ============================================================================ model + kinematics
class Model:
    def __init__(self):
        self.bones, self.by = [], {}

    def bone(self, name, parent, pivot, rot=(0, 0, 0)):
        assert name not in self.by, name
        b = dict(name=name, parent=parent, pivot=list(map(float, pivot)), rotation=list(map(float, rot)), cubes=[])
        self.bones.append(b)
        self.by[name] = b
        return b

    def cube(self, bone, origin, size, mat, faces=None):
        c = dict(origin=list(map(float, origin)), size=list(map(float, size)), mat=mat, faces=faces or {})
        self.by[bone]["cubes"].append(c)
        return c

    def box(self, bone, x0, y0, z0, x1, y1, z1, mat, faces=None):
        return self.cube(bone, (x0, y0, z0), (x1 - x0, y1 - y0, z1 - z0), mat, faces)

    def mirror(self, src, rename):
        """Copy bone `src` and its whole subtree mirrored across X=0."""
        kids = [b for b in self.bones if b["parent"] == src]
        b = self.by[src]
        nb = self.bone(rename(src), rename(b["parent"]) if b["parent"] and b["parent"] in self._mirrored else b["parent"],
                       (-b["pivot"][0], b["pivot"][1], b["pivot"][2]),
                       (b["rotation"][0], -b["rotation"][1], -b["rotation"][2]))
        self._mirrored.add(src)
        for c in b["cubes"]:
            o, s = c["origin"], c["size"]
            faces = dict(c["faces"])
            if "east" in faces or "west" in faces:
                faces["east"], faces["west"] = c["faces"].get("west"), c["faces"].get("east")
                faces = {k: v for k, v in faces.items() if v}
            self.cube(nb["name"], (-(o[0] + s[0]), o[1], o[2]), s, c["mat"], faces)
        for k in kids:
            self.mirror(k["name"], rename)
        return nb

    _mirrored = set()


def rot_matrix(fx, fy, fz):
    """GeckoLib/Bedrock file convention: actual right-handed angles (-x, -y, z), applied X then Y then Z."""
    ax, ay, az = np.radians([-fx, -fy, fz])
    cx, sx, cy, sy, cz, sz = math.cos(ax), math.sin(ax), math.cos(ay), math.sin(ay), math.cos(az), math.sin(az)
    rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return rz @ ry @ rx


def bone_matrices(model, anim=None, pose=None):
    mats = {}
    for b in model.bones:
        rot, pos, scl = np.array(b["rotation"]), np.zeros(3), np.ones(3)
        if pose and b["name"] in pose:
            rot = rot + np.array(pose[b["name"]])
        if anim and b["name"] in anim:
            r, p, s = anim[b["name"]]
            rot = rot + np.array(r)
            pos = np.array(p, dtype=float) * np.array([-1, 1, 1])
            scl = np.array(s, dtype=float)
        p = np.array(b["pivot"])
        m = np.eye(4)
        m[:3, :3] = rot_matrix(*rot) @ np.diag(scl)
        t1, t2, tp = np.eye(4), np.eye(4), np.eye(4)
        t1[:3, 3], t2[:3, 3], tp[:3, 3] = p, -p, pos
        mats[b["name"]] = (mats[b["parent"]] if b["parent"] else np.eye(4)) @ (tp @ t1 @ m @ t2)
    return mats


def cube_corners(c):
    x0, y0, z0 = c["origin"]
    sx, sy, sz = c["size"]
    return x0, y0, z0, x0 + sx, y0 + sy, z0 + sz


FACE_CORNERS = {  # (TL, TR, BL) as seen from outside
    "north": lambda a, b, c, d, e, f: ((d, e, c), (a, e, c), (d, b, c)),
    "south": lambda a, b, c, d, e, f: ((a, e, f), (d, e, f), (a, b, f)),
    "east": lambda a, b, c, d, e, f: ((d, e, f), (d, e, c), (d, b, f)),
    "west": lambda a, b, c, d, e, f: ((a, e, c), (a, e, f), (a, b, c)),
    "up": lambda a, b, c, d, e, f: ((a, e, c), (d, e, c), (a, e, f)),
    "down": lambda a, b, c, d, e, f: ((a, b, f), (d, b, f), (a, b, c)),
}
FACE_DIMS = {"north": (0, 1), "south": (0, 1), "east": (2, 1), "west": (2, 1), "up": (0, 2), "down": (0, 2)}
FACE_NORMAL_AXIS = {"north": 2, "south": 2, "east": 0, "west": 0, "up": 1, "down": 1}


# ============================================================================ build
def gear(m, name, parent, center, axis, r, thick=0.5, mat="gear", hub="gear_hub"):
    """Voxel cog: octagonal plate + 8 teeth (4 on the bone, 4 on a 45-degree child) + raised hub."""
    cx, cy, cz = center
    rot45 = {"x": (45, 0, 0), "y": (0, 45, 0), "z": (0, 0, 45)}[axis]
    m.bone(name, parent, center)
    m.bone(name + "_t", name, center, rot45)

    def put(bone, a0, a1, b0, b1, d0, d1, mt):
        # (a, b) span the gear plane, d runs along the axis
        if axis == "x":
            m.box(bone, cx + d0, cy + b0, cz + a0, cx + d1, cy + b1, cz + a1, mt)
        elif axis == "y":
            m.box(bone, cx + a0, cy + d0, cz + b0, cx + a1, cy + d1, cz + b1, mt)
        else:
            m.box(bone, cx + a0, cy + b0, cz + d0, cx + a1, cy + b1, cz + d1, mt)

    h = thick / 2
    s, c, tw = r * 0.78, r * 0.36, max(0.4, r * 0.22)
    put(name, -s, s, -c, c, -h, h, mat)
    put(name, -c, c, -s, s, -h, h, mat)
    put(name, -r, r, -tw, tw, -h * 0.8, h * 0.8, mat)
    put(name, -tw, tw, -r, r, -h * 0.8, h * 0.8, mat)
    put(name + "_t", -r, r, -tw, tw, -h * 0.8, h * 0.8, mat)
    put(name + "_t", -tw, tw, -r, r, -h * 0.8, h * 0.8, mat)
    hb = r * 0.3
    put(name, -hb, hb, -hb, hb, -h - 0.25, h + 0.25, hub)
    return name


# ---------------------------------------------------------------------------- variant: arm cannon
CANNON_ELBOW = (-6.0, 15.4, -2.6)   # forearm hinge: front edge of the elbow


def build_cannon(m, GEARS):
    """Steam cannon bolted onto the right forearm (wearer's right = -X). Replaces the bracer/gauntlet.
    GeckoLib overwrites armorRightArm with the vanilla arm pose every frame, so everything lives in children:
      armorRightArm > cannon_arm (shoulder: raise to aim) > cannon_forearm (elbow: bend to reload)
        > arm_cannon (housing) > cannon_barrel (recoil / break-open) > cannon_flash, cannon_shell
    Hidden at rest: cannon_flash (inside the barrel), cannon_shell (inside the breech), cannon_vent (in the housing)."""
    R, A, F = "armorRightArm", "cannon_arm", "cannon_forearm"
    m.bone(A, R, (-5, 22, 0))
    m.bone(F, A, CANNON_ELBOW)
    old = m.by[R]["cubes"]
    m.by[R]["cubes"] = []
    for c in old:
        y = c["origin"][1]
        if y >= 15.6 - 1e-6:
            m.by[A]["cubes"].append(c)          # sleeve, pauldron
        elif y >= 15.0 - 1e-6:
            m.by[F]["cubes"].append(c)          # elbow band
        # lower: bracer + gauntlet are replaced by the cannon
    m.by["gear_rarm"]["parent"] = A
    # keep parents before children in the bone list (GeckoLib doesn't care, the preview does)
    for n in (F, A):
        m.bones.remove(m.by[n])
        m.bones.insert(m.bones.index(m.by[R]) + 1, m.by[n])
    m.bones.remove(m.by["gear_rarm"])
    m.bones.remove(m.by["gear_rarm_t"])
    i = m.bones.index(m.by[F]) + 1
    m.bones[i:i] = [m.by["gear_rarm"], m.by["gear_rarm_t"]]

    C = "arm_cannon"
    m.bone(C, F, (-6, 12, 0))
    m.box(C, -9.3, 9.0, -3.3, -2.7, 14.8, 3.3, "iron_plate", faces={"north": "cannon_face"})  # housing
    m.box(C, -9.5, 14.0, -3.5, -2.5, 14.8, 3.5, "copper")                    # top band
    m.box(C, -9.5, 9.0, -3.5, -2.5, 9.8, 3.5, "copper")                      # bottom band
    m.box(C, -7.6, 11.4, -3.6, -4.4, 13.6, -3.3, "brass_plate")              # sight plate
    m.box(C, -7.1, 11.8, -3.75, -4.9, 13.2, -3.6, "glass")                   # pressure window (glows)
    m.box(C, -3.0, 10.2, -1.6, -2.4, 13.6, 1.6, "brass")                     # inner clamp
    m.box(C, -6.6, 12.6, 3.3, -5.4, 15.4, 4.3, "pipe")                       # steam hose, lower part
    m.box(C, -7.0, 12.0, 3.2, -5.0, 12.8, 4.6, "copper")                     # valve collar
    m.box(A, -6.6, 15.4, 2.7, -5.4, 20.3, 3.7, "pipe")                       # steam hose, upper part
    m.box(A, -6.9, 17.0, 2.5, -5.1, 17.6, 3.9, "copper")                     # hose clamp
    gear(m, "gear_cannon", C, (-9.65, 11.9, 0.0), "x", 2.3)                  # driven by the cannon_* anims only
    # barrel (points down the arm axis -> forward when the arm is raised to aim)
    Bb = "cannon_barrel"
    m.bone(Bb, C, (-6, 9, -1.9))                                            # hinge: front edge of the breech
    m.box(Bb, -7.9, 7.2, -1.9, -4.1, 9.0, 1.9, "iron_dark")                  # breech block
    m.box(Bb, -7.4, 4.4, -1.4, -4.6, 7.2, 1.4, "barrel")                     # copper barrel
    m.box(Bb, -7.65, 6.0, -1.65, -4.35, 6.6, 1.65, "copper")                 # reinforcing band
    m.box(Bb, -7.9, 3.4, -1.9, -4.1, 4.6, 1.9, "copper", faces={"down": "muzzle"})  # muzzle ring
    m.bone("cannon_flash", Bb, (-6, 5.4, 0))
    m.box("cannon_flash", -6.5, 4.9, -0.5, -5.5, 5.9, 0.5, "flash")         # parked inside the barrel
    m.bone("cannon_shell", Bb, (-6, 8.3, 0))                                # steam cartridge, parked in the breech
    m.box("cannon_shell", -6.7, 7.5, -0.7, -5.3, 8.7, 0.7, "brass")
    m.box("cannon_shell", -6.85, 8.7, -0.85, -5.15, 9.0, 0.85, "copper")    # rim
    m.bone("cannon_vent", C, (-8, 12, 0))                                   # vent puff, parked in the housing
    m.box("cannon_vent", -8.5, 11.5, -0.5, -7.5, 12.5, 0.5, "steam")


# ---------------------------------------------------------------------------- variant: Create-style elytra
EL_HINGE = (5.3, 22.5, 7.6)
EL_FOLD = [-6.0, -4.5, -3.0, -1.5, 0.0]          # rest pose (folded on the back), right wing; left is mirrored
EL_DEPLOY = [-100.0, -83.0, -66.0, -49.0, -32.0]  # fan spread
EL_PANELS = len(EL_FOLD)


def build_elytra(m, GEARS):
    """Mechanical fan-wings in Create style: andesite casing module with brass trim, andesite shafts,
    wooden cogwheels at the hinges and windmill-sail canvas panels in brass frames.
    `implant_socket` is an empty mount bone in the middle of the module for implants (code side)."""
    B, E = "armorBody", "elytra_module"
    HX, HY, HZ = EL_HINGE
    m.bone(E, B, (0, 18, 5))
    m.box(E, -3.8, 13.6, 3.2, 3.8, 23.0, 6.6, "andesite_casing")             # module housing
    m.box(E, -4.0, 22.6, 3.0, 4.0, 23.6, 6.8, "brass_casing")                # brass trim top
    m.box(E, -4.0, 13.2, 3.0, 4.0, 14.2, 6.8, "brass_casing")                # brass trim bottom
    m.box(E, -1.7, 15.3, 6.6, 1.7, 20.7, 7.0, "socket")                      # implant socket (glowing port)
    m.bone("implant_socket", E, (0, 18, 7.0))
    for sx in (-1, 1):
        x0, x1 = sorted((sx * 3.8, sx * 5.9))
        m.box(E, x0, 21.9, 5.4, x1, 23.1, 7.0, "andesite")                   # hinge bracket
        x0, x1 = sorted((sx * (HX - 0.35), sx * (HX + 0.35)))
        m.box(E, x0, HY - 0.35, 6.8, x1, HY + 0.35, 9.6, "shaft")            # hinge shaft
        x0, x1 = sorted((sx * 3.9, sx * 4.5))
        m.box(E, x0, 16.0, 6.4, x1, 20.0, 6.9, "brass")                      # side rails
    for tag, sx, speed in (("r", -1, 67.5), ("l", 1, -67.5)):
        m.bone(f"elytra_cog_{tag}_drive", E, (sx * HX, HY, 9.75))           # spun by the elytra_* anims
        GEARS.append((gear(m, f"elytra_cog_{tag}", f"elytra_cog_{tag}_drive", (sx * HX, HY, 9.75), "z", 1.9,
                           mat="cog_wood", hub="cog_hub"), "z", speed))        # spun by idle/overdrive
    W = "elytraRightWing"
    m.bone(W, B, (-HX, HY, HZ))
    for k in range(EL_PANELS):
        n, z = f"elytra_r_panel{k}", HZ + 0.36 * k
        m.bone(n, W, (-HX, HY, HZ), (0, 0, EL_FOLD[k]))
        m.box(n, -HX - 0.3, HY - 15.0, z - 0.04, -HX + 0.3, HY + 0.3, z + 0.34, "brass")      # spar
        m.box(n, -HX + 0.3, HY - 14.5, z, -HX + 3.6, HY - 0.6, z + 0.3, "canvas")          # sail
        m.box(n, -HX + 0.3, HY - 15.0, z - 0.02, -HX + 3.6, HY - 14.5, z + 0.32, "brass")  # tip bar
    m.mirror(W, lambda s: s.replace("Right", "Left").replace("_r_", "_l_"))


# ---------------------------------------------------------------------------- standalone models
FACE_ROT_X90 = {"north": "up", "up": "south", "south": "down", "down": "north", "east": "east", "west": "west"}


def extract(src, roots, root_name, root_pivot, xform=None, offset=(0, 0, 0)):
    """New Model holding the subtrees `roots` of `src` re-parented under a fresh root bone.
    xform='x90' maps (x, y, z) -> (x, -z, y): what pointed down (-Y) now points forward (-Z)."""
    m = Model()
    ox, oy, oz = offset

    def P(p):
        x, y, z = p
        if xform == "x90":
            x, y, z = x, -z, y
        return [x + ox, y + oy, z + oz]

    m.bone(root_name, None, root_pivot)
    keep, frontier = set(), list(roots)
    while frontier:
        n = frontier.pop()
        keep.add(n)
        frontier += [b["name"] for b in src.bones if b["parent"] == n]
    for b in src.bones:
        if b["name"] not in keep:
            continue
        nb = m.bone(b["name"], root_name if b["name"] in roots else b["parent"], P(b["pivot"]), b["rotation"])
        for c in b["cubes"]:
            a = P(c["origin"])
            e = P([c["origin"][i] + c["size"][i] for i in range(3)])
            lo = [min(a[i], e[i]) for i in range(3)]
            hi = [max(a[i], e[i]) for i in range(3)]
            faces = {(FACE_ROT_X90[k] if xform == "x90" else k): v for k, v in c["faces"].items()}
            m.box(nb["name"], *lo, *hi, c["mat"], faces)
    m.gears = [g for g in src.gears if g[0] in keep]
    m.steam = []
    return m


def build_cannon_item():
    """Standalone steam cannon (GeckoLib item, geo/item): the cannon from the arm-cannon variant taken off the
    arm, turned so the barrel points forward (-Z), window/sight on top, with a grip, shoulder brace and a short
    hose ending in a tank. Item origin: x=z=0 is the block centre, y=0 its bottom.
      steam_cannon > arm_cannon (housing) > cannon_barrel (recoil / break-open) > cannon_flash, cannon_shell
                                          > gear_cannon, cannon_vent
                   > cannon_grip, cannon_hose"""
    src = build("cannon")
    # the lower hose + valve collar ran up the arm; the item gets its own tank + feed pipe instead
    src.by["arm_cannon"]["cubes"] = [c for c in src.by["arm_cannon"]["cubes"]
                                     if not (c["origin"][1] >= 12.0 - 1e-6 and c["origin"][2] >= 3.2 - 1e-6)]
    # old cannon centre x=-6, barrel axis z=0, housing y 9..14.8 -> centre on the item origin
    m = extract(src, ["arm_cannon"], "steam_cannon", (0, 2, 0), xform="x90", offset=(6, 2.0, -10.0))
    C = "steam_cannon"
    # after the transform: housing x -3.3..3.3, y -1.3..5.3, z -1..4.8; barrel z -6.6..-1
    m.bone("cannon_grip", C, (0, 0, 2))
    m.box("cannon_grip", -0.9, -4.6, 1.2, 0.9, -1.3, 3.0, "leather")             # pistol grip
    m.box("cannon_grip", -1.1, -2.0, 1.0, 1.1, -1.3, 3.2, "copper")              # grip collar
    m.box("cannon_grip", -1.0, -4.9, 1.1, 1.0, -4.5, 3.1, "iron_dark")           # pommel cap
    m.box("cannon_grip", -0.3, -2.4, -0.6, 0.3, -1.3, 1.0, "iron_dark")          # trigger guard front
    m.box("cannon_grip", -0.3, -2.4, -0.6, 0.3, -2.0, 1.2, "iron_dark")          # trigger guard bottom
    m.bone("cannon_trigger", "cannon_grip", (0, -1.4, 0.4))
    m.box("cannon_trigger", -0.2, -2.0, 0.2, 0.2, -1.3, 0.6, "brass")
    m.box(C, -2.2, 0.4, 4.8, 2.2, 4.4, 7.4, "iron_plate")                        # stock / shoulder brace
    m.box(C, -2.5, 0.0, 7.4, 2.5, 4.8, 8.0, "leather", faces={"south": "leather_dark"})  # butt pad
    m.box(C, -2.4, 3.6, 4.7, 2.4, 4.2, 7.5, "copper")                            # stock band
    m.bone("cannon_hose", C, (2, 1.5, 6))
    m.box("cannon_hose", 2.2, 1.6, 5.2, 4.0, 4.0, 7.6, "canister")               # side steam tank
    m.box("cannon_hose", 2.1, 3.6, 5.1, 4.1, 4.1, 7.7, "copper")
    m.box("cannon_hose", 2.1, 1.5, 5.1, 4.1, 2.0, 7.7, "copper")
    m.box("cannon_hose", 2.9, 2.4, 3.6, 3.5, 3.0, 5.2, "pipe")                   # feed pipe into the housing
    for b in m.bones:  # lift so the lowest point (pommel) sits at y=0
        b["pivot"][1] += 4.9
        for c in b["cubes"]:
            c["origin"][1] += 4.9
    m.variant = "cannon_item"
    return m


def build_elytra_item():
    """Standalone Create-style elytra (wearable on its own, GeckoLib armor geo): the module + fan wings from the
    elytra variant, plus a harness so it sits on a bare player body. Only armorBody has content; the other
    armor bones are empty so GeoArmorRenderer finds them all."""
    src = build("elytra")
    m = Model()
    for name, pivot in (("armorHead", (0, 24, 0)), ("armorBody", (0, 24, 0)), ("armorRightArm", (-5, 22, 0)),
                        ("armorLeftArm", (5, 22, 0)), ("armorRightLeg", (-1.9, 12, 0)),
                        ("armorLeftLeg", (1.9, 12, 0)), ("armorRightBoot", (-1.9, 12, 0)),
                        ("armorLeftBoot", (1.9, 12, 0))):
        m.bone(name, None, pivot)
    sub = extract(src, ["elytra_module", "elytraRightWing", "elytraLeftWing"], "_tmp", (0, 0, 0))
    for b in sub.bones[1:]:
        b["parent"] = "armorBody" if b["parent"] == "_tmp" else b["parent"]
        m.bones.append(b)
        m.by[b["name"]] = b
    B = "armorBody"
    m.bone("elytra_harness", B, (0, 24, 0))
    H = "elytra_harness"
    m.box(H, -3.8, 13.6, 2.0, 3.8, 23.0, 3.2, "andesite")                        # mounting plate on the back
    for sx in (-1, 1):
        x0, x1 = sorted((sx * 1.6, sx * 3.0))
        m.box(H, x0, 24.0, -2.4, x1, 24.5, 2.4, "leather")                      # over the shoulders
        m.box(H, x0, 15.0, -2.5, x1, 24.0, -2.0, "leather")                     # front straps
        m.box(H, x0, 20.0, 2.0, x1, 24.0, 2.4, "leather")                       # back, up to the plate
        m.box(H, x0 - 0.1, 21.0, -2.7, x1 + 0.1, 21.8, -1.9, "brass")           # strap buckles
    m.box(H, -4.4, 12.6, -2.5, 4.4, 14.2, 2.4, "leather")                        # waist belt
    m.box(H, -1.0, 12.4, -2.8, 1.0, 14.4, -2.5, "copper_plate")                  # belt buckle
    m.box(H, -1.6, 18.2, -2.7, 1.6, 19.0, -2.4, "leather_dark")                  # chest strap
    m.box(H, -0.6, 17.9, -2.9, 0.6, 19.3, -2.6, "brass")                         # chest clasp
    m.gears = [g for g in src.gears if g[0] in m.by]
    m.steam = []
    m.variant = "elytra_item"
    return m


def build(variant="base"):
    if variant == "cannon_item":
        return build_cannon_item()
    if variant == "elytra_item":
        return build_elytra_item()
    m = Model()
    Model._mirrored = set()
    GEARS = []  # (bone, axis, speed deg/s)

    # ------------------------------------------------------------------ HELMET
    H = "armorHead"
    m.bone(H, None, (0, 24, 0))
    m.box(H, -5, 23.5, -5, 5, 32.5, 5, "iron_plate")                         # shell
    m.box(H, -4.5, 32.5, -4.5, 4.5, 33.3, 4.5, "iron")                       # dome
    m.box(H, -1.5, 32.5, -5.6, 1.5, 34.4, 5.3, "brass_plate")                # crest ridge
    m.box(H, -1.5, 31.6, -5.8, 1.5, 32.5, -5.0, "brass")                     # crest nose
    m.box(H, -4.7, 30.0, -5.8, 4.7, 31.6, -5.0, "brass_plate")               # brow bar
    m.box(H, -4.7, 27.0, -5.8, -3.4, 30.0, -5.0, "brass")                    # visor frame sides
    m.box(H, 3.4, 27.0, -5.8, 4.7, 30.0, -5.0, "brass")
    m.box(H, -4.7, 23.6, -5.8, 4.7, 27.0, -5.0, "brass_plate")               # jaw plate
    m.box(H, -3.4, 27.0, -5.4, 3.4, 30.0, -5.0, "glass")                     # glowing visor
    m.box(H, -1.7, 23.8, -7.2, 1.7, 27.6, -5.8, "iron")                      # respirator snout
    m.box(H, -1.25, 24.2, -7.6, 1.25, 27.0, -7.2, "grill")                   # grill
    m.box(H, -1.9, 27.2, -7.0, 1.9, 27.9, -5.8, "iron_dark")                 # snout brow
    for sx in (-1, 1):
        x0, x1 = sorted((sx * 2.0, sx * 3.7))
        m.box(H, x0, 24.2, -6.9, x1, 26.4, -5.6, "copper")                   # filter canisters
        m.box(H, min(sx * 1.7, sx * 2.0), 24.9, -6.6, max(sx * 1.7, sx * 2.0), 25.7, -6.0, "copper")
        x0, x1 = sorted((sx * 5.0, sx * 5.6))
        m.box(H, x0, 24.5, -2.8, x1, 30.5, 2.8, "brass_plate")               # ear plates
    m.box(H, -4.6, 22.6, 3.8, 4.6, 25.0, 5.6, "iron_plate")                  # neck guard
    GEARS.append((gear(m, "gear_helmet", H, (5.85, 28.6, 0.6), "x", 2.4), "x", 90))
    GEARS.append((gear(m, "gear_helmet_s", H, (-5.85, 26.4, 1.6), "x", 1.3), "x", -157.5))

    # ------------------------------------------------------------------ BODY (chestplate)
    B = "armorBody"
    m.bone(B, None, (0, 24, 0))
    m.box(B, -4.6, 12.0, -2.7, 4.6, 24.3, 2.7, "iron_plate")                 # torso core
    m.box(B, -5.0, 16.0, -3.4, 5.0, 24.0, -2.7, "brass_plate")               # chest plate
    m.box(B, -3.6, 13.0, -3.2, 3.6, 16.0, -2.7, "brass_plate")               # ab plate
    m.box(B, -3.8, 14.0, -3.45, 3.8, 14.8, -3.2, "copper")                   # ab strip
    m.box(B, -2.0, 7.6, -3.3, 2.0, 11.0, -2.6, "brass_plate")                # tasset
    m.box(B, -5.0, 16.0, 2.7, 5.0, 24.0, 3.2, "brass_plate")                 # back plate
    # pressure gauge with an animated needle
    m.box(B, -1.9, 17.1, -3.9, 1.9, 20.9, -3.4, "copper_plate")              # bezel
    m.box(B, -1.45, 17.55, -4.0, 1.45, 20.45, -3.9, "gauge")                 # dial
    m.bone("gauge_needle", B, (0, 19.0, -4.0))
    m.box("gauge_needle", -0.12, 19.0, -4.1, 0.12, 20.2, -4.0, "needle")
    m.box("gauge_needle", -0.3, 18.7, -4.16, 0.3, 19.3, -4.0, "needle")
    # copper pipes looping from the collar down to the gauge
    for sx in (-1, 1):
        x0, x1 = sorted((sx * 2.4, sx * 3.4))
        m.box(B, x0, 18.4, -4.2, x1, 24.6, -3.2, "pipe")
        x0, x1 = sorted((sx * 1.85, sx * 2.4))
        m.box(B, x0, 18.4, -4.2, x1, 19.4, -3.2, "pipe")
        x0, x1 = sorted((sx * 2.2, sx * 3.6))
        m.box(B, x0, 22.0, -4.35, x1, 22.6, -3.05, "copper")                 # pipe clamp
    # belt + pouches
    m.box(B, -5.0, 10.8, -3.4, 5.0, 12.8, 3.4, "leather")
    m.box(B, -1.3, 10.6, -3.75, 1.3, 13.0, -3.4, "copper_plate")             # buckle
    for sx in (-1, 1):
        x0, x1 = sorted((sx * 2.0, sx * 4.7))
        m.box(B, x0, 9.0, -4.2, x1, 12.2, -3.4, "leather")                   # front pouches
        m.box(B, x0 - 0.1, 11.4, -4.35, x1 + 0.1, 12.5, -3.3, "leather_dark")
        x0, x1 = sorted((sx * 0.5, sx * 4.0))
        m.box(B, x0, 9.4, 3.4, x1, 12.4, 4.3, "leather")                     # back pouches
        m.box(B, x0 - 0.1, 11.6, 3.3, x1 + 0.1, 12.6, 4.45, "leather_dark")
        x0, x1 = sorted((sx * 5.0, sx * 5.6))
        m.box(B, x0, 9.6, -1.6, x1, 12.4, 1.6, "leather")                    # hip pouches
    # steam backpack (the elytra variant swaps frame/tank/back gear for the Create module)
    EL = variant == "elytra"
    if not EL:
        m.box(B, -3.8, 14.0, 3.2, 3.8, 23.2, 5.6, "iron_plate")              # frame
        m.box(B, -2.8, 15.0, 5.6, 2.8, 22.2, 6.0, "copper_plate")            # tank face
        m.box(B, -3.3, 22.2, 3.4, 3.3, 23.6, 5.4, "iron_dark")               # top cap
    for sx in (-1, 1):
        x0, x1 = sorted((sx * 3.8, sx * 5.0))
        m.box(B, x0, 15.0, 3.4, x1, 22.0, 4.8, "canister")                   # side cylinders
        m.box(B, x0 - 0.15, 21.0, 3.25, x1 + 0.15, 21.6, 4.95, "copper")
        m.box(B, x0 - 0.15, 15.6, 3.25, x1 + 0.15, 16.2, 4.95, "copper")
        x0, x1 = sorted((sx * 2.0, sx * 3.3))
        m.box(B, x0, 23.2, 3.6, x1, 28.6 if sx > 0 else 27.6, 4.9, "chimney", faces={"up": "chimney_hole"})
        m.box(B, x0 - 0.2, 26.0 if sx > 0 else 25.4, 3.4, x1 + 0.2, 26.6 if sx > 0 else 26.0, 5.1, "copper")
        if not EL:
            x0, x1 = sorted((sx * 1.0, sx * 1.8))
            m.box(B, x0, 13.2, 5.6, x1, 15.0, 6.3, "pipe")                   # outlet pipes
    if not EL:
        GEARS.append((gear(m, "gear_back", B, (0, 18.6, 6.3), "z", 3.1, thick=0.6), "z", -247.5))
    # steam puffs above the chimneys (animated)
    STEAM = []
    for tag, sx, top in (("l", 1, 28.6), ("r", -1, 27.6)):
        bx = sx * 2.65
        m.bone(f"steam_{tag}", B, (bx, top, 4.25))
        for i in range(3):
            n = f"steam_{tag}{i + 1}"
            s = (0.9, 1.2, 1.5)[i]
            m.bone(n, f"steam_{tag}", (bx, top + 0.5, 4.25))
            m.box(n, bx - s / 2, top + 0.2, 4.25 - s / 2, bx + s / 2, top + 0.2 + s, 4.25 + s / 2, "steam")
            STEAM.append(n)

    # ------------------------------------------------------------------ ARMS (right = -X)
    R = "armorRightArm"
    m.bone(R, None, (-5, 22, 0))
    m.box(R, -8.5, 15.6, -2.5, -3.5, 20.2, 2.5, "iron_plate")                # upper sleeve
    m.box(R, -9.8, 20.0, -3.7, -3.4, 25.0, 3.7, "brass_plate")               # pauldron
    m.box(R, -9.3, 25.0, -3.2, -3.9, 25.8, 3.2, "iron_plate")                # pauldron cap
    m.box(R, -10.0, 19.6, -3.9, -3.2, 20.5, 3.9, "copper")                   # pauldron rim
    m.box(R, -8.8, 15.0, -2.8, -3.2, 15.8, 2.8, "copper")                    # elbow band
    m.box(R, -9.0, 11.2, -3.0, -3.0, 15.0, 3.0, "brass_plate")               # bracer
    m.box(R, -9.2, 14.2, -3.2, -2.8, 15.0, 3.2, "copper")
    m.box(R, -9.2, 11.0, -3.2, -2.8, 11.8, 3.2, "copper")
    m.box(R, -8.6, 9.4, -2.6, -3.4, 11.0, 2.6, "iron")                       # gauntlet
    m.box(R, -8.8, 9.6, -2.95, -3.2, 10.7, -2.6, "iron_dark")                # knuckles
    GEARS.append((gear(m, "gear_rarm", R, (-10.05, 22.6, 0.0), "x", 2.2), "x", 112.5))
    m.mirror(R, lambda n: n.replace("Right", "Left").replace("_rarm", "_larm"))
    GEARS.append(("gear_larm", "x", -112.5))

    # ------------------------------------------------------------------ LEGS (leggings)
    L = "armorRightLeg"
    m.bone(L, None, (-1.9, 12, 0))
    m.box(L, -4.4, 6.8, -2.6, 0.6, 12.0, 2.6, "iron_plate")                  # thigh
    m.box(L, -4.0, 8.0, -3.2, 0.2, 11.4, -2.6, "brass_plate")                # thigh plate
    m.box(L, -4.6, 6.4, -2.8, 0.8, 7.2, 2.8, "copper")                       # band
    m.box(L, -4.3, 3.6, -2.5, 0.5, 6.4, 2.5, "iron")                         # shin
    m.box(L, -4.1, 4.0, -3.4, 0.3, 6.9, -2.5, "brass_plate")                 # knee cop
    m.box(L, -3.2, 4.6, 2.5, -2.5, 10.6, 3.1, "rod")                         # back piston rod
    m.box(L, -3.4, 8.2, 2.4, -2.3, 11.2, 3.4, "canister")                    # piston cylinder
    m.box(L, -1.4, 4.6, 2.5, -0.7, 9.0, 3.1, "rod")
    GEARS.append((gear(m, "gear_rleg", L, (-4.75, 5.3, 0.3), "x", 1.8), "x", 135))
    m.mirror(L, lambda n: n.replace("Right", "Left").replace("_rleg", "_lleg"))
    GEARS.append(("gear_lleg", "x", -135))

    # ------------------------------------------------------------------ BOOTS
    F = "armorRightBoot"
    m.bone(F, None, (-1.9, 12, 0))
    m.box(F, -4.7, 0.5, -3.2, 0.9, 4.0, 2.9, "iron_plate")                   # boot
    m.box(F, -4.9, -0.4, -4.1, 1.1, 0.6, 3.2, "sole")                        # sole
    m.box(F, -4.5, 0.6, -4.25, 0.7, 2.4, -3.2, "copper_plate")               # toe cap
    m.box(F, -4.9, 3.5, -3.4, 1.1, 4.3, 3.1, "copper")                       # top band
    m.box(F, -2.4, 0.6, 2.9, -1.4, 3.6, 3.5, "rod")                          # heel piston
    GEARS.append((gear(m, "gear_rboot", F, (-4.95, 2.2, 0.8), "x", 1.25), "x", -202.5))
    m.mirror(F, lambda n: n.replace("Right", "Left").replace("_rboot", "_lboot"))
    GEARS.append(("gear_lboot", "x", 202.5))

    if variant == "cannon":
        build_cannon(m, GEARS)
    elif variant == "elytra":
        build_elytra(m, GEARS)

    m.variant = variant
    m.gears, m.steam = GEARS, STEAM
    return m


# ============================================================================ texture
def clamp(v):
    return max(0, min(255, int(round(v))))


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def shade(c, k):
    return tuple(clamp(v * k) for v in c)


PAL = {  # material -> base colour
    "brass": (182, 150, 66), "brass_plate": (182, 150, 66), "gear": (196, 150, 64), "gear_hub": (120, 96, 48),
    "iron": (78, 80, 84), "iron_plate": (78, 80, 84), "iron_dark": (54, 55, 58),
    "copper": (188, 106, 62), "copper_plate": (188, 106, 62), "pipe": (190, 108, 62),
    "leather": (116, 70, 42), "leather_dark": (88, 50, 30),
    "canister": (128, 132, 138), "rod": (170, 174, 180), "chimney": (66, 67, 70),
    "sole": (52, 52, 56), "steam": (196, 198, 202), "needle": (60, 22, 18),
    "glass": (255, 170, 40), "gauge": (226, 222, 202), "grill": (178, 98, 58), "chimney_hole": (30, 26, 24),
    # cannon
    "cannon_face": (78, 80, 84), "barrel": (188, 106, 62), "muzzle": (30, 26, 24), "flash": (255, 236, 150),
    # Create-style elytra
    "andesite": (146, 148, 142), "shaft": (150, 152, 146), "cog_wood": (156, 114, 68), "cog_hub": (120, 122, 118),
    "andesite_casing": (146, 148, 142), "brass_casing": (196, 160, 72), "canvas": (226, 216, 190),
    "socket": (196, 160, 72),
}
WEATHERED = {"brass", "brass_plate", "gear", "iron", "iron_plate", "iron_dark", "chimney", "sole", "cannon_face"}
RIVETED = {"brass_plate", "iron_plate", "copper_plate", "cannon_face"}
CYLINDER = {"pipe", "canister", "rod", "chimney", "copper", "barrel", "shaft"}
NO_BEVEL = {"glass", "gauge", "steam", "chimney_hole", "gear", "cog_wood", "flash", "muzzle", "canvas", "socket",
            "andesite_casing", "brass_casing"}
CASING = {"andesite_casing": ((150, 152, 146), (120, 86, 54)),      # frame colour, plank colour (Create casings)
          "brass_casing": ((206, 170, 78), (78, 54, 38))}
GLOW = {"glass"}
RUST = (130, 70, 36)
GRIME = (58, 48, 34)


def paint_face(px, gx, rect, mat, face, cube, rng):
    x0, y0, w, h = rect
    base = PAL[mat]
    buf = {}

    def put(u, v, c):
        if 0 <= u < w and 0 <= v < h:
            buf[(u, v)] = c

    # 1) base with blocky 2x2 noise + vertical light falloff on side faces
    cells = {}
    for v in range(h):
        for u in range(w):
            key = (u // 2, v // 2)
            if key not in cells:
                cells[key] = rng.choice([0.92, 0.96, 1.0, 1.0, 1.03, 1.06])
            k = cells[key] * rng.uniform(0.98, 1.02)
            if face not in ("up", "down"):
                k *= 1.06 - 0.12 * (v / max(1, h - 1))
            elif face == "down":
                k *= 0.8
            put(u, v, shade(base, k))

    # 2) cylinder shading across pipes / tanks
    if mat in CYLINDER:
        sizes = cube["size"]
        long_axis = int(np.argmax(sizes))
        iu, iv = FACE_DIMS[face]
        if long_axis != FACE_NORMAL_AXIS[face]:
            across_u = long_axis == iv
            n = w if across_u else h
            prof = [0.72, 1.0, 1.22, 1.08, 0.95, 0.85, 0.72]
            for v in range(h):
                for u in range(w):
                    t = (u if across_u else v) / max(1, n - 1)
                    k = prof[min(len(prof) - 1, int(t * len(prof)))]
                    put(u, v, shade(buf[(u, v)], k))
        elif mat != "chimney":     # end cap: ring
            for v in range(h):
                for u in range(w):
                    if 0 < u < w - 1 and 0 < v < h - 1:
                        put(u, v, shade(buf[(u, v)], 0.78))

    # 3) weathering: rust streaks and grime blotches
    if mat in WEATHERED and w * h >= 12:
        for _ in range(max(1, w * h // 150)):
            cu, cv = rng.randrange(w), rng.randrange(h)
            col = RUST if rng.random() < 0.55 else GRIME
            r = rng.choice([1, 1, 1, 2])
            strength = rng.uniform(0.18, 0.4)
            for v in range(cv - r, cv + r + 1):
                for u in range(cu - r, cu + r + 1):
                    if (u - cu) ** 2 + (v - cv) ** 2 <= r * r + 0.5 and rng.random() < 0.8 and (u, v) in buf:
                        put(u, v, mix(buf[(u, v)], col, strength))
            if col == RUST and face not in ("up", "down"):        # drip down
                ln = rng.randrange(2, 6)
                for v in range(cv, cv + ln):
                    if (cu, v) in buf:
                        put(cu, v, mix(buf[(cu, v)], RUST, strength * 0.7))
        for _ in range(max(0, w * h // 120)):                     # bright scratches
            cu, cv = rng.randrange(w), rng.randrange(h)
            for i in range(rng.randrange(2, 4)):
                if (cu + i, cv + i // 2) in buf:
                    put(cu + i, cv + i // 2, shade(buf[(cu + i, cv + i // 2)], 1.25))

    # 4) materials with special patterns
    if mat == "glass":
        for v in range(h):
            for u in range(w):
                t = v / max(1, h - 1)
                c = mix((255, 214, 90), (238, 128, 20), t)
                if (u - v * 1.4) % 11 < 2.2:                    # diagonal reflection streaks
                    c = mix(c, (255, 246, 196), 0.6)
                if u == 0 or v == 0 or u == w - 1 or v == h - 1:
                    c = shade(c, 0.78)
                put(u, v, c)
                gx[x0 + u, y0 + v] = c + (255,)
    elif mat == "gauge":
        if face == "north":
            cu, cv = (w - 1) / 2, (h - 1) / 2
            R = min(w, h) / 2
            for v in range(h):
                for u in range(w):
                    d = math.hypot(u - cu, v - cv) / R
                    if d > 0.98:
                        c = PAL["copper"]
                    elif d > 0.84:
                        c = (40, 34, 30)
                    else:
                        c = shade(base, 1.0 - 0.15 * d)
                        a = math.degrees(math.atan2(v - cv, u - cu)) % 360
                        if d > 0.62 and (a % 30 < 9) and not (40 < a < 140):   # ticks, gap at the bottom
                            c = (52, 44, 40)
                        if d > 0.62 and 300 < a < 340:                         # red zone
                            c = (196, 40, 30)
                    put(u, v, c)
        else:
            for k in buf:
                put(*k, shade(PAL["copper"], 0.9))
    elif mat == "grill":
        if face == "north":
            for v in range(h):
                for u in range(w):
                    rim = u == 0 or v == 0 or u == w - 1 or v == h - 1
                    if not rim and u % 2 == 1:
                        put(u, v, (40, 24, 18))
    elif mat == "chimney_hole":
        for v in range(h):
            for u in range(w):
                rim = u == 0 or v == 0 or u == w - 1 or v == h - 1
                put(u, v, shade(PAL["chimney"], 1.15) if rim else (24 + 8 * rng.random(), 18, 16))
                if not rim:
                    gx[x0 + u, y0 + v] = (255, 110, 30, 110)                   # faint ember glow
    elif mat in ("leather", "leather_dark") and w >= 4 and h >= 4 and face not in ("up", "down"):
        for u in range(1, w - 1, 2):                                          # stitching
            put(u, 1, shade(base, 1.45))
            put(u, h - 2, shade(base, 1.45))
    elif mat == "sole" and face == "down":
        for v in range(h):
            for u in range(w):
                if v % 3 == 0:
                    put(u, v, (34, 34, 36))
    elif mat == "steam":
        for k in buf:
            put(*k, shade(base, rng.uniform(0.88, 1.08)))
    elif mat in ("gear", "cog_wood"):                                           # worn rim, bright centre
        for v in range(h):
            for u in range(w):
                d = max(abs(u - (w - 1) / 2) / max(1, w / 2), abs(v - (h - 1) / 2) / max(1, h / 2))
                c = shade(buf[(u, v)], 1.12 - 0.35 * d)
                if mat == "cog_wood" and face not in ("up", "down") and (u + 2 * v) % 5 == 0:
                    c = shade(c, 0.86)                                          # wood grain
                put(u, v, c)
    elif mat == "andesite":                                                     # speckled stone
        for k in buf:
            if rng.random() < 0.18:
                put(*k, shade(buf[k], rng.choice([0.8, 1.18])))
    elif mat in CASING:                                                         # Create casing: frame + planks
        frame, plank = CASING[mat]
        bw = 2 if min(w, h) >= 10 else 1
        for v in range(h):
            for u in range(w):
                edge = min(u, v, w - 1 - u, h - 1 - v)
                if edge < bw or min(w, h) < 4:
                    c = shade(frame, (1.15 if edge == 0 and (u == 0 or v == 0) else 1.0) * rng.uniform(0.95, 1.04))
                    if edge == 0 and (u == w - 1 or v == h - 1):
                        c = shade(frame, 0.7)
                else:
                    row = (v - bw) % 3
                    c = shade(plank, (0.78 if row == 2 else 1.0) * rng.uniform(0.94, 1.05))
                    if (u - bw) % 7 == (3 * ((v - bw) // 3)) % 7 and row != 2:
                        c = shade(plank, 0.82)                                  # plank seams
                put(u, v, c)
        if min(w, h) >= 6:                                                      # brass corner studs
            for (u, v) in ((bw, bw), (w - 1 - bw, bw), (bw, h - 1 - bw), (w - 1 - bw, h - 1 - bw)):
                put(u, v, shade(frame, 1.3))
    elif mat == "canvas":                                                       # windmill-sail canvas in a wood frame
        wood = (120, 84, 50)
        for v in range(h):
            for u in range(w):
                if face in ("north", "south"):
                    if u == 0 or u == w - 1 or v == 0 or v == h - 1 or (v % 6 == 5 and v < h - 2):
                        c = shade(wood, rng.uniform(0.9, 1.08))
                    else:
                        c = shade(base, (0.95 if (u + v) % 2 else 1.02) * (1.04 - 0.1 * v / max(1, h - 1)))
                else:
                    c = shade(wood, rng.uniform(0.85, 1.0))
                put(u, v, c)
    elif mat == "socket":                                                       # implant port: brass rim, dark bay
        for v in range(h):
            for u in range(w):
                rim = u == 0 or v == 0 or u == w - 1 or v == h - 1
                if face != "south" or rim or min(w, h) < 4:
                    put(u, v, shade(base, 0.7 if (u == w - 1 or v == h - 1) else 1.08))
                    continue
                c = (30, 26, 24)
                if u == 1 or u == w - 2:
                    c = (52, 50, 48)                                           # guide rails
                if u in (2, w - 3) and v % 2 == 0 and 1 < v < h - 2:
                    c = (255, 150, 40)                                         # contact pins
                    gx[x0 + u, y0 + v] = c + (255,)
                if v == h // 2 and 2 <= u <= w - 3:
                    c = (255, 196, 80)                                         # lock line
                    gx[x0 + u, y0 + v] = c + (200,)
                put(u, v, c)
    elif mat == "cannon_face" and w >= 6 and h >= 6:                             # hazard band along the bottom
        for v in range(h - 4, h - 1):
            for u in range(1, w - 1):
                put(u, v, (214, 168, 40) if (u + v) % 4 < 2 else (34, 30, 28))
    elif mat == "barrel" and face not in ("up", "down"):                         # soot toward the muzzle
        for v in range(h):
            for u in range(w):
                t = (v / max(1, h - 1)) ** 2
                put(u, v, mix(buf[(u, v)], (44, 34, 30), 0.65 * t * rng.uniform(0.8, 1.1)))
    elif mat == "muzzle":
        for v in range(h):
            for u in range(w):
                edge = min(u, v, w - 1 - u, h - 1 - v)
                if edge == 0:
                    c = shade(PAL["copper"], 0.75)
                elif edge == 1:
                    c = (64, 40, 30)
                    gx[x0 + u, y0 + v] = (255, 120, 30, 90)
                else:
                    c = (18, 14, 12)
                put(u, v, c)
    elif mat == "flash":
        for v in range(h):
            for u in range(w):
                c = mix((255, 250, 220), (255, 170, 40), max(abs(u - (w - 1) / 2), abs(v - (h - 1) / 2)) / max(1, w / 2))
                put(u, v, c)
                gx[x0 + u, y0 + v] = c + (255,)

    # 5) pixel-art bevel: dark outline, light top-left inner edge
    if mat not in NO_BEVEL and w >= 3 and h >= 3:
        for v in range(h):
            for u in range(w):
                if u == 0 or v == 0 or u == w - 1 or v == h - 1:
                    put(u, v, shade(buf[(u, v)], 0.62))
                elif (v == 1 or u == 1) and mat not in CYLINDER:
                    put(u, v, shade(buf[(u, v)], 1.16))

    # 6) rivets
    if mat in RIVETED and w >= 7 and h >= 7:
        step = 5
        spots = set()
        for u in range(2, w - 2, step):
            spots.add((u, 2))
            spots.add((u, h - 3))
        for v in range(2, h - 2, step):
            spots.add((2, v))
            spots.add((w - 3, v))
        spots |= {(w - 3, h - 3)}
        for (u, v) in spots:
            c = buf[(u, v)]
            put(u, v, shade(c, 1.4))
            put(u + 1, v + 1, shade(c, 0.55))
            put(u + 1, v, shade(c, 0.85))

    for (u, v), c in buf.items():
        px[x0 + u, y0 + v] = tuple(clamp(t) for t in c) + (255,)


def pack(model, tex):
    rects = []
    for b in model.bones:
        for c in b["cubes"]:
            for face, (iu, iv) in FACE_DIMS.items():
                w = max(1, int(math.ceil(c["size"][iu] * DENSITY - 1e-6)))
                h = max(1, int(math.ceil(c["size"][iv] * DENSITY - 1e-6)))
                rects.append((h, w, c, face))
    rects.sort(key=lambda r: (-r[0], -r[1]))
    x = y = shelf = 0
    uv = {}
    for h, w, c, face in rects:
        if x + w > tex:
            x, y, shelf = 0, y + shelf, 0
        if y + h > tex:
            return None
        uv[(id(c), face)] = (x, y, w, h)
        x += w
        shelf = max(shelf, h)
    return rects, uv, y + shelf


def pack_and_paint(model, rng):
    global TEX
    while (res := pack(model, TEX)) is None:
        TEX *= 2
    rects, uv, used = res
    img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    glow = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    px, gx = img.load(), glow.load()
    for h, w, c, face in rects:
        paint_face(px, gx, uv[(id(c), face)], c["faces"].get(face, c["mat"]), face, c, rng)
    occ = np.zeros((TEX, TEX), dtype=np.int32)
    for (x0, y0, w, h) in uv.values():
        occ[y0:y0 + h, x0:x0 + w] += 1
    assert occ.max() == 1, "UV overlap"
    return img, glow, uv, used


# ============================================================================ export
def r4(v):
    v = round(float(v), 4)
    return int(v) if v == int(v) else v


def export_geo(model, uv, path):
    bones = []
    for b in model.bones:
        bj = {"name": b["name"]}
        if b["parent"]:
            bj["parent"] = b["parent"]
        bj["pivot"] = [r4(v) for v in b["pivot"]]
        if any(abs(v) > 1e-9 for v in b["rotation"]):
            bj["rotation"] = [r4(v) for v in b["rotation"]]
        cubes = []
        for c in b["cubes"]:
            faces = {}
            for face in FACE_DIMS:
                x0, y0, w, h = uv[(id(c), face)]
                faces[face] = {"uv": [x0, y0], "uv_size": [w, h]}
            cubes.append({"origin": [r4(v) for v in c["origin"]], "size": [r4(v) for v in c["size"]], "uv": faces})
        if cubes:
            bj["cubes"] = cubes
        bones.append(bj)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.{NAME}", "texture_width": TEX, "texture_height": TEX,
                        "visible_bounds_width": 3, "visible_bounds_height": 3.5, "visible_bounds_offset": [0, 1.25, 0]},
        "bones": bones}]}
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(geo, fh, indent="\t")


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
            drift = 0.8 * math.sin(TAU * ph + i)
            add(out, n, pos=(drift, rise * ph, 0.6 * ph), scl=(k, k, k))

    # idle (loop 4s): gears turn, needle trembles in the green zone, chimneys puff steam
    def idle(t):
        out = {}
        gears(out, t)
        needle = -25 + 10 * math.sin(TAU * t / 4.0) + 3 * math.sin(TAU * t * 3.0)
        add(out, "gauge_needle", (0, 0, needle))
        steam(out, t, 2.0, 6.0)
        return out

    # overdrive (loop 2s): for sprinting/attacking - gears race, needle slams into the red, thick steam
    def overdrive(t):
        out = {}
        gears(out, t, 3.0)
        needle = 55 + 6 * math.sin(TAU * t * 4.0)
        add(out, "gauge_needle", (0, 0, needle))
        steam(out, t, 0.5, 9.0)
        return out

    anims = {
        "idle": dict(fn=idle, length=4.0, dt=0.1, loop=True),
        "overdrive": dict(fn=overdrive, length=2.0, dt=0.05, loop=True),
    }

    def smooth(x):
        x = max(0.0, min(1.0, x))
        return x * x * (3 - 2 * x)

    if m.variant == "cannon":
        # All cannon_* anims belong on ONE "arm" controller; they only touch the cannon bones, so the body
        # controller (idle/overdrive) keeps running in parallel. Aim-state anims start and end at AIM.
        AIM = -90.0

        def arm(out, shoulder, elbow=0.0, cog=0.0):
            add(out, "cannon_arm", (shoulder, 0, 0))
            add(out, "cannon_forearm", (elbow, 0, 0))
            add(out, "gear_cannon", (-cog, 0, 0))

        def c_idle(t):          # loop 4s: arm hangs like a normal arm, cog ticks over slowly
            out = {}
            arm(out, 0.0, 0.0, 45 * t)
            return out

        def aim_start(t):       # 0.35s, hold: raise and straighten the arm
            out = {}
            e = smooth(t / 0.35)
            arm(out, AIM * e, -25 * math.sin(math.pi * e), 90 * e)
            return out

        def aim(t):             # loop 2s: held forward, slight breathing
            out = {}
            arm(out, AIM + 1.2 * math.sin(TAU * t / 2.0), 0.6 * math.sin(TAU * t / 2.0 + 1), 45 * t)
            return out

        def aim_end(t):         # 0.35s: lower back to the hanging pose
            out = {}
            e = smooth(t / 0.35)
            arm(out, AIM * (1 - e), -25 * math.sin(math.pi * e), 90 * e)
            return out

        def fire(t):            # 0.6s, from AIM: recoil kicks the arm up, barrel slides back, muzzle flash
            out = {}
            rec = t / 0.04 if t < 0.04 else max(0.0, 1 - (t - 0.04) / 0.4) ** 2
            arm(out, AIM - 9 * rec, -6 * rec, 200 * smooth(t / 0.5))
            add(out, "cannon_barrel", pos=(0, 1.4 * rec, 0))
            if 0.01 <= t < 0.14:
                k = 2.4 - 6 * (t - 0.01)
                add(out, "cannon_flash", pos=(0, -3.0, 0), scl=(k, k * 1.3, k))
            return out

        def spinup(t):          # loop 1s, at AIM: charging - cog races, barrel shivers
            out = {}
            arm(out, AIM + 0.5 * math.sin(TAU * t * 9), 0, 720 * t)
            add(out, "cannon_barrel", pos=(0.08 * math.sin(TAU * t * 10), 0.12 * math.sin(TAU * t * 7), 0))
            return out

        # reload (1.9s, AIM -> AIM): bend the elbow so the cannon points up, break the breech, spent shell flies
        # out, a fresh cartridge is shot out of the right backpack canister over the shoulder into the breech,
        # snap shut + steam vent, then extend the arm again.
        R_SH, R_EL = -55.0, -110.0

        def bend(t):
            return smooth(t / 0.35) - smooth((t - 1.5) / 0.35)

        def brk(t):
            return smooth((t - 0.3) / 0.18) if t < 1.3 else 1 - min(1.0, (t - 1.3) / 0.1) ** 2

        def reload_pose(t, out):
            k = bend(t)
            arm(out, AIM + (R_SH - AIM) * k, R_EL * k, 220 * smooth(t / 0.4) + 300 * smooth((t - 1.3) / 0.4))
            add(out, "cannon_barrel", (-38 * brk(t), 0, 0), pos=(0, 0.6 * max(0.0, 1 - abs(t - 1.42) / 0.08), 0))

        # where the backpack canister is, seen from the barrel when the arm is bent and the breech open
        pose = {}
        reload_pose(0.9, pose)
        Mb = bone_matrices(m, pose)["cannon_barrel"]
        src = np.array([-4.4, 22.6, 4.1, 1.0])                  # top of the right side canister (body space)
        d0 = (np.linalg.inv(Mb) @ src)[:3] - np.array([-6.0, 8.25, 0.0])
        entry = np.array([0.0, 1.8, 3.2])                       # just outside the open gap

        def reload(t):
            out = {}
            reload_pose(t, out)
            if 0.5 <= t < 0.8:                                  # spent shell pops out of the gap and falls away
                e = (t - 0.5) / 0.3
                add(out, "cannon_shell", (300 * e * e, 0, 0),
                    pos=(0, 1.6 * smooth(e * 2.5) - 3.0 * e * e, 1.2 * smooth(e * 2.5) + 7.0 * e * e),
                    scl=(1, 1, 1) if e < 0.75 else ((1 - e) * 4,) * 3)
            elif 0.8 <= t < 0.82:
                add(out, "cannon_shell", scl=(0, 0, 0))
            elif 0.82 <= t < 1.3:                               # fresh cartridge flies in
                e = (t - 0.82) / 0.48
                if e < 0.65:
                    f = e / 0.65
                    d = d0 + (entry - d0) * smooth(f)
                    d = d + np.array([0.0, 0.0, 4.0]) * math.sin(math.pi * f)   # arc over the shoulder
                    k = min(1.0, f / 0.12) * (1 + 0.6 * math.sin(math.pi * min(1.0, f * 1.3)))
                    add(out, "cannon_shell", (-540 * f, 0, 0), pos=(-d[0], d[1], d[2]), scl=(k, k, k))
                else:
                    f = (e - 0.65) / 0.35
                    a_, b_ = smooth(f * 2), smooth(f * 2 - 1)
                    add(out, "cannon_shell", pos=(0, entry[1] * (1 - b_), entry[2] * (1 - a_)))
            if 1.4 <= t < 1.76:                                 # steam vents out of the housing
                e = (t - 1.4) / 0.36
                k = 1.0 + 2.4 * e if e < 0.8 else (1 - e) / 0.2 * 2.92
                add(out, "cannon_vent", pos=(0, 1.2 * e, 3.5 + 3.0 * e), scl=(k, k, k))
            return out

        anims["cannon_idle"] = dict(fn=c_idle, length=4.0, dt=0.5, loop=True)
        anims["cannon_aim_start"] = dict(fn=aim_start, length=0.35, dt=0.05, loop="hold_on_last_frame")
        anims["cannon_aim"] = dict(fn=aim, length=2.0, dt=0.1, loop=True)
        anims["cannon_aim_end"] = dict(fn=aim_end, length=0.35, dt=0.05, loop=False)
        anims["cannon_fire"] = dict(fn=fire, length=0.6, dt=0.02, loop=False)
        anims["cannon_reload"] = dict(fn=reload, length=1.9, dt=0.02, loop=False)
        anims["cannon_spinup"] = dict(fn=spinup, length=1.0, dt=0.05, loop=True)

    if m.variant == "cannon_item":
        # standalone cannon: barrel points -Z, breech hinge on top. One controller for all of these.
        anims = {}

        def cog(out, a):
            add(out, "gear_cannon", (-a, 0, 0))

        def ci_idle(t):         # loop 4s: cog ticks over, tank breathes a little
            out = {}
            cog(out, 45 * t)
            add(out, "cannon_hose", scl=(1, 1 + 0.02 * math.sin(TAU * t / 4.0), 1))
            return out

        def ci_fire(t):         # 0.6s: trigger pull, barrel kicks back, muzzle flash, cog jumps
            out = {}
            rec = t / 0.04 if t < 0.04 else max(0.0, 1 - (t - 0.04) / 0.4) ** 2
            cog(out, 200 * smooth(t / 0.5))
            add(out, "cannon_trigger", (-25 * max(0.0, 1 - abs(t - 0.03) / 0.12), 0, 0))
            add(out, "cannon_barrel", pos=(0, 0, 1.4 * rec))
            add(out, "steam_cannon", (-6 * rec, 0, 0), pos=(0, 0, 0.5 * rec))   # whole gun kicks up/back
            if 0.01 <= t < 0.14:
                k = 2.4 - 6 * (t - 0.01)
                add(out, "cannon_flash", pos=(0, 0, -3.5), scl=(k, k, k * 1.3))
            return out

        def ci_spinup(t):       # loop 1s: charging - cog races, barrel shivers
            out = {}
            cog(out, 720 * t)
            add(out, "cannon_barrel", pos=(0.08 * math.sin(TAU * t * 10), 0.1 * math.sin(TAU * t * 7), 0))
            return out

        # reload 1.5s: barrel breaks open downward, spent shell pops up and out, fresh one drops in, snap shut, vent
        def ci_brk(t):
            return smooth(t / 0.25) if t < 1.05 else 1 - min(1.0, (t - 1.05) / 0.1) ** 2

        def ci_reload(t):
            out = {}
            cog(out, 220 * smooth(t / 0.3) + 300 * smooth((t - 1.05) / 0.3))
            add(out, "cannon_barrel", (35 * ci_brk(t), 0, 0), pos=(0, 0, -0.4 * max(0.0, 1 - abs(t - 1.17) / 0.06)))
            if 0.3 <= t < 0.6:                                   # spent shell flies up and back
                e = (t - 0.3) / 0.3
                k = 1.0 if e < 0.7 else (1 - e) / 0.3
                add(out, "cannon_shell", (420 * e, 0, 0), pos=(0, 6.5 * e - 4.0 * e * e, 4.0 * e), scl=(k, k, k))
            elif 0.6 <= t < 0.65:
                add(out, "cannon_shell", scl=(0, 0, 0))
            elif 0.65 <= t < 1.0:                                # fresh cartridge appears above and drops in
                e = (t - 0.65) / 0.35
                k = min(1.0, e / 0.2)
                add(out, "cannon_shell", pos=(0, 3.5 * (1 - smooth(e)), 0.8 * (1 - smooth(e))), scl=(k, k, k))
            if 1.12 <= t < 1.5:                                  # steam vents out of the left side
                e = (t - 1.12) / 0.38
                k = 1.0 + 2.4 * e if e < 0.8 else (1 - e) / 0.2 * 2.92
                add(out, "cannon_vent", pos=(2.5 + 2.5 * e, 1.5 * e, 0), scl=(k, k, k))
            return out

        anims["idle"] = dict(fn=ci_idle, length=4.0, dt=0.25, loop=True)
        anims["fire"] = dict(fn=ci_fire, length=0.6, dt=0.02, loop=False)
        anims["spinup"] = dict(fn=ci_spinup, length=1.0, dt=0.05, loop=True)
        anims["reload"] = dict(fn=ci_reload, length=1.5, dt=0.02, loop=False)
        return anims

    if m.variant == "elytra_item":
        del anims["overdrive"]

    if m.variant in ("elytra", "elytra_item"):
        def wings(out, p, sweep, flutter=0.0, t=0.0, cog=0.0):
            """p[k] in 0..1 per panel (0 folded, 1 spread). Left wing mirrors the right."""
            for k in range(EL_PANELS):
                d = (EL_DEPLOY[k] - EL_FOLD[k]) * p[k] + flutter * math.sin(TAU * t / 2.0 + k * 0.7)
                add(out, f"elytra_r_panel{k}", (0, 0, d))
                add(out, f"elytra_l_panel{k}", (0, 0, -d))
            add(out, "elytraRightWing", (0, -sweep, 0))
            add(out, "elytraLeftWing", (0, sweep, 0))
            add(out, "elytra_cog_r_drive", (0, 0, -cog))
            add(out, "elytra_cog_l_drive", (0, 0, cog))

        def deploy_p(t):  # cascading unfold over 0.9s
            return [smooth((t - 0.07 * k) / 0.6) for k in range(EL_PANELS)]

        def deploy(t):
            out = {}
            p = deploy_p(t)
            wings(out, p, 14 * smooth(t / 0.9), cog=270 * smooth(t / 0.9))
            return out

        def glide(t):
            out = {}
            wings(out, [1.0] * EL_PANELS, 14 + 2.5 * math.sin(TAU * t / 2.0), flutter=2.0, t=t, cog=270 + 180 * t)
            return out

        def fold(t):
            out = {}
            s = 0.9 - t * 0.9 / 0.8
            p = [smooth((s - 0.07 * (EL_PANELS - 1 - k)) / 0.6) for k in range(EL_PANELS)]
            wings(out, p, 14 * smooth(s / 0.9), cog=270 * smooth(s / 0.9))
            return out

        anims["elytra_deploy"] = dict(fn=deploy, length=0.9, dt=0.05, loop="hold_on_last_frame")
        anims["elytra_glide"] = dict(fn=glide, length=2.0, dt=0.1, loop=True)
        anims["elytra_fold"] = dict(fn=fold, length=0.8, dt=0.05, loop=False)
    return anims


def export_animations(m, path):
    anims = make_animations(m)
    out = {"format_version": "1.8.0", "animations": {}}
    for name, a in anims.items():
        n = int(round(a["length"] / a["dt"]))
        times = [round(i * a["dt"], 4) for i in range(n + 1)]
        frames = [a["fn"](t) for t in times]
        bones = {}
        for b in m.bones:
            bn = b["name"]
            chans = {}
            for ci, (cname, ident) in enumerate((("rotation", 0.0), ("position", 0.0), ("scale", 1.0))):
                vals = [[round(float(x), 3) for x in f.get(bn, ([0] * 3, [0] * 3, [1] * 3))[ci]] for f in frames]
                if all(abs(v - ident) < 1e-3 for vv in vals for v in vv):
                    continue
                keys = {}
                for i, (t, v) in enumerate(zip(times, vals)):
                    if 0 < i < len(times) - 1:
                        pv, nv = vals[i - 1], vals[i + 1]
                        if all(abs((pv[k] + nv[k]) / 2 - v[k]) < 1e-3 for k in range(3)):
                            continue
                    keys[f"{t:g}"] = v
                chans[cname] = keys
            if chans:
                bones[bn] = chans
        out["animations"][f"animation.{NAME}.{name}"] = {"loop": a["loop"], "animation_length": a["length"],
                                                         "bones": bones}
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(out, fh, indent="\t")
    return anims


# ============================================================================ item icons (16x16)
ICON_PAL = {
    "k": (34, 26, 22), "B": (226, 194, 104), "b": (184, 150, 66), "d": (128, 100, 44),
    "I": (122, 124, 128), "i": (84, 86, 90), "j": (56, 57, 60), "C": (230, 150, 96), "c": (186, 104, 60),
    "O": (255, 222, 110), "o": (246, 146, 30), "w": (230, 226, 206), "r": (190, 40, 30),
    "l": (120, 72, 42), "L": (86, 50, 30), "s": (176, 178, 184), "g": (60, 60, 64),
}
ICONS = {
    "steam_helmet": [
        "......kkkk......",
        "....kkdBBdkk....",
        "...kjjdBbdjjk...",
        "..kIijdBbdjiik..",
        "..kIiijbbjiiik..",
        "..kbBBBBBBBBbk..",
        "..kbkkkkkkkkbk..",
        "..kbkOOOOOOkbk..",
        "..kdkooooookdk..",
        "..kdkkkkkkkkdk..",
        "..kbbbkjjkbbbk..",
        "..kddkcjjckddk..",
        "...kkcCjjCckk...",
        "....kkcwwckk....",
        "......kkkk......",
        "................",
    ],
    "steam_chestplate": [
        ".kkkkk....kkkkk.",
        "kBBBBbkkkkbBBBBk",
        "kBbbbdkcckdbbbbk",
        "kbbbdkBccBkdbbbk",
        "kdddkcBbbBckdddk",
        ".kiikckwwkckiik.",
        ".kIikbwwrwbkiIk.",
        ".kIikbkwwkbkiIk.",
        ".kCckbbbbbbkcCk.",
        ".kBbkdccccdkbBk.",
        ".kbdkjjjjjjkdbk.",
        ".kiiklllClllkiik",
        ".kkkkLLLcLLLkkkk",
        "....klLkkLlk....",
        "....kkk..kkk....",
        "................",
    ],
    "steam_leggings": [
        "................",
        "..kkkkkkkkkkkk..",
        "..kIiiiiiiiiik..",
        "..kiBbbkkbbBik..",
        "..kiBbdkkdbBik..",
        "..kccck..kccck..",
        "..kIiik..kiiIk..",
        "..kBbbk..kbbBk..",
        "..kbbdk..kdbbk..",
        "..kddgk..kgddk..",
        "..kIiik..kiiIk..",
        "..kIijk..kjiIk..",
        "..kiijk..kjiik..",
        "..kkkkk..kkkkk..",
        "................",
        "................",
    ],
    "steam_boots": [
        "................",
        "................",
        "................",
        "..kkkkk..kkkkk..",
        "..kCcck..kccCk..",
        "..kIiik..kiiIk..",
        "..kIijk..kjiIk..",
        "..kbBbk..kbBbk..",
        "..kIijk..kjiIk..",
        ".kkIijk..kjiIkk.",
        "kCcIijk..kjiIcCk",
        "kcciiik..kiiicck",
        "kggggggkkggggggk",
        "kkkkkkkkkkkkkkkk",
        "................",
        "................",
    ],
    "steam_arm_cannon": [
        "................",
        "...kkkkkk.......",
        "..kIIIIiik......",
        "..kIkkkkik......",
        "..kIkOOkikkkkkk.",
        ".kkIkookikjjjjkk",
        "kBbkkkkkkiCCCcCk",
        "kbdBkiiijicccccC",
        "kBbkkiiijiccccck",
        ".kkIiiiijkjjjjkk",
        "..kygygyjkkkkkk.",
        "..kkkkkkkk......",
        "....kccck.......",
        "....kcCck.......",
        ".....kkk........",
        "................",
    ],
    "steam_elytra_module": [
        "................",
        "kk............kk",
        "kwk..........kwk",
        "kwwk........kwwk",
        "klwwk.kkkk.kwwlk",
        "kwwwwkaaaakwwwwk",
        "kwlwwkaxxakwwlwk",
        "kwwwwkaxOakwwwwk",
        "klwwwkaxOakwwwlk",
        "kwwwkkaxxakkwwwk",
        "kwwk.kaaaak.kwwk",
        "klk..kBBBBk..klk",
        "kk...kkkkkk...kk",
        "................",
        "................",
        "................",
    ],
}
ICONS["steam_elytra"] = ICONS["steam_elytra_module"]   # standalone wearable elytra uses the same icon
ICON_PAL.update({"y": (214, 168, 40), "a": (150, 152, 146), "x": (40, 34, 30)})


def make_icon(rows):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        assert len(row) == 16, (row, len(row))
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), ICON_PAL.get(ch, ICON_PAL["k"]) + (255,))
    return img


# ============================================================================ preview renderer
def render(model, tex, glow, uv, mats, eye, scale=24, title=None, bounds=None, hidden=()):
    eye = np.array(eye, dtype=float)
    fwd = -eye / np.linalg.norm(eye)
    right = np.cross(fwd, [0, 1, 0])
    right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    V = np.array([right, up, fwd])
    light = np.array([-0.4, 0.8, -0.45])
    light /= np.linalg.norm(light)
    faces = []
    for b in model.bones:
        if any(b["name"].startswith(h) for h in hidden):
            continue
        M = mats[b["name"]]
        for c in b["cubes"]:
            co = cube_corners(c)
            for face, sel in FACE_CORNERS.items():
                tl, tr, bl = (np.array(p, dtype=float) for p in sel(*co))
                w = [(M @ np.append(p, 1))[:3] for p in (tl, tr, bl, tr + bl - tl)]
                n = np.cross(w[1] - w[0], w[2] - w[0])
                nn = np.linalg.norm(n)
                if nn < 1e-9:
                    continue
                n = -n / nn
                if (V @ n)[2] >= 0:
                    continue
                cam = [V @ p for p in w]
                faces.append((sum(p[2] for p in cam) / 4, cam, c, face, 0.55 + 0.45 * max(0.0, float(n @ light))))
    if bounds is None:
        pts = np.array([p for f in faces for p in f[1]])
        bounds = (pts.min(axis=0), pts.max(axis=0))
    mn, mx = bounds
    pad, top = 16, (22 if title else 0)
    W = int((mx[0] - mn[0]) * scale) + 2 * pad
    H = int((mx[1] - mn[1]) * scale) + 2 * pad + top
    img = Image.new("RGB", (W, H), (24, 24, 26))
    dr = ImageDraw.Draw(img)
    if title:
        dr.text((8, 5), title, fill=(200, 200, 200))
    rgb = tex.convert("RGB")

    def to2d(p):
        return np.array(((p[0] - mn[0]) * scale + pad, (mx[1] - p[1]) * scale + pad + top))

    faces.sort(key=lambda f: -f[0])
    for depth, cam, c, face, bright in faces:
        p0, p1, p2, p3 = (to2d(p) for p in cam)
        u0, v0, w, h = uv[(id(c), face)]
        xs, ys = [p0[0], p1[0], p2[0], p3[0]], [p0[1], p1[1], p2[1], p3[1]]
        bx0, by0 = int(math.floor(min(xs))), int(math.floor(min(ys)))
        bx1, by1 = int(math.ceil(max(xs))) + 1, int(math.ceil(max(ys))) + 1
        e1, e2 = p1 - p0, p2 - p0
        det = e1[0] * e2[1] - e2[0] * e1[1]
        if abs(det) < 1e-6:
            continue
        off = np.array((bx0, by0))
        mask = Image.new("L", (bx1 - bx0, by1 - by0), 0)
        ImageDraw.Draw(mask).polygon([tuple(p - off) for p in (p0, p1, p3, p2)], fill=255)
        P0 = p0 - off
        a, b_ = w * e2[1] / det, -w * e2[0] / det
        d, e = -h * e1[1] / det, h * e1[0] / det
        coef = (a, b_, -(a * P0[0] + b_ * P0[1]), d, e, -(d * P0[0] + e * P0[1]))
        patch = rgb.crop((u0, v0, u0 + w, v0 + h)).transform(mask.size, Image.AFFINE, coef, resample=Image.NEAREST)
        img.paste(patch.point(lambda v: clamp(v * bright)), (bx0, by0), mask)
        g = glow.crop((u0, v0, u0 + w, v0 + h)).transform(mask.size, Image.AFFINE, coef, resample=Image.NEAREST)
        ga = Image.fromarray(np.minimum(np.array(g.split()[3]), np.array(mask)).astype(np.uint8))
        img.paste(g.convert("RGB"), (bx0, by0), ga)
    return img


# ============================================================================ main
VARIANTS = [("steam_armor", "base", "armor"), ("steam_armor_cannon", "cannon", "cannon"),
            ("steam_armor_elytra", "elytra", "elytra"),
            ("steam_cannon", "cannon_item", "cannon_item"),     # standalone cannon, GeckoLib item (geo/item)
            ("steam_elytra", "elytra_item", "elytra_item")]     # standalone wearable elytra (geo/armor)
ITEM_VARIANTS = {"cannon_item"}
CANNON_ITEM_DISPLAY = {  # models/item/steam_cannon.json (builtin/entity -> GeoItemRenderer)
    "thirdperson_righthand": {"rotation": [0, 180, 0], "translation": [0, 2, -1], "scale": [0.6, 0.6, 0.6]},
    "thirdperson_lefthand": {"rotation": [0, 180, 0], "translation": [0, 2, -1], "scale": [0.6, 0.6, 0.6]},
    "firstperson_righthand": {"rotation": [0, 175, 0], "translation": [1, 1, -2], "scale": [0.6, 0.6, 0.6]},
    "firstperson_lefthand": {"rotation": [0, 185, 0], "translation": [-1, 1, -2], "scale": [0.6, 0.6, 0.6]},
    "ground": {"translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
    "gui": {"rotation": [30, 135, 0], "translation": [0, -1.5, 0], "scale": [0.55, 0.55, 0.55]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "fixed": {"rotation": [0, 90, 0], "translation": [0, -1, 0], "scale": [0.7, 0.7, 0.7]},
}


def main():
    global NAME, TEX
    only = sys.argv[sys.argv.index("--variant") + 1] if "--variant" in sys.argv else None
    for name, variant, prefix in VARIANTS:
        if only and only != variant:
            continue
        NAME, TEX = name, 256
        print(f"== {name}")
        run_variant(variant, prefix)


def run_variant(variant, prefix):
    m = build(variant)
    tex, glow, uv, used = pack_and_paint(m, random.Random(77))
    mats = bone_matrices(m)
    pts = []
    for b in m.bones:
        for c in b["cubes"]:
            a, bb, cc, d, e, f = cube_corners(c)
            pts += [(mats[b["name"]] @ np.array([x, y, z, 1.0]))[:3] for x in (a, d) for y in (bb, e) for z in (cc, f)]
    pts = np.array(pts)
    print(f"bbox {np.round(pts.min(0), 1)} .. {np.round(pts.max(0), 1)}")
    print(f"bones {len(m.bones)}, cubes {sum(len(b['cubes']) for b in m.bones)}, texture {TEX}, rows used {used}")

    dirs = {k: os.path.join(OUT, *k.split("/")) for k in
            ("geo/armor", "animations/armor", "textures/armor", "geo/item", "animations/item", "textures/item",
             "models/item")}
    for d in dirs.values():
        os.makedirs(d, exist_ok=True)
    os.makedirs(PREVIEW, exist_ok=True)
    kind = "item" if variant in ITEM_VARIANTS else "armor"
    export_geo(m, uv, os.path.join(dirs[f"geo/{kind}"], f"{NAME}.geo.json"))
    tex.save(os.path.join(dirs[f"textures/{kind}"], f"{NAME}.png"))
    glow.save(os.path.join(dirs[f"textures/{kind}"], f"{NAME}_glowmask.png"))
    anims = export_animations(m, os.path.join(dirs[f"animations/{kind}"], f"{NAME}.animation.json"))
    modid = "flesh_spread"
    if "--modid" in sys.argv:
        modid = sys.argv[sys.argv.index("--modid") + 1]
    if kind == "item":
        with open(os.path.join(dirs["models/item"], f"{NAME}.json"), "w", encoding="utf-8") as fh:
            json.dump({"parent": "builtin/entity", "gui_light": "front", "display": CANNON_ITEM_DISPLAY}, fh, indent=2)
    sheet = Image.new("RGBA", (136 * len(ICONS), 16 * 8), (24, 24, 26, 255))
    for i, (iname, rows) in enumerate(ICONS.items()):
        icon = make_icon(rows)
        icon.save(os.path.join(dirs["textures/item"], f"{iname}.png"))
        with open(os.path.join(dirs["models/item"], f"{iname}.json"), "w", encoding="utf-8") as fh:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"{modid}:item/{iname}"}}, fh,
                      indent=2)
        big = icon.resize((128, 128), Image.NEAREST)
        sheet.paste(big, (i * 136, 0), big)
    sheet.save(os.path.join(PREVIEW, "icons_x8.png"))

    # previews: full set + a pose with arms/legs swung
    views = [("front", (0, 0.15, -1)), ("34", (-0.8, 0.35, -1)), ("back", (0, 0.2, 1)), ("back34", (0.8, 0.3, 1)),
             ("side", (1, 0.05, 0))]
    for vname, eye in views:
        render(m, tex, glow, uv, mats, eye, scale=16, title=vname).save(os.path.join(PREVIEW, f"{prefix}_{vname}.png"))
    walk = {"armorRightArm": (-35, 0, 0), "armorLeftArm": (35, 0, 0), "armorRightLeg": (30, 0, 0),
            "armorRightBoot": (30, 0, 0), "armorLeftLeg": (-30, 0, 0), "armorLeftBoot": (-30, 0, 0)}
    if kind == "armor":
        render(m, tex, glow, uv, bone_matrices(m, pose=walk), (-0.8, 0.35, -1), scale=16, title="walk pose")         .save(os.path.join(PREVIEW, f"{prefix}_walk.png"))
    tex.resize((TEX * 3, TEX * 3), Image.NEAREST).save(os.path.join(PREVIEW, f"{prefix}_texture_x3.png"))
    if variant == "base":
        for slot, keep in (("helmet", "armorHead"), ("chestplate", ("armorBody", "armorRightArm", "armorLeftArm")),
                           ("leggings", ("armorRightLeg", "armorLeftLeg")), ("boots", ("armorRightBoot", "armorLeftBoot"))):
            keep = (keep,) if isinstance(keep, str) else keep
            hidden = [b["name"] for b in m.bones if b["parent"] is None and b["name"] not in keep]
            render(m, tex, glow, uv, mats, (-0.8, 0.35, -1), scale=16, title=slot, hidden=hidden)                 .save(os.path.join(PREVIEW, f"slot_{slot}.png"))
    if variant == "cannon":
        aim = anims["cannon_aim"]["fn"](0.0)
        for vname, eye in (("aim", (-0.9, 0.3, -1)), ("aim_side", (-1, 0.1, 0))):
            render(m, tex, glow, uv, bone_matrices(m, aim), eye, scale=16, title=vname) \
                .save(os.path.join(PREVIEW, f"cannon_{vname}.png"))
        render(m, tex, glow, uv, bone_matrices(m, anims["cannon_fire"]["fn"](0.05)), (-0.9, 0.3, -1), scale=16,
               title="firing").save(os.path.join(PREVIEW, "cannon_firing.png"))
        render(m, tex, glow, uv, bone_matrices(m, anims["cannon_reload"]["fn"](1.0)), (-1, 0.2, -0.5), scale=16,
               title="reloading").save(os.path.join(PREVIEW, "cannon_reloading.png"))
    if variant == "cannon_item":
        for nm, a, t, eye in (("firing", "fire", 0.05, (-0.9, 0.3, -1)), ("reloading", "reload", 0.45, (-1, 0.3, 0.4)),
                              ("open", "reload", 0.7, (-1, 0.5, 0.2))):
            render(m, tex, glow, uv, bone_matrices(m, anims[a]["fn"](t)), eye, scale=20, title=nm)                 .save(os.path.join(PREVIEW, f"cannon_item_{nm}.png"))
    if variant in ("elytra", "elytra_item"):
        spread = anims["elytra_glide"]["fn"](0.0)
        for vname, eye in (("spread_back", (0, 0.2, 1)), ("spread_back34", (0.8, 0.5, 1)), ("spread_top", (0.2, 1, 0.3))):
            render(m, tex, glow, uv, bone_matrices(m, spread), eye, scale=12, title=vname)                 .save(os.path.join(PREVIEW, f"{prefix}_{vname}.png"))
    if "--anim" in sys.argv:
        skip = {"idle", "overdrive"} if variant not in ("base", "cannon_item") else set()
        for name, a in anims.items():
            if name in skip:
                continue
            n = 8
            times = [a["length"] * i / n for i in range(n)] if a["loop"] is True else                 [a["length"] * i / (n - 1) for i in range(n)]
            eye = (0.8, 0.3, 1) if name.startswith("elytra") else (-0.8, 0.35, -1)
            bounds = (np.array([-24, -2, 0]), np.array([24, 44, 0]))
            if variant == "cannon_item":
                eye, bounds = (-1, 0.35, -0.6), (np.array([-11, -3, 0]), np.array([11, 16, 0]))
            pose = None
            if name == "cannon_reload":
                n = 8
                times = [0.0, 0.3, 0.6, 0.9, 1.05, 1.2, 1.45, 1.9]
                eye = (-1, 0.2, -0.5)
            frames = [render(m, tex, glow, uv, bone_matrices(m, a["fn"](t), pose=pose), eye,
                             scale=16 if variant == "cannon_item" else 8,
                             title=f"{name} t={t:.2f}", bounds=bounds)
                      for t in times]
            W, H = frames[0].size
            sh = Image.new("RGB", (W * 4, H * 2))
            for i, f in enumerate(frames):
                sh.paste(f, ((i % 4) * W, (i // 4) * H))
            tag = "cannon_item_" if variant == "cannon_item" else ("elytra_item_" if variant == "elytra_item" else "")
            sh.save(os.path.join(PREVIEW, f"anim_{tag}{name}.png"))

if __name__ == "__main__":
    main()
