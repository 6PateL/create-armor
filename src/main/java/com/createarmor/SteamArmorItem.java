package com.createarmor;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
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
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import software.bernie.geckolib.core.object.PlayState;

/**
 * Частина парового обладунку (GeckoLib). Нагрудник носить у собі імплант (NBT "Implant"): від нього залежить модель
 * (з гарматою на руці, з крилами, з руками-extendo) і можливості. Імплант працює лише з повним сетом.
 */
public class SteamArmorItem extends ArmorItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public SteamArmorItem(Type type) {
        super(SteamMaterial.INSTANCE, type, new Properties().rarity(Rarity.RARE).fireResistant());
    }

    /** Парові крила як імплант: нагрудник дає політ, як елітра. */
    @Override
    public boolean canElytraFly(ItemStack stack, LivingEntity entity) {
        return type == Type.CHESTPLATE && Implants.kind(entity) == Implants.Kind.ELYTRA && Implants.canFlyNow(entity);
    }

    @Override
    public boolean elytraFlightTick(ItemStack stack, LivingEntity entity, int flightTicks) {
        return canElytraFly(stack, entity);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private Object renderer;

            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
                if (renderer == null) {
                    renderer = ArmorClient.armor();
                }
                return ArmorClient.prep(renderer, entity, stack, slot, original);
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, st -> {
            Entity e = st.getData(DataTickets.ENTITY);
            String v = Implants.variant(e);
            if (v.equals("steam_armor_elytra")) {
                return st.setAndContinue(WingAnims.pick("steam_armor_elytra", e));
            }
            return st.setAndContinue(RawAnimation.begin().thenLoop("animation." + v + ".idle"));
        }));
        controllers.add(new AnimationController<>(this, "implant", 3, st -> {
            Entity e = st.getData(DataTickets.ENTITY);
            String v = Implants.variant(e);
            if (v.equals("steam_armor_extendo")) {
                return st.setAndContinue(WingAnims.extendo(e));
            }
            if (v.equals("steam_armor_cannon")) {
                return st.setAndContinue(WingAnims.armCannon(e));
            }
            return PlayState.STOP;
        }));
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level level, java.util.List<Component> tip, TooltipFlag flag) {
        if (type == Type.CHESTPLATE) {
            ItemStack imp = Implants.implant(stack);
            tip.add(imp.isEmpty() ? Component.translatable("tooltip.createarmor.no_implant").withStyle(ChatFormatting.GRAY)
                    : Component.translatable("tooltip.createarmor.implant", imp.getHoverName()).withStyle(ChatFormatting.GOLD));
        }
        tip.add(Component.translatable("tooltip.createarmor.set_bonus").withStyle(ChatFormatting.DARK_AQUA));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
