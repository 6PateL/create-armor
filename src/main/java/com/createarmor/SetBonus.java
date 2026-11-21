package com.createarmor;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.tags.FluidTags;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;

/**
 * Бонуси повного сету: менша шкода від падіння, без обмежень мода Stamina, акваланг (бектанк Create у слоті імпланта
 * дає дихати під водою, незеритовий — ще й пірнати в лаву). Стабільна температура (Cold Sweat) — через датапак
 * data/createarmor/cold_sweat/item/insulator.
 */
@Mod.EventBusSubscriber(modid = CreateArmor.MOD_ID)
public final class SetBonus {
    private static Method staminaUnlock, staminaSet, staminaMax, consumeAir, getAir;

    private SetBonus() {
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent e) {
        if (Implants.fullSet(e.getEntity())) {
            e.setDistance(Math.max(0F, e.getDistance() - 4F));
            e.setDamageMultiplier(e.getDamageMultiplier() * 0.4F);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.player.level().isClientSide) {
            return;
        }
        Player p = e.player;
        if (!Implants.fullSet(p)) {
            return;
        }
        if (p.tickCount % 10 == 0) {
            refillStamina(p);
        }
        aqualung(p);
    }

    // ---------------------------------------------------------------- Stamina (insane96)

    /** Підписка на StaminaEvent.Consumed (якщо мод є): у повному сеті витрата витривалості = 0. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void hookStamina() {
        try {
            Class<?> ev = Class.forName("insane96mcp.stamina.event.StaminaEvent$Consumed");
            Method setAmount = ev.getMethod("setAmount", float.class);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, (Class) ev, (java.util.function.Consumer) (Object o) -> {
                try {
                    Player p = ((net.minecraftforge.event.entity.player.PlayerEvent) o).getEntity();
                    if (Implants.fullSet(p)) {
                        setAmount.invoke(o, 0F);
                    }
                } catch (ReflectiveOperationException ignored) {
                    // інша версія
                }
            });
            Class<?> h = Class.forName("insane96mcp.stamina.feature.StaminaHandler");
            staminaUnlock = h.getMethod("unlockSprinting", Player.class);
            staminaSet = h.getMethod("setStamina", Player.class, float.class);
            staminaMax = h.getMethod("getMaxStamina", Player.class);
            CreateArmor.LOG.info("Steam armor: Stamina hooked");
        } catch (Throwable t) {
            // мода немає
        }
    }

    private static void refillStamina(Player p) {
        if (staminaSet == null) {
            return;
        }
        try {
            staminaUnlock.invoke(null, p);
            staminaSet.invoke(null, p, (float) staminaMax.invoke(null, p));
        } catch (ReflectiveOperationException ignored) {
            // ігноруємо
        }
    }

    // ---------------------------------------------------------------- акваланг

    private static void aqualung(Player p) {
        ItemStack tank = LiveImplant.get(p);
        if (tank.isEmpty() || !LiveImplant.isBacktank(tank)) {
            return;
        }
        boolean water = p.isEyeInFluid(FluidTags.WATER);
        boolean lava = p.isInLava() || p.isEyeInFluid(FluidTags.LAVA);
        if (!water && !lava) {
            return;
        }
        if (air(tank) <= 0) {
            return;
        }
        if (water) {
            p.setAirSupply(p.getMaxAirSupply());
        }
        if (lava) {
            var id = ForgeRegistries.ITEMS.getKey(tank.getItem());
            if (id == null || !id.getPath().contains("netherite")) {
                return;
            }
            p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, false, false, true));
        }
        if (p.tickCount % 20 == 0) {
            consume(p, tank);
        }
    }

    private static float air(ItemStack tank) {
        try {
            if (getAir == null) {
                getAir = Class.forName("com.simibubi.create.content.equipment.armor.BacktankUtil").getMethod("getAir", ItemStack.class);
            }
            return ((Number) getAir.invoke(null, tank)).floatValue();
        } catch (Throwable t) {
            return tank.getTag() == null ? 0 : tank.getTag().getFloat("Air");
        }
    }

    private static void consume(LivingEntity e, ItemStack tank) {
        try {
            if (consumeAir == null) {
                consumeAir = Class.forName("com.simibubi.create.content.equipment.armor.BacktankUtil")
                        .getMethod("consumeAir", LivingEntity.class, ItemStack.class, float.class);
            }
            consumeAir.invoke(null, e, tank, 1F);
        } catch (Throwable t) {
            if (tank.getTag() != null) {
                tank.getTag().putFloat("Air", Math.max(0, tank.getTag().getFloat("Air") - 1));
            }
        }
    }
}
