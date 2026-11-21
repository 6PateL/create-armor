package com.createarmor;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.core.animation.RawAnimation;

import java.util.HashMap;
import java.util.Map;

/** Вибір анімацій крил і extendo-рук (спільно для окремих крил і крил-імпланта). */
final class WingAnims {
    private static final Map<String, RawAnimation[]> CACHE = new HashMap<>();
    private static final Map<Integer, Boolean> LEFT_NEXT = new HashMap<>();
    private static final Map<Integer, Boolean> WAS_SWINGING = new HashMap<>();
    private static final Map<Integer, Boolean> LAST_LEFT = new HashMap<>();

    private WingAnims() {
    }

    private static RawAnimation[] of(String model) {
        return CACHE.computeIfAbsent(model, m -> new RawAnimation[]{
                RawAnimation.begin().thenLoop("animation." + m + ".idle"),
                // розкрити й тримати, далі ковзання
                RawAnimation.begin().thenPlay("animation." + m + ".elytra_deploy").thenLoop("animation." + m + ".elytra_glide"),
                // присів — розправити й тримати
                RawAnimation.begin().thenPlayAndHold("animation." + m + ".elytra_deploy"),
                // скласти й повернутися до спокою
                RawAnimation.begin().thenPlay("animation." + m + ".elytra_fold").thenLoop("animation." + m + ".idle")});
    }

    static RawAnimation pick(String model, Entity e) {
        RawAnimation[] a = of(model);
        if (e instanceof LivingEntity le) {
            if (le.isFallFlying()) {
                return a[1];
            }
            if (le.isCrouching()) {
                return a[2];
            }
        }
        return a[3];
    }

    private static final RawAnimation EXT_IDLE = RawAnimation.begin().thenLoop("animation.steam_armor_extendo.extendo_idle");
    private static final RawAnimation EXT_RIGHT = RawAnimation.begin().thenPlay("animation.steam_armor_extendo.extendo_strike_right")
            .thenLoop("animation.steam_armor_extendo.extendo_idle");
    private static final RawAnimation EXT_LEFT = RawAnimation.begin().thenPlay("animation.steam_armor_extendo.extendo_strike_left")
            .thenLoop("animation.steam_armor_extendo.extendo_idle");

    /** Кожен новий змах — по черзі правою й лівою extendo-рукою. */
    static RawAnimation extendo(Entity e) {
        if (!(e instanceof LivingEntity le)) {
            return EXT_IDLE;
        }
        int id = le.getId();
        boolean swinging = le.swinging;
        boolean was = WAS_SWINGING.getOrDefault(id, false);
        WAS_SWINGING.put(id, swinging);
        if (swinging && !was) {
            boolean left = LEFT_NEXT.getOrDefault(id, false);
            LEFT_NEXT.put(id, !left);
            LAST_LEFT.put(id, left);
            return left ? EXT_LEFT : EXT_RIGHT;
        }
        Boolean last = LAST_LEFT.get(id);
        return last == null ? EXT_IDLE : last ? EXT_LEFT : EXT_RIGHT;
    }
}
