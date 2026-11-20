package com.createarmor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Поставлені крила: та сама модель steam_elytra, опущена на землю; смуга заряду показує пару. */
public class WingsRenderer extends GeoBlockRenderer<SteamWingsBlockEntity> {
    public WingsRenderer() {
        super(new GeoModel<>() {
            @Override
            public ResourceLocation getModelResource(SteamWingsBlockEntity a) {
                return new ResourceLocation(CreateArmor.MOD_ID, "geo/armor/steam_elytra.geo.json");
            }

            @Override
            public ResourceLocation getTextureResource(SteamWingsBlockEntity a) {
                return new ResourceLocation(CreateArmor.MOD_ID, "textures/armor/steam_elytra.png");
            }

            @Override
            public ResourceLocation getAnimationResource(SteamWingsBlockEntity a) {
                return new ResourceLocation(CreateArmor.MOD_ID, "animations/armor/steam_elytra.animation.json");
            }
        });
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void preRender(PoseStack pose, SteamWingsBlockEntity be, BakedGeoModel model, MultiBufferSource buffers, VertexConsumer buffer,
                          boolean reRender, float partialTick, int light, int overlay, float r, float g, float b, float a) {
        super.preRender(pose, be, model, buffers, buffer, reRender, partialTick, light, overlay, r, g, b, a);
        if (!reRender) {
            pose.translate(0, -7.5 / 16.0, -3.8 / 16.0);
        }
        float fill = be.energy() / (float) Steam.MAX;
        model.getBone("charge_bar").ifPresent(bone -> bone.setScaleX(Math.max(0.02F, fill)));
    }
}
