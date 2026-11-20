package com.createarmor;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Запас пари парових крил (NBT "Steam" на самих крилах — окремих чи вставлених імплантом). Поповнюється кінетично:
 * крила ставляться блоком, і вал Create, що крутиться над ними (або поруч), набирає пару (як риг Octo Arms).
 * Витрачає пару лише поштовх: 3 повні поштовхи на повний запас.
 */
public final class Steam {
    public static final int MAX = 1500;
    public static final int BOOST_COST = 500;
    private static final String TAG = "Steam";

    private Steam() {
    }

    public static int get(ItemStack s) {
        return s.getTag() == null ? 0 : s.getTag().getInt(TAG);
    }

    public static void set(ItemStack s, int v) {
        s.getOrCreateTag().putInt(TAG, Math.max(0, Math.min(MAX, v)));
    }

    /** Крила, якими зараз користується істота: в слоті нагрудника або імплант. */
    public static ItemStack wings(LivingEntity e) {
        ItemStack chest = e.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() instanceof SteamElytraItem) {
            return chest;
        }
        ItemStack imp = Implants.active(e);
        return Implants.kindOf(imp) == Implants.Kind.ELYTRA ? imp : ItemStack.EMPTY;
    }

    public static boolean hasWings(LivingEntity e) {
        return !wings(e).isEmpty();
    }

    public static int energy(LivingEntity e) {
        return get(wings(e));
    }

    /** Змінює запас пари; для імпланта переписує NBT нагрудника. Повертає нове значення або -1, якщо крил немає. */
    public static int modify(LivingEntity e, UnaryOperator<Integer> f) {
        ItemStack chest = e.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() instanceof SteamElytraItem) {
            set(chest, f.apply(get(chest)));
            return get(chest);
        }
        ItemStack imp = Implants.active(e);
        if (Implants.kindOf(imp) != Implants.Kind.ELYTRA) {
            return -1;
        }
        set(imp, f.apply(get(imp)));
        Implants.setImplant(chest, imp);
        return get(imp);
    }

    /** ПКМ крилами (або модулем крил) по блоку: поставити їх на зарядку. */
    public static InteractionResult place(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPlaceContext pc = new BlockPlaceContext(ctx);
        BlockPos pos = pc.getClickedPos();
        if (!pc.canPlace()) {
            return InteractionResult.FAIL;
        }
        BlockState st = CreateArmor.WINGS_BLOCK.get().getStateForPlacement(pc);
        if (st == null || !st.canSurvive(level, pos)) {
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide) {
            level.setBlock(pos, st, 3);
            if (level.getBlockEntity(pos) instanceof SteamWingsBlockEntity be) {
                be.put(ctx.getItemInHand());
            }
            level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_IRON, SoundSource.BLOCKS, 1.0F, 0.8F);
            if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) {
                ctx.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
