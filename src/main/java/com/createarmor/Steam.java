package com.createarmor;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.function.UnaryOperator;

/**
 * Запас пари парових крил (NBT "Steam" на самих крилах — окремих чи вставлених імплантом). Поповнюється кінетично:
 * Shift+ПКМ по поставленому бектанку Create, що крутиться від вала (як Octo Arms).
 */
public final class Steam {
    public static final int MAX = 1600;
    public static final int BOOST_COST = 400;
    private static final String TAG = "Steam";

    private Steam() {
    }

    public static int get(ItemStack s) {
        return s.getTag() == null ? 0 : s.getTag().getInt(TAG);
    }

    public static void set(ItemStack s, int v) {
        s.getOrCreateTag().putInt(TAG, Math.max(0, Math.min(MAX, v)));
    }

    /** Крила, якими зараз користується істота: в слоті нагрудника або імплант. */
    public static ItemStack wings(LivingEntity e) {
        ItemStack chest = e.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() instanceof SteamElytraItem) {
            return chest;
        }
        ItemStack imp = Implants.active(e);
        return Implants.kindOf(imp) == Implants.Kind.ELYTRA ? imp : ItemStack.EMPTY;
    }

    public static boolean hasWings(LivingEntity e) {
        return !wings(e).isEmpty();
    }

    public static int energy(LivingEntity e) {
        return get(wings(e));
    }

    /** Змінює запас пари; для імпланта переписує NBT нагрудника. Повертає нове значення або -1, якщо крил немає. */
    public static int modify(LivingEntity e, UnaryOperator<Integer> f) {
        ItemStack chest = e.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() instanceof SteamElytraItem) {
            set(chest, f.apply(get(chest)));
            return get(chest);
        }
        ItemStack imp = Implants.active(e);
        if (Implants.kindOf(imp) != Implants.Kind.ELYTRA) {
            return -1;
        }
        set(imp, f.apply(get(imp)));
        Implants.setImplant(chest, imp);
        return get(imp);
    }
}
