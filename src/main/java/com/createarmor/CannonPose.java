package com.createarmor;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;

/** Поза правої руки для наручної гармати (викликається з mixin у HumanoidModel.setupAnim, лише на клієнті). */
public final class CannonPose {
    private CannonPose() {
    }

    public static void apply(HumanoidModel<?> m, LivingEntity e, float yaw, float pitch) {
        if (e == null || Implants.kind(e) != Implants.Kind.CANNON || !e.getMainHandItem().isEmpty()) {
            return;
        }
        int id = e.getId();
        if (id == CannonState.localId && CannonState.menuOpen) {
            // зігнута рука: гармата перед обличчям, ніби розглядаєш набої
            m.rightArm.xRot = -1.75F;
            m.rightArm.yRot = -0.75F;
            m.rightArm.zRot = 0.35F;
        } else if (CannonState.fireAge(id) < 25 || CannonState.reloading(id) && CannonState.fireAge(id) < 40) {
            // рука вперед, по погляду
            m.rightArm.xRot = m.head.xRot - (float) Math.PI / 2F;
            m.rightArm.yRot = m.head.yRot - 0.1F;
            m.rightArm.zRot = 0F;
        }
    }
}
