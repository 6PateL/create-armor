package com.createarmor;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Слот імпланта парового нагрудника: зберігається в NBT нагрудника, працює лише з повним сетом. */
public final class Implants {
    public enum Kind { NONE, CHESTPLATE, EXTENDO, ELYTRA, CANNON }

    public static final String TAG = "Implant";
    private static final EquipmentSlot[] SET = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private Implants() {
    }

    public static boolean fullSet(LivingEntity e) {
        for (EquipmentSlot s : SET) {
            if (!(e.getItemBySlot(s).getItem() instanceof SteamArmorItem)) {
                return false;
            }
        }
        return true;
    }

    public static ItemStack implant(ItemStack chest) {
        CompoundTag t = chest.getTag();
        if (t == null || !t.contains(TAG)) {
            return ItemStack.EMPTY;
        }
        return ItemStack.of(t.getCompound(TAG));
    }

    public static void setImplant(ItemStack chest, ItemStack implant) {
        if (implant.isEmpty()) {
            if (chest.getTag() != null) {
                chest.getTag().remove(TAG);
                if (chest.getTag().isEmpty()) {
                    chest.setTag(null);
                }
            }
        } else {
            chest.getOrCreateTag().put(TAG, implant.save(new CompoundTag()));
        }
    }

    /**
     * Що можна вставити в слот імпланта: лише імпланти (модулі крил, extendo, наручна гармата) та речі, що й так
     * працюють як імпланти — бектанк Create (акваланг) і риг Octo Arms (вони бачать слот як нагрудник).
     */
    public static Kind kindOf(ItemStack s) {
        if (s.isEmpty()) {
            return Kind.NONE;
        }
        Item i = s.getItem();
        if (i == CreateArmor.ELYTRA_MODULE.get()) {
            return Kind.ELYTRA;
        }
        if (i == CreateArmor.EXTENDO_MODULE.get()) {
            return Kind.EXTENDO;
        }
        if (i == CreateArmor.ARM_CANNON.get()) {
            return Kind.CANNON;
        }
        return LiveImplant.isBacktank(s) ? Kind.CHESTPLATE : Kind.NONE;
    }

    public static boolean allowed(ItemStack s) {
        return kindOf(s) != Kind.NONE;
    }

    /** Імплант, що зараз діє (повний сет + щось у слоті). */
    public static ItemStack active(LivingEntity e) {
        if (e == null || !fullSet(e)) {
            return ItemStack.EMPTY;
        }
        return implant(e.getItemBySlot(EquipmentSlot.CHEST));
    }

    public static Kind kind(LivingEntity e) {
        return kindOf(active(e));
    }

    public static boolean canFlyNow(LivingEntity e) {
        return true;
    }

    /** Варіант моделі обладунку залежно від імпланта. */
    public static String variant(Object entity) {
        if (entity instanceof LivingEntity le) {
            return switch (kind(le)) {
                case EXTENDO -> "steam_armor_extendo";
                case ELYTRA -> "steam_armor_elytra";
                case CANNON -> "steam_armor_cannon";
                default -> "steam_armor";
            };
        }
        return "steam_armor";
    }
}
