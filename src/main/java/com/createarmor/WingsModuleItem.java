package com.createarmor;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.UseOnContext;

/** Модуль парових крил (імплант). Заряджається так само: поставити блоком під вал, що крутиться. */
public class WingsModuleItem extends Item {
    public WingsModuleItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.RARE));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        return Steam.place(ctx);
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level level, java.util.List<net.minecraft.network.chat.Component> tip,
                                net.minecraft.world.item.TooltipFlag flag) {
        tip.add(net.minecraft.network.chat.Component.translatable("message.createarmor.charged", Steam.get(stack), Steam.MAX)
                .withStyle(net.minecraft.ChatFormatting.WHITE));
        tip.add(net.minecraft.network.chat.Component.translatable("tooltip.createarmor.wings_charge")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13F * Steam.get(stack) / Steam.MAX);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xE8E0D0;
    }
}
