package com.createarmor;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Кругове меню вибору снаряда: поки тримаєш R. Відпустив — обрано той, на який дивиться курсор. */
public class AmmoScreen extends Screen {
    private final Cannon.Target target;
    private final List<ItemStack> types = new ArrayList<>();
    private final List<Integer> counts = new ArrayList<>();
    private int hovered = -1;

    public AmmoScreen(Cannon.Target target) {
        super(Component.translatable("screen.createarmor.ammo"));
        this.target = target;
        LocalPlayer p = Minecraft.getInstance().player;
        Map<Item, Integer> found = new LinkedHashMap<>();
        if (p != null) {
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                ItemStack s = p.getInventory().getItem(i);
                if (Cannon.isAmmo(s)) {
                    found.merge(s.getItem(), s.getCount(), Integer::sum);
                }
            }
        }
        for (Map.Entry<Item, Integer> e : found.entrySet()) {
            types.add(new ItemStack(e.getKey()));
            counts.add(e.getValue());
        }
        CannonState.menuOpen = true;
    }

    public static int count(LocalPlayer p, Item item) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.getItem() == item) {
                n += s.getCount();
            }
        }
        return n;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        long win = Minecraft.getInstance().getWindow().getWindow();
        int key = ArmorClient.CANNON_KEY.getKey().getValue();
        if (!InputConstants.isKeyDown(win, key)) {
            choose();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        choose();
        return true;
    }

    private void choose() {
        if (hovered >= 0 && hovered < types.size()) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(types.get(hovered).getItem());
            Net.CHANNEL.sendToServer(new Net.CannonAction((byte) 2, (byte) target.ordinal(), id == null ? "" : id.toString()));
        }
        onClose();
    }

    @Override
    public void removed() {
        CannonState.menuOpen = false;
        super.removed();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fillGradient(0, 0, width, height, 0x88100C08, 0xAA100C08);
        int cx = width / 2;
        int cy = height / 2;
        if (types.isEmpty()) {
            Component c = Component.translatable("screen.createarmor.no_ammo");
            g.drawString(font, c, cx - font.width(c) / 2, cy - 4, 0xFFD0A070, true);
            return;
        }
        int n = types.size();
        int radius = Math.max(40, 18 + n * 8);
        double dx = mx - cx;
        double dy = my - cy;
        hovered = -1;
        if (dx * dx + dy * dy > 14 * 14) {
            double ang = Math.atan2(dy, dx) + Math.PI / 2;
            if (ang < 0) {
                ang += Math.PI * 2;
            }
            hovered = (int) Math.round(ang / (Math.PI * 2) * n) % n;
        } else if (n == 1) {
            hovered = 0;
        }
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, 0xFFC8962E);
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n - Math.PI / 2;
            int x = cx + (int) Math.round(Math.cos(a) * radius);
            int y = cy + (int) Math.round(Math.sin(a) * radius);
            boolean h = i == hovered;
            g.fill(x - 13, y - 13, x + 13, y + 13, h ? 0xFFC8962E : 0xFF5C4A33);
            g.fill(x - 12, y - 12, x + 12, y + 12, 0xFF1C1712);
            g.renderItem(types.get(i), x - 8, y - 8);
            g.drawString(font, "x" + counts.get(i), x - 10, y + 15, 0xFFE8D8B0, true);
        }
        if (hovered >= 0) {
            Component name = types.get(hovered).getHoverName();
            g.drawString(font, name, cx - font.width(name) / 2, cy + radius + 30, 0xFFFFE0A0, true);
        }
        Component hint = Component.translatable("screen.createarmor.ammo_hint");
        g.drawString(font, hint, cx - font.width(hint) / 2, cy - radius - 30, 0xFFB8A888, true);
    }
}
