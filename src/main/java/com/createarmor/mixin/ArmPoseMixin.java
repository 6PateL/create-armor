package com.createarmor.mixin;

import com.createarmor.CannonPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Поза правої руки з наручною гарматою: вперед після пострілу, зігнута до обличчя в меню вибору снаряда. */
@Mixin(value = HumanoidModel.class, remap = false)
public abstract class ArmPoseMixin {
    @Inject(method = "m_6973_(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"), remap = false, require = 0)
    private void createarmor$pose(LivingEntity e, float limb, float amount, float age, float yaw, float pitch, CallbackInfo ci) {
        CannonPose.apply((HumanoidModel<?>) (Object) this, e, yaw, pitch);
    }
}
