# Create Armor — steampunk GeckoLib armor (Forge 1.20.1, GeckoLib 4)

Context repo for AI assistants: a procedural generator plus its generated assets and preview renders.

- `armor.jpg` — reference concept art.
- `tools/gen_armor.py` — single-file generator (Python 3, numpy, Pillow). Builds the `steam_armor` set and its
  `steam_armor_cannon` / `steam_armor_elytra` variants: geometry, UV-packed textures + glowmasks, animations,
  item models/icons. Run `python tools/gen_armor.py [--out <assets/modid dir>]`.
- Standalone models (same generator, `--variant cannon_item` / `--variant elytra_item`):
  - `steam_cannon` — the cannon taken off the arm as its own GeckoLib **item** (`geo/item`, `animations/item`,
    `textures/item/steam_cannon.png` + glowmask, `models/item/steam_cannon.json` = `builtin/entity` with display
    transforms). Barrel points -Z, grip + stock + side steam tank. Anims: `idle`, `fire`, `spinup`, `reload`.
  - `steam_elytra` — the Create-style fan wings + module with a leather harness, wearable on their own in the chest
    slot (GeckoLib armor geo, content under `armorBody`, other armor bones empty). Anims: `idle`, `elytra_deploy`,
    `elytra_glide`, `elytra_fold`, `elytra_charge`, `elytra_charged`.
  - **Kinetic charging port** (both `steam_elytra` and `steam_armor_elytra`): a Create-style shaft input at the bottom
    of the module's back face, for charging the elytra with rotational (kinetic) energy. Bones: `kinetic_port`
    (mount point where the shaft connects, pivot `0, 14, 7.5`), `kinetic_shaft` (shaft, spins about Z), `kinetic_cog` (Create cogwheel on the shaft - this cog IS the port:
    mesh another cog with it to feed rotation in),
    `charge_bar` (glowing bar above the implant socket, pivot at its left end - set scale X = charge 0..1 from code).
    `elytra_charge` = plugged in and charging (shaft spins, bar fills), `elytra_charged` = full bar pulsing.
- `output/` — generated assets in GeckoLib `DefaultedItemGeoModel` layout (`geo/armor`, `animations/armor`,
  `textures/armor`, `textures/item`, `models/item`).
- `tools/preview/` — renders of the model (front/side/back/3-4 views, texture sheets, animation frames) for visual checks.

Bones follow GeckoLib's armor template (armorHead, armorBody, armorRightArm, …). 1 block = 16 px, model faces -Z.
