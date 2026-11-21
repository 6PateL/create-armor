package com.createarmor;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Рекрути (і будь-які моби) з паровою гарматою в руці вміють нею стріляти: бачать ціль на 6–56 блоках — постріл
 * шрапнеллю, далі 7 с перезарядки (як у гравця). Зовсім близько не стріляють — щоб не зачепити себе.
 */
@Mod.EventBusSubscriber(modid = CreateArmor.MOD_ID)
public final class MobCannon {
    private static final String NEXT = "createarmor_cannon_next";
    private static final String AIM = "createarmor_cannon_aim";

    private MobCannon() {
    }

    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent e) {
        if (!(e.getEntity() instanceof Mob m) || m instanceof Player || m.level().isClientSide
                || !(m.getMainHandItem().getItem() instanceof SteamCannonItem)) {
            return;
        }
        LivingEntity t = m.getTarget();
        var d = m.getPersistentData();
        long now = m.level().getGameTime();
        if (t == null || !t.isAlive()) {
            d.remove(AIM);
            return;
        }
        double dist = m.distanceTo(t);
        if (dist < 6.0 || dist > 56.0 || !m.hasLineOfSight(t) || now < d.getLong(NEXT)) {
            d.remove(AIM);
            return;
        }
        m.getLookControl().setLookAt(t, 30F, 30F);
        // пів секунди прицілюється, потім стріляє
        int aim = d.getInt(AIM) + 1;
        d.putInt(AIM, aim);
        if (aim < 10) {
            return;
        }
        d.remove(AIM);
        if (Cannon.fireFromMob(m, t)) {
            d.putLong(NEXT, now + Cannon.RELOAD_TICKS + m.getRandom().nextInt(30));
            Net.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(() -> m), new Net.CannonFx(m.getId(), (byte) 2));
        } else {
            d.putLong(NEXT, now + 200);
        }
    }
}
