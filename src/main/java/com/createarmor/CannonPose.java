package com.createarmor;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;

/** Поза правої руки для наручної гармати (викликається з mixin у HumanoidModel.setupAnim, лише на клієнті). */
public final class CannonPose {
    private CannonPose() {
    }

    public static void apply(HumanoidModel<?> m, LivingEntity e, float yaw, float pitch) {
        if (e == null || Implants.kind(e) != Implants.Kind.CANNON) {
            return;
        }
        // рука — це гармата: власну руку гравця ховаємо, рух дає анімація броні (cannon_arm), як у прев'ю
        m.rightArm.visible = false;
        if (m instanceof net.minecraft.client.model.PlayerModel<?> pm) {
            pm.rightSleeve.visible = false;
        }
        int id = e.getId();
        if (id == CannonState.localId && CannonState.menuOpen) {
            // меню снарядів: гармату підтягнуто до грудей
            m.rightArm.xRot = 0F;
            m.rightArm.yRot = -0.7F;
            m.rightArm.zRot = 0.15F;
        } else if (CannonState.aiming(id) || CannonState.fireAge(id) < 14) {
            // анімація вже піднімає руку горизонтально — додаємо лише нахил і поворот погляду
            m.rightArm.xRot = m.head.xRot;
            m.rightArm.yRot = m.head.yRot;
            m.rightArm.zRot = 0F;
        }
    }
}
