package com.createarmor;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/**
 * Парові крила: носяться в слоті нагрудника (як елітра) або вставляються імплантом у паровий нагрудник. Літають як
 * елітра, а ще мають паровий поштовх (тримай і відпусти пробіл у польоті), що витрачає заряд.
 */
public class SteamElytraItem extends ArmorItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public SteamElytraItem() {
        super(ArmorMaterials.LEATHER, Type.CHESTPLATE, new Properties().stacksTo(1).rarity(Rarity.EPIC).durability(864).fireResistant());
    }

    @Override
    public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext ctx) {
        return Steam.place(ctx);
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level level, java.util.List<net.minecraft.network.chat.Component> tip,
                                net.minecraft.world.item.TooltipFlag flag) {
        tip.add(net.minecraft.network.chat.Component.translatable("message.createarmor.charged", Steam.get(stack), Steam.MAX)
                .withStyle(net.minecraft.ChatFormatting.WHITE));
        tip.add(net.minecraft.network.chat.Component.translatable("tooltip.createarmor.wings_charge")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13F * Steam.get(stack) / Steam.MAX);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xE8E0D0;
    }

    @Override
    public boolean canElytraFly(ItemStack stack, LivingEntity entity) {
        return stack.getDamageValue() < stack.getMaxDamage() - 1;
    }

    @Override
    public boolean elytraFlightTick(ItemStack stack, LivingEntity entity, int flightTicks) {
        if (!entity.level().isClientSide && (flightTicks + 1) % 40 == 0) {
            stack.hurtAndBreak(1, entity, e -> e.broadcastBreakEvent(EquipmentSlot.CHEST));
        }
        return true;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private Object renderer;

            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
                if (renderer == null) {
                    renderer = ArmorClient.elytra();
                }
                return ArmorClient.prep(renderer, entity, stack, slot, original);
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, st -> {
            Entity e = st.getData(DataTickets.ENTITY);
            if (e instanceof LivingEntity le && le.isFallFlying()) {
                return st.setAndContinue(RawAnimation.begin().thenLoop("animation.steam_elytra.elytra_glide"));
            }
            return st.setAndContinue(RawAnimation.begin().thenLoop("animation.steam_elytra.idle"));
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
