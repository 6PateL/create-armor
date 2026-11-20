package com.createarmor;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.animatable.GeoReplacedEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Рота забезпечення (Train Recruits, жителі з позначкою «[+]») у вимірі плоті виглядає як жителі в паровому обладунку.
 * Це лише вигляд: захист від зараження дає сам вимір плоті (Flesh Spread пропускає роту).
 */
@Mod.EventBusSubscriber(modid = CreateArmor.MOD_ID, value = Dist.CLIENT)
public final class SteamVillagerClient {
    private static final ResourceLocation FLESH = new ResourceLocation("flesh_spread", "flesh");
    private static GeoReplacedEntityRenderer<Villager, Anim> renderer;

    private SteamVillagerClient() {
    }

    static final class Anim implements GeoReplacedEntity {
        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

        @Override
        public EntityType<?> getReplacingEntityType() {
            return EntityType.VILLAGER;
        }

        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
            controllers.add(new AnimationController<>(this, "main", 5, st -> st.setAndContinue(st.isMoving()
                    ? RawAnimation.begin().thenLoop("animation.steam_villager.walk")
                    : RawAnimation.begin().thenLoop("animation.steam_villager.idle"))));
        }

        @Override
        public AnimatableInstanceCache getAnimatableInstanceCache() {
            return cache;
        }
    }

    static void init(EntityRendererProvider.Context ctx) {
        renderer = new GeoReplacedEntityRenderer<>(ctx,
                new DefaultedEntityGeoModel<>(new ResourceLocation(CreateArmor.MOD_ID, "steam_villager"), "head"), new Anim());
        renderer.addRenderLayer(new AutoGlowingGeoLayer<>(renderer));
    }

    public static boolean isSupply(Villager v) {
        if (!v.level().dimension().location().equals(FLESH)) {
            return false;
        }
        Component n = v.getCustomName();
        return n != null && n.getString().startsWith("[+]");
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRender(RenderLivingEvent.Pre<?, ?> e) {
        if (renderer == null || !(e.getEntity() instanceof Villager v) || !isSupply(v)) {
            return;
        }
        e.setCanceled(true);
        float yaw = Mth.lerp(e.getPartialTick(), v.yRotO, v.getYRot());
        renderer.render(v, yaw, e.getPartialTick(), e.getPoseStack(), e.getMultiBufferSource(), e.getPackedLight());
    }
}
