package com.createarmor;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Клієнт: рендер броні (варіант моделі за імплантом), шар імпланта-нагрудника, клавіші, HUD пари, меню. */
public final class ArmorClient {
    public static final KeyMapping IMPLANT_KEY = new KeyMapping("key.createarmor.implant", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K, "key.categories.createarmor");

    private static float boostCharge;
    private static int heldAtMax;
    private static boolean wasJump;

    private ArmorClient() {
    }

    // ---------------- моделі ----------------

    /** Модель обладунку: geo/animations/textures беруться з варіанта, що відповідає імпланту носія. */
    static class SteamModel<T extends net.minecraft.world.item.Item & software.bernie.geckolib.animatable.GeoItem> extends GeoModel<T> {
        private final String fixed;
        private String variant = "steam_armor";

        SteamModel(String fixed) {
            this.fixed = fixed;
        }

        @Override
        public ResourceLocation getModelResource(T a, GeoRenderer<T> renderer) {
            if (fixed == null && renderer instanceof GeoArmorRenderer<?> r) {
                variant = Implants.variant(r.getCurrentEntity());
            }
            return getModelResource(a);
        }

        private String name() {
            return fixed != null ? fixed : variant;
        }

        @Override
        public ResourceLocation getModelResource(T a) {
            return new ResourceLocation(CreateArmor.MOD_ID, "geo/armor/" + name() + ".geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(T a) {
            return new ResourceLocation(CreateArmor.MOD_ID, "textures/armor/" + name() + ".png");
        }

        @Override
        public ResourceLocation getAnimationResource(T a) {
            return new ResourceLocation(CreateArmor.MOD_ID, "animations/armor/" + name() + ".animation.json");
        }
    }

    static class SteamArmorRenderer<T extends net.minecraft.world.item.Item & software.bernie.geckolib.animatable.GeoItem> extends GeoArmorRenderer<T> {
        SteamArmorRenderer(String fixed) {
            super(new SteamModel<>(fixed));
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }

        @Override
        public void preRender(PoseStack pose, T animatable, BakedGeoModel model, MultiBufferSource buffers, VertexConsumer buffer,
                              boolean reRender, float partialTick, int light, int overlay, float r, float g, float b, float a) {
            super.preRender(pose, animatable, model, buffers, buffer, reRender, partialTick, light, overlay, r, g, b, a);
            if (getCurrentEntity() instanceof LivingEntity le) {
                float fill = Steam.energy(le) / (float) Steam.MAX;
                model.getBone("charge_bar").ifPresent(bone -> bone.setScaleX(Math.max(0.02F, fill)));
            }
        }
    }

    public static Object armor() {
        return new SteamArmorRenderer<SteamArmorItem>(null);
    }

