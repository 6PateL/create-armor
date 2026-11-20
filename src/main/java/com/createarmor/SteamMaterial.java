package com.createarmor;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.ForgeRegistries;

/** Паровий метал: міцніший за незерит, броня трохи краща, ремонт сталевими пластинами (steam_plate). */
public enum SteamMaterial implements ArmorMaterial {
    INSTANCE;

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> 13 * 42;
            case CHESTPLATE -> 16 * 42;
            case LEGGINGS -> 15 * 42;
            case BOOTS -> 11 * 42;
        };
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> 3;
            case CHESTPLATE -> 9;
            case LEGGINGS -> 7;
            case BOOTS -> 3;
        };
    }

    @Override
    public int getEnchantmentValue() {
        return 12;
    }

    @Override
    public SoundEvent getEquipSound() {
        SoundEvent s = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("create", "wrench_rotate"));
        return s != null ? s : SoundEvents.ARMOR_EQUIP_IRON;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.of(CreateArmor.PLATE.get());
    }

    @Override
    public String getName() {
        return CreateArmor.MOD_ID + ":steam";
    }

    @Override
    public float getToughness() {
        return 3.5F;
    }

    @Override
    public float getKnockbackResistance() {
        return 0.15F;
    }
}
