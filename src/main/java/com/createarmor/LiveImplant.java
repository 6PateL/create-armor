package com.createarmor;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * «Живий» імплант-нагрудник: один і той самий ItemStack, який інші моди бачать як нагрудник (через mixin у Octo Arms
 * та постачальника бектанків Create). Їхні зміни (повітря, міцність, налаштування) раз на кілька тіків записуються назад
 * у NBT парового нагрудника. Так гра сприймає слот імпланта як слот нагрудника.
 */
@Mod.EventBusSubscriber(modid = CreateArmor.MOD_ID)
public final class LiveImplant {
    private static final class Entry {
        ItemStack live = ItemStack.EMPTY;
        CompoundTag tag;
        ItemStack saved = ItemStack.EMPTY;
    }

    private static final Map<LivingEntity, Entry> SERVER = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<LivingEntity, Entry> CLIENT = Collections.synchronizedMap(new WeakHashMap<>());

    private LiveImplant() {
    }

    /** Імплант-нагрудник, що діє зараз, як живий стек; EMPTY, якщо його немає. */
    public static ItemStack get(LivingEntity e) {
        ItemStack chest = e.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() != CreateArmor.CHESTPLATE.get() || !Implants.fullSet(e) || chest.getTag() == null
                || !chest.getTag().contains(Implants.TAG)) {
            return ItemStack.EMPTY;
        }
        CompoundTag tag = chest.getTag().getCompound(Implants.TAG);
        Map<LivingEntity, Entry> map = e.level().isClientSide ? CLIENT : SERVER;
        Entry en = map.computeIfAbsent(e, k -> new Entry());
        if (en.tag == null || !en.tag.equals(tag)) {
            en.tag = tag.copy();
            en.live = ItemStack.of(tag);
            en.saved = en.live.copy();
        }
        return Implants.kindOf(en.live) == Implants.Kind.CHESTPLATE ? en.live : ItemStack.EMPTY;
    }

    /** Для mixin: що бачить чужий код, коли питає слот гравця. */
    public static ItemStack chest(LivingEntity e, EquipmentSlot slot) {
        ItemStack s = e.getItemBySlot(slot);
        if (slot != EquipmentSlot.CHEST || s.getItem() != CreateArmor.CHESTPLATE.get()) {
            return s;
        }
        ItemStack live = get(e);
        return live.isEmpty() ? s : live;
    }

    static boolean isBacktank(ItemStack s) {
        for (Class<?> c = s.getItem().getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            if (c.getSimpleName().equals("BacktankItem")) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer().getTickCount() % 5 != 0) {
            return;
        }
        List<Map.Entry<LivingEntity, Entry>> list;
        synchronized (SERVER) {
            list = new ArrayList<>(SERVER.entrySet());
        }
        for (Map.Entry<LivingEntity, Entry> me : list) {
            LivingEntity le = me.getKey();
            Entry en = me.getValue();
            if (le == null || le.isRemoved()) {
                SERVER.remove(le);
                continue;
            }
            ItemStack chest = le.getItemBySlot(EquipmentSlot.CHEST);
            if (chest.getItem() != CreateArmor.CHESTPLATE.get()) {
                SERVER.remove(le);
                continue;
            }
            if (!ItemStack.matches(en.live, en.saved)) {
                Implants.setImplant(chest, en.live);
                en.saved = en.live.copy();
                en.tag = chest.getTag() == null ? null : chest.getTag().getCompound(Implants.TAG).copy();
            }
        }
    }

    /** Імплант-нагрудник «тікає», як вдягнений: працюють його власні ефекти. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Player p = e.player;
        ItemStack live = get(p);
        if (!live.isEmpty()) {
            live.inventoryTick(p.level(), p, 38, false);
        }
    }

    /** Бектанк Create як імплант: Create бачить його повітря (шолом для дайвінгу, інструменти). */
    @SuppressWarnings("unchecked")
    static void registerBacktankSupplier() {
        try {
            Class<?> util = Class.forName("com.simibubi.create.content.equipment.armor.BacktankUtil");
            Function<LivingEntity, List<ItemStack>> f = le -> {
                ItemStack s = get(le);
                return !s.isEmpty() && isBacktank(s) ? List.of(s) : List.of();
            };
            util.getMethod("addBacktankSupplier", Function.class).invoke(null, f);
        } catch (Throwable t) {
            CreateArmor.LOG.info("Create backtank supplier not registered: {}", t.toString());
        }
    }
}
