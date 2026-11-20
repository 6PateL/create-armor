package com.createarmor;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Меню одного слота імпланта. Вміст живе в NBT нагрудника, тож меню лише показує/записує його. */
public class ImplantMenu extends AbstractContainerMenu {
    private final SimpleContainer box = new SimpleContainer(1);
    private final Player player;

    public ImplantMenu(int id, Inventory inv) {
        super(CreateArmor.IMPLANT_MENU.get(), id);
        this.player = inv.player;
        if (!player.level().isClientSide) {
            box.setItem(0, Implants.implant(player.getItemBySlot(EquipmentSlot.CHEST)));
            box.addListener(c -> save());
        }
        addSlot(new Slot(box, 0, 80, 35) {
            @Override
            public boolean mayPlace(ItemStack s) {
                return Implants.allowed(s);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 84 + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            addSlot(new Slot(inv, c, 8 + c * 18, 142));
        }
    }

    private void save() {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() == CreateArmor.CHESTPLATE.get()) {
            Implants.setImplant(chest, box.getItem(0).copy());
        }
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack s = slot.getItem();
        ItemStack copy = s.copy();
        if (index == 0) {
            if (!moveItemStackTo(s, 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!Implants.allowed(s) || slots.get(0).hasItem()) {
                return ItemStack.EMPTY;
            }
            ItemStack one = s.split(1);
            slots.get(0).set(one);
        }
        if (s.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player p) {
        return Implants.fullSet(p) && p.getItemBySlot(EquipmentSlot.CHEST).getItem() == CreateArmor.CHESTPLATE.get();
    }
}
