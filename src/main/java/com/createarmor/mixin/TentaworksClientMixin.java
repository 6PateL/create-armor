package com.createarmor.mixin;

import com.createarmor.LiveImplant;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Рендер щупалець Octo Arms і їхній екран налаштувань теж бачать імплант як нагрудник. */
@Pseudo
@Mixin(targets = {"dev.tentaworks.client.TentacleLayer", "dev.tentaworks.client.RigSettingsScreen"}, remap = false)
public abstract class TentaworksClientMixin {
    // TentacleLayer.render — метод екземпляра, тож обробник теж не static
    @Redirect(method = "*", require = 0, remap = false, at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/client/player/AbstractClientPlayer;m_6844_(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack createarmor$abstract(AbstractClientPlayer p, EquipmentSlot slot) {
        return LiveImplant.chest(p, slot);
    }

    @Redirect(method = "*", require = 0, remap = false, at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/client/player/LocalPlayer;m_6844_(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack createarmor$local(LocalPlayer p, EquipmentSlot slot) {
        return LiveImplant.chest(p, slot);
    }
}
