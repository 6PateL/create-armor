package com.createarmor;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;

/**
 * Ручна й наручна парові гармати: 1 снаряд Create Big Cannons у «магазині», перезарядка 7 с (R), утримання R — вибір типу
 * снаряда. Снаряд справжній (CBC сам рахує фізику, вибухи, звуки); запал удару ставиться, якщо на снаряді його немає.
 */
public final class Cannon {
    public static final int RELOAD_TICKS = 140;
    public static final float SPEED_HAND = 4.0F;
    public static final float SPEED_ARM = 3.5F;

    public enum Target { HAND, ARM }

    private Cannon() {
    }

    public static ItemStack read(Player p, Target t) {
        if (t == Target.HAND) {
            ItemStack s = p.getMainHandItem();
            return s.getItem() instanceof SteamCannonItem ? s : ItemStack.EMPTY;
        }
        return Implants.kind(p) == Implants.Kind.CANNON ? Implants.active(p) : ItemStack.EMPTY;
    }

    private static void write(Player p, Target t, ItemStack s) {
        if (t == Target.ARM) {
            Implants.setImplant(p.getItemBySlot(EquipmentSlot.CHEST), s);
        }
    }

    public static Target target(Player p) {
        if (p.getMainHandItem().getItem() instanceof SteamCannonItem) {
            return Target.HAND;
        }
        return Implants.kind(p) == Implants.Kind.CANNON ? Target.ARM : null;
    }

    public static boolean isAmmo(ItemStack s) {
        if (!(s.getItem() instanceof BlockItem bi)) {
            return false;
        }
        for (Class<?> c = bi.getBlock().getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            if (c.getName().equals("rbasamoyai.createbigcannons.munitions.big_cannon.ProjectileBlock")) {
                return true;
            }
        }
        return false;
    }

    public static ResourceLocation selected(ItemStack c) {
        return c.getTag() == null || !c.getTag().contains("Ammo") ? null : ResourceLocation.tryParse(c.getTag().getString("Ammo"));
    }

    public static boolean hasRound(ItemStack c) {
        return c.getTag() != null && c.getTag().contains("Round");
    }

    public static long reloadEnd(ItemStack c) {
        return c.getTag() == null ? 0 : c.getTag().getLong("ReloadEnd");
    }

    public static boolean ready(ItemStack c, long now) {
        return hasRound(c) && now >= reloadEnd(c);
    }

    public static ItemStack round(ItemStack c) {
        return hasRound(c) ? ItemStack.of(c.getTag().getCompound("Round")) : ItemStack.EMPTY;
    }