    public static Object elytra() {
        return new SteamArmorRenderer<SteamElytraItem>("steam_elytra");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static HumanoidModel<?> prep(Object renderer, LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
        GeoArmorRenderer r = (GeoArmorRenderer) renderer;
        r.prepForRender(entity, stack, slot, original);
        return r;
    }

    public static BlockEntityWithoutLevelRenderer cannon() {
        GeoItemRenderer<SteamCannonItem> r = new GeoItemRenderer<>(
                new DefaultedItemGeoModel<>(new ResourceLocation(CreateArmor.MOD_ID, "steam_cannon")));
        r.addRenderLayer(new AutoGlowingGeoLayer<>(r));
        return r;
    }

    // ---------------- шар імпланта-нагрудника ----------------

    /** Малює вставлений чужий нагрудник поверх парового (трохи більшим), щоб було видно, що в слоті. */
    static class ImplantLayer<E extends LivingEntity, M extends HumanoidModel<E>> extends RenderLayer<E, M> {
        private final HumanoidModel<LivingEntity> base;

        ImplantLayer(RenderLayerParent<E, M> parent, HumanoidModel<LivingEntity> base) {
            super(parent);
            this.base = base;
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, E e, float limbSwing, float limbAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            ItemStack imp = Implants.active(e);
            if (Implants.kindOf(imp) != Implants.Kind.CHESTPLATE) {
                return;
            }
            getParentModel().copyPropertiesTo(base);
            base.setAllVisible(false);
            base.body.visible = true;
            base.rightArm.visible = true;
            base.leftArm.visible = true;
            Model m = ForgeHooksClient.getArmorModel(e, imp, EquipmentSlot.CHEST, base);
            pose.pushPose();
            pose.scale(1.12F, 1.06F, 1.18F);
            if (m instanceof GeoArmorRenderer<?>) {
                m.renderToBuffer(pose, buffers.getBuffer(RenderType.armorCutoutNoCull(texture(e, imp))), light,
                        OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
            } else {
                VertexConsumer vc = ItemRendererGlint.armor(buffers, texture(e, imp), imp.hasFoil());
                m.renderToBuffer(pose, vc, light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
            }
            pose.popPose();
        }

        private static ResourceLocation texture(Entity e, ItemStack s) {
            String mat = s.getItem() instanceof ArmorItem ai ? ai.getMaterial().getName() : "iron";
            String dom = "minecraft";
            int c = mat.indexOf(':');
            if (c >= 0) {
                dom = mat.substring(0, c);
                mat = mat.substring(c + 1);
            }
            String def = String.format(java.util.Locale.ROOT, "%s:textures/models/armor/%s_layer_1.png", dom, mat);
            return new ResourceLocation(ForgeHooksClient.getArmorTexture(e, s, def, EquipmentSlot.CHEST, null));
        }
    }

    static final class ItemRendererGlint {
        static VertexConsumer armor(MultiBufferSource buffers, ResourceLocation tex, boolean foil) {
            return net.minecraft.client.renderer.entity.ItemRenderer.getArmorFoilBuffer(buffers, RenderType.armorCutoutNoCull(tex), false, foil);
        }
    }

    // ---------------- екран імпланта ----------------

    static class ImplantScreen extends AbstractContainerScreen<ImplantMenu> {
        ImplantScreen(ImplantMenu menu, Inventory inv, Component title) {
            super(menu, inv, title);
        }

        @Override
        protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
            int x = leftPos;
            int y = topPos;
            g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF2B241C);
            g.fill(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, 0xFF4A3B2A);
            g.fill(x + 4, y + 4, x + imageWidth - 4, y + 72, 0xFF5C4A33);
            for (Slot s : menu.slots) {
                int c = menu.slots.indexOf(s) == 0 ? 0xFFC8962E : 0xFF8B7355;
                g.fill(x + s.x - 2, y + s.y - 2, x + s.x + 18, y + s.y + 18, c);
                g.fill(x + s.x - 1, y + s.y - 1, x + s.x + 17, y + s.y + 17, 0xFF1C1712);
            }
        }

        @Override
        protected void renderLabels(GuiGraphics g, int mx, int my) {
            g.drawString(font, title, 8, 6, 0xFFE8C77A, false);
            g.drawString(font, playerInventoryTitle, 8, imageHeight - 94, 0xFFD8C8A8, false);
            ItemStack in = menu.slots.get(0).getItem();
            Component kind = Component.translatable("implant.createarmor." + Implants.kindOf(in).name().toLowerCase(java.util.Locale.ROOT));
            g.drawString(font, kind, (imageWidth - font.width(kind)) / 2, 58, 0xFFB8A888, false);
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float partial) {
            renderBackground(g);
            super.render(g, mx, my, partial);
            renderTooltip(g, mx, my);
        }
    }

    // ---------------- реєстрація (mod bus) ----------------

    @Mod.EventBusSubscriber(modid = CreateArmor.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void setup(FMLClientSetupEvent e) {
            e.enqueueWork(() -> MenuScreens.register(CreateArmor.IMPLANT_MENU.get(), ImplantScreen::new));
        }

        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) {
            e.register(IMPLANT_KEY);
        }

        @SubscribeEvent
        @SuppressWarnings({"unchecked", "rawtypes"})
        public static void layers(EntityRenderersEvent.AddLayers e) {
            for (String skin : e.getSkins()) {
                LivingEntityRenderer r = e.getSkin(skin);
                add(r, e);
            }
            for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES.getValues()) {
                EntityRenderer<?> r;
                try {
                    r = e.getRenderer((EntityType) type);
                } catch (Exception ex) {
                    continue;
                }
                if (r instanceof LivingEntityRenderer<?, ?> lr && lr.getModel() instanceof HumanoidModel<?>) {
                    add(lr, e);
                }
            }
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static void add(LivingEntityRenderer r, EntityRenderersEvent.AddLayers e) {
            if (r == null || !(r.getModel() instanceof HumanoidModel)) {
                return;
            }
            HumanoidModel<LivingEntity> base = new HumanoidModel<>(e.getEntityModels().bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
            r.addLayer(new ImplantLayer(r, base));
        }
    }

    // ---------------- тік і HUD (forge bus) ----------------

    @Mod.EventBusSubscriber(modid = CreateArmor.MOD_ID, value = Dist.CLIENT)
    public static final class ForgeBus {
        private ForgeBus() {
        }

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer p = mc.player;
            if (p == null) {
                return;
            }
            while (IMPLANT_KEY.consumeClick()) {
                if (mc.screen == null) {
                    Net.CHANNEL.sendToServer(new Net.OpenImplant());
                }
            }
            boolean jump = mc.options.keyJump.isDown() && mc.screen == null;
            if (p.isFallFlying() && Steam.hasWings(p)) {
                if (jump) {
                    // як стрибок коня: смуга росте, тримай на максимумі недовго, інакше спадає
                    if (boostCharge < 1F && heldAtMax == 0) {
                        boostCharge = Math.min(1F, boostCharge + 0.06F);
                    } else {
                        heldAtMax++;
                        if (heldAtMax > 10) {
                            boostCharge = Math.max(0.4F, boostCharge - 0.05F);
                        }
                    }
                } else if (wasJump && boostCharge > 0.05F) {
                    Net.CHANNEL.sendToServer(new Net.Boost(boostCharge));
                    boostCharge = 0;
                    heldAtMax = 0;
                }
            } else {
                boostCharge = 0;
                heldAtMax = 0;
            }
            if (!jump && !wasJump) {
                heldAtMax = 0;
            }
            wasJump = jump;
        }

        @SubscribeEvent
        public static void hud(RenderGuiEvent.Post e) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer p = mc.player;
            if (p == null || mc.options.hideGui || !Steam.hasWings(p)) {
                return;
            }
            GuiGraphics g = e.getGuiGraphics();
            int w = e.getWindow().getGuiScaledWidth();
            int h = e.getWindow().getGuiScaledHeight();
            // запас пари — вузька смужка праворуч від хотбара
            float fill = Steam.energy(p) / (float) Steam.MAX;
            int x = w / 2 + 96;
            int y = h - 22;
            g.fill(x, y, x + 6, y + 20, 0xAA1C1712);
            int fh = Math.round(18 * fill);
            g.fill(x + 1, y + 19 - fh, x + 5, y + 19, fill < 0.25F ? 0xFFD0603A : 0xFFE8E0D0);
            // смуга поштовху на місці смуги досвіду
            if (boostCharge > 0) {
                int bx = w / 2 - 91;
                int by = h - 29;
                g.fill(bx, by, bx + 182, by + 5, 0xCC1C1712);
                int bw = Math.round(180 * boostCharge);
                int col = boostCharge >= 1F ? 0xFFFFE070 : 0xFFC8962E;
                g.fill(bx + 1, by + 1, bx + 1 + bw, by + 4, col);
                int cost = Math.round(Steam.BOOST_COST * boostCharge);
                if (cost > Steam.energy(p)) {
                    int ok = Math.round(180 * Steam.energy(p) / (float) Steam.BOOST_COST);
                    g.fill(bx + 1 + Math.min(ok, bw), by + 1, bx + 1 + bw, by + 4, 0xFF7A3A2A);
                }
            }
        }
    }
}
