package com.createarmor;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Сервер: жителі гравця з Train Recruits — рота забезпечення та розрахунки гармат — у вимірі плоті «вдягають» паровий
 * обладунок (лише вигляд). Звичайні жителі не під командуванням гравця сюди не потрапляють живими (Flesh Spread).
 */
@Mod.EventBusSubscriber(modid = CreateArmor.MOD_ID)
public final class SteamLookSync {
    private static final ResourceLocation FLESH = new ResourceLocation("flesh_spread", "flesh");

    private SteamLookSync() {
    }

    static boolean steamLook(Villager v) {
        if (!v.level().dimension().location().equals(FLESH)) {
            return false;
        }
        var d = v.getPersistentData();
        return d.getBoolean("trainrecruits_supply") || d.getBoolean("trainrecruits_gunner");
    }

    @SubscribeEvent
    public static void onTrack(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof Villager v && e.getEntity() instanceof ServerPlayer sp && steamLook(v)) {
            Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new Net.SteamLook(v.getId()));
        }
    }
}
