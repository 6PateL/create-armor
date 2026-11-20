package com.createarmor;

import com.google.common.collect.Multimap;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Бонуси повного сету для будь-якої істоти (гравці, рекрути): +4 шкоди, властивості імпланта-нагрудника, досяжність
 * extendo. Перераховуються, коли змінюється спорядження (зокрема NBT нагрудника після вставки імпланта).
 */
@Mod.EventBusSubscriber(modid = CreateArmor.MOD_ID)
public final class ArmorEvents {
    private static final UUID SET_DAMAGE = UUID.fromString("6b1f3c1e-6a43-4f1b-9d55-5e7f0c2a9a01");
    private static final UUID REACH_BLOCK = UUID.fromString("6b1f3c1e-6a43-4f1b-9d55-5e7f0c2a9a02");
    private static final UUID REACH_ENTITY = UUID.fromString("6b1f3c1e-6a43-4f1b-9d55-5e7f0c2a9a03");
    private static final String APPLIED = "createarmor_implant_attrs";
    public static final double EXTENDO_REACH = 3.0;

    private ArmorEvents() {
    }

    @SubscribeEvent
    public static void onEquip(LivingEquipmentChangeEvent e) {
        if (e.getSlot().getType() != EquipmentSlot.Type.ARMOR || e.getEntity().level().isClientSide) {
            return;
        }
        refresh(e.getEntity());
    }

    public static void refresh(LivingEntity le) {
        boolean full = Implants.fullSet(le);
        ItemStack imp = full ? Implants.implant(le.getItemBySlot(EquipmentSlot.CHEST)) : ItemStack.EMPTY;
        Implants.Kind kind = Implants.kindOf(imp);

        toggle(le, Attributes.ATTACK_DAMAGE, SET_DAMAGE, "Steam set damage", full ? 4.0 : 0);
        boolean extendo = kind == Implants.Kind.EXTENDO;
        toggle(le, ForgeMod.BLOCK_REACH.get(), REACH_BLOCK, "Steam extendo reach", extendo ? EXTENDO_REACH : 0);
        toggle(le, ForgeMod.ENTITY_REACH.get(), REACH_ENTITY, "Steam extendo reach", extendo ? EXTENDO_REACH : 0);

        // старі модифікатори імпланта-нагрудника
        ListTag old = le.getPersistentData().getList(APPLIED, 8);
        for (int i = 0; i < old.size(); i++) {
            String[] parts = old.getString(i).split("\\|");
            Attribute a = ForgeRegistries.ATTRIBUTES.getValue(ResourceLocation.tryParse(parts[0]));
            AttributeInstance inst = a == null ? null : le.getAttribute(a);
            if (inst != null) {
                inst.removeModifier(UUID.fromString(parts[1]));
            }
        }
        ListTag now = new ListTag();
        if (kind == Implants.Kind.CHESTPLATE) {
            Multimap<Attribute, AttributeModifier> mods = imp.getAttributeModifiers(EquipmentSlot.CHEST);
            Map<String, Double> sum = new HashMap<>();
            Map<String, Attribute> attrs = new HashMap<>();
            Map<String, AttributeModifier.Operation> ops = new HashMap<>();
            mods.forEach((a, m) -> {
                ResourceLocation id = ForgeRegistries.ATTRIBUTES.getKey(a);
                if (id == null) {
                    return;
                }
                String k = id + "#" + m.getOperation().toValue();
                sum.merge(k, m.getAmount(), Double::sum);
                attrs.put(k, a);
                ops.put(k, m.getOperation());
            });
            for (Map.Entry<String, Double> en : sum.entrySet()) {
                Attribute a = attrs.get(en.getKey());
                AttributeInstance inst = le.getAttribute(a);
                if (inst == null) {
                    continue;
                }
                UUID id = UUID.nameUUIDFromBytes(("createarmor:implant:" + en.getKey()).getBytes());
                inst.removeModifier(id);
                inst.addTransientModifier(new AttributeModifier(id, "Steam implant", en.getValue(), ops.get(en.getKey())));
                now.add(StringTag.valueOf(ForgeRegistries.ATTRIBUTES.getKey(a) + "|" + id));
            }
        }
        if (now.isEmpty()) {
            le.getPersistentData().remove(APPLIED);
        } else {
            le.getPersistentData().put(APPLIED, now);
        }
    }

    private static void toggle(LivingEntity le, Attribute a, UUID id, String name, double amount) {
        AttributeInstance inst = le.getAttribute(a);
        if (inst == null) {
            return;
        }
        AttributeModifier cur = inst.getModifier(id);
        if (amount == 0) {
            if (cur != null) {
                inst.removeModifier(id);
            }
        } else if (cur == null || cur.getAmount() != amount) {
            inst.removeModifier(id);
            inst.addTransientModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.ADDITION));
        }
    }

    /** Кінетична зарядка: Shift+ПКМ по бектанку Create (що крутиться від вала) переливає повітря в пару крил. */
    @SubscribeEvent
    public static void onClickBlock(PlayerInteractEvent.RightClickBlock e) {
        Player p = e.getEntity();
        if (!p.isShiftKeyDown()) {
            return;
        }
        BlockEntity be = e.getLevel().getBlockEntity(e.getPos());
        if (be == null || !be.getClass().getName().endsWith("BacktankBlockEntity") && !hasAir(be)) {
            return;
        }
        ItemStack hand = p.getItemInHand(e.getHand());
        boolean handWings = Implants.kindOf(hand) == Implants.Kind.ELYTRA;
        if (!handWings && !Steam.hasWings(p)) {
            return;
        }
        e.setCanceled(true);
        e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        if (e.getLevel().isClientSide) {
            return;
        }
        try {
            Method get = be.getClass().getMethod("getAirLevel");
            Method set = be.getClass().getMethod("setAirLevel", int.class);
            int air = (int) get.invoke(be);
            int have = handWings ? Steam.get(hand) : Steam.energy(p);
            int move = Math.min(air, Steam.MAX - have);
            if (move <= 0) {
                p.displayClientMessage(Component.translatable(air <= 0 ? "message.createarmor.tank_empty" : "message.createarmor.full",
                        have, Steam.MAX), true);
                return;
            }
            set.invoke(be, air - move);
            int result;
            if (handWings) {
                Steam.set(hand, have + move);
                result = Steam.get(hand);
            } else {
                result = Steam.modify(p, v -> v + move);
            }
            e.getLevel().playSound(null, e.getPos(), SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.7F, 1.4F);
            p.displayClientMessage(Component.translatable("message.createarmor.charged", result, Steam.MAX), true);
        } catch (ReflectiveOperationException ex) {
            CreateArmor.LOG.warn("Backtank charge failed", ex);
        }
    }

    private static boolean hasAir(BlockEntity be) {
        try {
            be.getClass().getMethod("getAirLevel");
            return true;
        } catch (NoSuchMethodException ex) {
            return false;
        }
    }
}
