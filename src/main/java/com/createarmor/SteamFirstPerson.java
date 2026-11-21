package com.createarmor;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Перша особа: рука в паровому нарукавнику та жовте скло шолома з латунною оправою. */
@Mod.EventBusSubscriber(modid = CreateArmor.MOD_ID, value = Dist.CLIENT)
public final class SteamFirstPerson {
    private static final ResourceLocation ARMOR_TEX = new ResourceLocation(CreateArmor.MOD_ID, "textures/armor/steam_armor.png");
    private static final ResourceLocation VISOR = new ResourceLocation(CreateArmor.MOD_ID, "textures/misc/steam_visor.png");

    private SteamFirstPerson() {
    }

    @SubscribeEvent
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void onArm(RenderArmEvent e) {
        AbstractClientPlayer p = e.getPlayer();
        ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
        if (!(chest.getItem() instanceof SteamArmorItem)) {
            return;
        }
        if (!(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(p) instanceof PlayerRenderer pr)) {
            return;
        }
        e.setCanceled(true);
        boolean right = e.getArm() == HumanoidArm.RIGHT;
        // спершу звичайна рука гравця (як у ванілі), потім поверх — нарукавник
        if (right) {
            pr.renderRightHand(e.getPoseStack(), e.getMultiBufferSource(), e.getPackedLight(), p);
        } else {
            pr.renderLeftHand(e.getPoseStack(), e.getMultiBufferSource(), e.getPackedLight(), p);
        }
        PlayerModel<AbstractClientPlayer> model = pr.getModel();
        HumanoidModel<?> armor = IClientItemExtensions.of(chest).getHumanoidArmorModel(p, chest, EquipmentSlot.CHEST, model);
        if (!(armor instanceof ArmorClient.SteamArmorRenderer sr)) {
            return;
        }
        sr.onlyPart = right ? model.rightArm : model.leftArm;
        sr.onlyModel = model;
        try {
            // GeckoLib сам бере текстуру/буфер; переданий буфер лише для сигнатури
            sr.renderToBuffer(e.getPoseStack(), e.getMultiBufferSource().getBuffer(RenderType.armorCutoutNoCull(ARMOR_TEX)),
                    e.getPackedLight(), OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
        } finally {
            sr.onlyPart = null;
            sr.onlyModel = null;
        }
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Post e) {
        if (!e.getOverlay().id().equals(VanillaGuiOverlay.HELMET.id())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.options.getCameraType().isFirstPerson()
                || mc.player.getItemBySlot(EquipmentSlot.HEAD).getItem() != CreateArmor.HELMET.get()) {
            return;
        }
        GuiGraphics g = e.getGuiGraphics();
        int w = e.getWindow().getGuiScaledWidth();
        int h = e.getWindow().getGuiScaledHeight();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        g.blit(VISOR, 0, 0, w, h, 0F, 0F, 1024, 512, 1024, 512);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }
}
