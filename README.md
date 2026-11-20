# Create Armor — steampunk GeckoLib armor (Forge 1.20.1, GeckoLib 4)

Context repo for AI assistants: a procedural generator plus its generated assets and preview renders.

- `armor.jpg` — reference concept art.
- `tools/gen_armor.py` — single-file generator (Python 3, numpy, Pillow). Builds the `steam_armor` set and its
  `steam_armor_cannon` / `steam_armor_elytra` variants: geometry, UV-packed textures + glowmasks, animations,
  item models/icons. Run `python tools/gen_armor.py [--out <assets/modid dir>]`.
- `steam_armor_extendo` (`--variant extendo`) — base armor + a back module with **two Create extendo grips** as
  extra arms (the Extendo Grip item itself already exists in Create; this is only the armor model). Each arm sits on a
  brass-casing bearing on the side of the backpack (mid-back); at rest the arms leave the back sideways and curve
  up (Doc Ock style, segmented tentacle necks); on a strike the tentacle rises off the back, arcs over the shoulder
  and the handle rolls its pistol grip outward so nothing clips the helmet or pauldrons. Bones (right; left = `extendo_l*`): `extendo_r` (hinge, swing), `extendo_r_neck0..3`
  (curved vertebrae, straighten on a strike), `extendo_r_grip` > `extendo_r_cog`, scissor segments `extendo_r_seg0..2` with bars
  `_seg{k}a/_seg{k}b`, `extendo_r_claw` > `_claw_top/_claw_bot`. Anims (own "extendo" controller): `extendo_idle`,
  `extendo_strike` (loop: right then left - play while the player attacks / mines), `extendo_strike_right`,
  `extendo_strike_left` (one strike each - alternate them per swing from code). Icon: `steam_extendo_module`.
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
- `steam_villager` — `tools/gen_villager.py` (reuses `gen_armor.py`): a villager clad in the same steampunk armor
  (closed helmet with a full face mask, glowing goggle eyes and a respirator snout over the nose - no skin visible; brass breastplate, tassets, pauldrons/bracers and full gauntlets on the crossed arms, greaves +
  boots, Create wrench on the hip) with a Create-style backpack (brass casing, two copper tanks, big + small
  cogwheel, pressure gauge, two exhausts with steam). GeckoLib **entity**: `geo/entity`, `animations/entity`,
  `textures/entity/steam_villager.png` + glowmask. Bones: `head`>`nose`, `body`>`backpack`, `arms`, `right_leg`,
  `left_leg`. Anims: `idle`, `walk`, `work`, `head_shake`. Run `python tools/gen_villager.py [--anim]`.
- `output/` — generated assets in GeckoLib `DefaultedItemGeoModel` layout (`geo/armor`, `animations/armor`,
  `textures/armor`, `textures/item`, `models/item`).
- `tools/preview/` — renders of the model (front/side/back/3-4 views, texture sheets, animation frames) for visual checks.

Bones follow GeckoLib's armor template (armorHead, armorBody, armorRightArm, …). 1 block = 16 px, model faces -Z.

## Мод (Forge 1.20.1) — `src/`

- Паровий сет (шолом/нагрудник/поножі/чоботи), GeckoLib-рендер; модель змінюється за імплантом (`steam_armor_cannon/elytra/extendo`).
- Повний сет: +4 шкоди (для будь-якої істоти, зокрема рекрутів) і слот імпланта (клавіша **K**).
- Імпланти: будь-який нагрудник (передає броню/міцність, малюється поверх), Extendo Grip / модуль (+3 досяжності),
  парові крила або модуль (політ + паровий поштовх: тримай і відпусти пробіл у польоті), наручна гармата (поки лише модель).
- Парові крила носяться й окремо в слоті нагрудника. Пара заряджається Shift+ПКМ по бектанку Create, що крутиться від вала.
- Крафт: `steam_plate` — sequenced assembly (sturdy sheet → brass sheet → алмаз → 250 mB води → прес, 2 цикли);
  броня/крила/модулі — mechanical crafting. Гармати потребують Create Big Cannons.
