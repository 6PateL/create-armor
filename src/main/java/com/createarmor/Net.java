package com.createarmor;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public final class Net {
    private static final String V = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CreateArmor.MOD_ID, "main"), () -> V, V::equals, V::equals);

    private Net() {
    }

    public static void register() {
        CHANNEL.registerMessage(0, OpenImplant.class, (m, b) -> { }, b -> new OpenImplant(), OpenImplant::handle);
        CHANNEL.registerMessage(1, Boost.class, Boost::encode, Boost::decode, Boost::handle);
        CHANNEL.registerMessage(2, CannonAction.class, CannonAction::encode, CannonAction::decode, CannonAction::handle);
        CHANNEL.registerMessage(3, CannonFx.class, CannonFx::encode, CannonFx::decode, CannonFx::handle);
    }

    /** Клавіша імпланта: відкрити слот (лише в повному сеті). */
    public record OpenImplant() {
        static void handle(OpenImplant m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p == null) {
                    return;
                }
                if (!Implants.fullSet(p) || p.getItemBySlot(EquipmentSlot.CHEST).getItem() != CreateArmor.CHESTPLATE.get()) {
                    p.displayClientMessage(Component.translatable("message.createarmor.need_set"), true);
                    return;
                }
                NetworkHooks.openScreen(p, new SimpleMenuProvider((id, inv, pl) -> new ImplantMenu(id, inv),
                        Component.translatable("container.createarmor.implant")));
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Паровий поштовх крил: сила 0..1 (як стрибок коня). */
    public record Boost(float charge) {
        static void encode(Boost m, FriendlyByteBuf b) {
            b.writeFloat(m.charge);
        }

        static Boost decode(FriendlyByteBuf b) {
            return new Boost(b.readFloat());
        }

        static void handle(Boost m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p == null || !p.isFallFlying() || !Steam.hasWings(p)) {
                    return;
                }
                float charge = Math.max(0F, Math.min(1F, m.charge));
                int have = Steam.energy(p);
                if (have < Steam.BOOST_COST * charge) {
                    charge = have / (float) Steam.BOOST_COST;
                }
                if (charge < 0.08F) {
                    p.level().playSound(null, p.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 1.6F);
                    p.displayClientMessage(Component.translatable("message.createarmor.no_steam"), true);
                    return;
                }
                int cost = Math.round(Steam.BOOST_COST * charge);
                Steam.modify(p, v -> v - cost);
                Vec3 look = p.getLookAngle();
                Vec3 v = p.getDeltaMovement().scale(0.5).add(look.scale(0.6 + 1.7 * charge));
                p.setDeltaMovement(v);
                p.hurtMarked = true;
                p.level().playSound(null, p.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0F, 0.6F);
                p.level().playSound(null, p.blockPosition(), SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 0.8F);
                if (p.level() instanceof ServerLevel sl) {
                    Vec3 back = p.position().add(0, 1, 0).subtract(look.scale(0.8));
                    sl.sendParticles(ParticleTypes.CLOUD, back.x, back.y, back.z, (int) (10 + 20 * charge), 0.25, 0.25, 0.25, 0.05);
                    sl.sendParticles(ParticleTypes.LARGE_SMOKE, back.x, back.y, back.z, 6, 0.2, 0.2, 0.2, 0.02);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Гармата: 0 — перезарядка, 1 — постріл (наручна), 2 — вибір снаряда. */
    public record CannonAction(byte action, byte target, String ammo) {
        static void encode(CannonAction m, FriendlyByteBuf b) {
            b.writeByte(m.action);
            b.writeByte(m.target);
            b.writeUtf(m.ammo, 256);
        }

        static CannonAction decode(FriendlyByteBuf b) {
            return new CannonAction(b.readByte(), b.readByte(), b.readUtf(256));
        }

        static void handle(CannonAction m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p == null || m.target < 0 || m.target > 1) {
                    return;
                }
                Cannon.Target t = Cannon.Target.values()[m.target];
                switch (m.action) {
                    case 0 -> Cannon.reload(p, t);
                    case 1 -> {
                        if (t == Cannon.Target.ARM && p.getMainHandItem().isEmpty()) {
                            Cannon.fire(p, t);
                        }
                    }
                    case 2 -> Cannon.select(p, t, m.ammo);
                    default -> {
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Ефект для клієнтів: 1 — постріл, 2 — почалась перезарядка (для анімацій руки й гармати). */
    public record CannonFx(int entity, byte kind) {
        static void encode(CannonFx m, FriendlyByteBuf b) {
            b.writeVarInt(m.entity);
            b.writeByte(m.kind);
        }

        static CannonFx decode(FriendlyByteBuf b) {
            return new CannonFx(b.readVarInt(), b.readByte());
        }

        static void handle(CannonFx m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                if (m.kind == 1) {
                    CannonState.FIRED.put(m.entity, CannonState.time);
                    CannonState.RELOAD_UNTIL.remove(m.entity);
                } else if (m.kind == 2) {
                    CannonState.RELOAD_UNTIL.put(m.entity, CannonState.time + Cannon.RELOAD_TICKS);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