    public static void select(ServerPlayer p, Target t, String id) {
        ItemStack c = read(p, t);
        if (c.isEmpty()) {
            return;
        }
        if (id.isEmpty()) {
            if (c.getTag() != null) {
                c.getTag().remove("Ammo");
            }
        } else {
            c.getOrCreateTag().putString("Ammo", id);
        }
        write(p, t, c);
        Item it = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(id));
        p.displayClientMessage(Component.translatable("message.createarmor.ammo_selected",
                it == null ? Component.literal(id) : new ItemStack(it).getHoverName()).withStyle(ChatFormatting.GOLD), true);
    }

    public static void reload(ServerPlayer p, Target t) {
        ItemStack c = read(p, t);
        if (c.isEmpty()) {
            return;
        }
        long now = p.level().getGameTime();
        if (hasRound(c)) {
            p.displayClientMessage(Component.translatable(now >= reloadEnd(c) ? "message.createarmor.loaded" : "message.createarmor.reloading"), true);
            return;
        }
        ResourceLocation sel = selected(c);
        ItemStack found = ItemStack.EMPTY;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (isAmmo(s) && (sel == null || sel.equals(ForgeRegistries.ITEMS.getKey(s.getItem())))) {
                found = s;
                break;
            }
        }
        if (found.isEmpty()) {
            p.displayClientMessage(Component.translatable("message.createarmor.no_ammo"), true);
            p.level().playSound(null, p.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.7F, 1.2F);
            return;
        }
        ItemStack one = found.copyWithCount(1);
        if (!p.getAbilities().instabuild) {
            found.shrink(1);
        }
        c.getOrCreateTag().put("Round", one.save(new CompoundTag()));
        c.getTag().putLong("ReloadEnd", now + RELOAD_TICKS);
        write(p, t, c);
        p.level().playSound(null, p.blockPosition(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.PLAYERS, 1.0F, 0.6F);
        p.level().playSound(null, p.blockPosition(), SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.3F);
        Net.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new Net.CannonFx(p.getId(), (byte) 2));
    }

    public static void fire(ServerPlayer p, Target t) {
        ItemStack c = read(p, t);
        if (c.isEmpty()) {
            return;
        }
        Level level = p.level();
        long now = level.getGameTime();
        if (!ready(c, now)) {
            level.playSound(null, p.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6F, 1.5F);
            p.displayClientMessage(Component.translatable(hasRound(c) ? "message.createarmor.reloading" : "message.createarmor.empty"), true);
            return;
        }
        ItemStack round = round(c);
        c.getTag().remove("Round");
        c.getTag().remove("ReloadEnd");
        write(p, t, c);

        Vec3 look = p.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 muzzle = p.getEyePosition().add(look.scale(1.1)).add(right.scale(t == Target.ARM ? 0.4 : 0.25)).add(0, -0.25, 0);
        float speed = t == Target.HAND ? SPEED_HAND : SPEED_ARM;
        Entity shell = spawnShell(level, round);
        if (shell == null) {
            p.displayClientMessage(Component.translatable("message.createarmor.bad_ammo"), true);
            if (!p.getAbilities().instabuild) {
                p.getInventory().placeItemBackInInventory(round);
            }
            return;
        }
        call(shell, "setChargePower", new Class<?>[]{float.class}, speed);
        shell.setPos(muzzle);
        if (shell instanceof Projectile pr) {
            pr.setOwner(p);
            pr.shoot(look.x, look.y, look.z, speed, 0.6F);
        } else {
            shell.setDeltaMovement(look.scale(speed));
        }
        call(shell, "addUntouchableEntity", new Class<?>[]{Entity.class, int.class}, p, 2);
        level.addFreshEntity(shell);

        SoundEvent boom = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("createbigcannons", "fire_big_cannon"));
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, boom != null ? boom : SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3.0F, 1.25F);
        level.playSound(null, p.blockPosition(), SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 0.7F);
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 14, 0.2, 0.2, 0.2, 0.04);
            sl.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 8, 0.1, 0.1, 0.1, 0.06);
            sl.sendParticles(ParticleTypes.POOF, muzzle.x, muzzle.y, muzzle.z, 10, 0.15, 0.15, 0.15, 0.08);
            Vec3 vent = p.position().add(0, 1.3, 0).add(right.scale(0.4));
            sl.sendParticles(ParticleTypes.CLOUD, vent.x, vent.y, vent.z, 8, 0.1, 0.2, 0.1, 0.03);
        }
        Vec3 kick = look.scale(t == Target.HAND ? -0.7 : -0.5);
        p.push(kick.x, kick.y * 0.5, kick.z);
        p.hurtMarked = true;
        Net.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new Net.CannonFx(p.getId(), (byte) 1));
        if (t == Target.HAND) {
            p.swing(InteractionHand.MAIN_HAND, true);
        }
    }

    private static Entity spawnShell(Level level, ItemStack round) {
        if (!(round.getItem() instanceof BlockItem bi)) {
            return null;
        }
        try {
            Method m = bi.getBlock().getClass().getMethod("getProjectile", Level.class, ItemStack.class);
            Object o = m.invoke(bi.getBlock(), level, round);
            if (!(o instanceof Entity e)) {
                return null;
            }
            boolean fuzed = false;
            for (Class<?> c = e.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                if (c.getSimpleName().equals("FuzedBigCannonProjectile")) {
                    fuzed = true;
                    break;
                }
            }
            String tag = round.getTag() == null ? "" : round.getTag().toString().toLowerCase(java.util.Locale.ROOT);
            if (fuzed && !tag.contains("fuze")) {
                Item fuze = ForgeRegistries.ITEMS.getValue(new ResourceLocation("createbigcannons", "impact_fuze"));
                if (fuze != null) {
                    call(e, "setFuze", new Class<?>[]{ItemStack.class}, new ItemStack(fuze));
                }
            }
            return e;
        } catch (Throwable t) {
            CreateArmor.LOG.warn("CBC shell spawn failed: {}", t.toString());
            return null;
        }
    }

    private static void call(Object o, String name, Class<?>[] types, Object... args) {
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name, types);
                m.setAccessible(true);
                m.invoke(o, args);
                return;
            } catch (NoSuchMethodException ignored) {
                // шукаємо вище
            } catch (Throwable t) {
                return;
            }
        }
    }
}
