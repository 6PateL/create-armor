package com.createarmor.mixin;

import com.createarmor.LiveImplant;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Octo Arms питає «що в слоті нагрудника» — віддаємо йому імплант парового нагрудника. */
@Pseudo
@Mixin(targets = {"dev.tentaworks.rig.RigAir", "dev.tentaworks.rig.TentacleRigItem", "dev.tentaworks.network.RigSettingsPacket"}, remap = false)
public abstract class TentaworksCommonMixin {
    @Redirect(method = "*", require = 0, remap = false, at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/world/entity/player/Player;m_6844_(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack createarmor$player(Player p, EquipmentSlot slot) {
        return LiveImplant.chest(p, slot);
    }

    @Redirect(method = "*", require = 0, remap = false, at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/server/level/ServerPlayer;m_6844_(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack createarmor$server(ServerPlayer p, EquipmentSlot slot) {
        return LiveImplant.chest(p, slot);
    }
}
