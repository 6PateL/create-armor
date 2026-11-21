package com.createarmor.mixin;

import com.createarmor.LiveImplant;
import com.simibubi.create.content.equipment.armor.BacktankItem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Create питає «який бектанк вдягнено» (рендер на спині, HUD повітря) — бектанк в імпланті теж рахується. */
@Mixin(value = BacktankItem.class, remap = false)
public abstract class BacktankWornMixin {
    @Inject(method = "getWornBy", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void createarmor$worn(Entity entity, CallbackInfoReturnable<BacktankItem> cir) {
        if (entity instanceof LivingEntity le) {
            ItemStack s = LiveImplant.get(le);
            if (!s.isEmpty() && s.getItem() instanceof BacktankItem b) {
                cir.setReturnValue(b);
            }
        }
    }
}
