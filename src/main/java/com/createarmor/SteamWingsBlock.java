package com.createarmor;

import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * Поставлені парові крила — кінетичний блок Create (як бектанк / риг Octo Arms). Вал або шестерня під'єднуються до
 * кінетичного порту на спині модуля (бік, куди дивиться FACING), блок бере стрес і заряджає пару.
 * Зламати блок або ПКМ порожньою рукою — забрати крила з усією парою.
 */
public class SteamWingsBlock extends HorizontalKineticBlock implements IBE<SteamWingsBlockEntity> {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 14, 15);

    public SteamWingsBlock(Properties p) {
        super(p);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction pref = getPreferredHorizontalFacing(ctx);
        Direction f = pref != null ? pref : ctx.getHorizontalDirection();
        return defaultBlockState().setValue(HORIZONTAL_FACING, f);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(HORIZONTAL_FACING).getAxis();
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(HORIZONTAL_FACING);
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public Class<SteamWingsBlockEntity> getBlockEntityClass() {
        return SteamWingsBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SteamWingsBlockEntity> getBlockEntityType() {
        return CreateArmor.WINGS_BE.get();
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.getItemInHand(hand).isEmpty() || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SteamWingsBlockEntity be) {
            ItemStack s = be.take();
            level.removeBlock(pos, false);
            player.setItemInHand(hand, s);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SteamWingsBlockEntity be) {
            ItemStack s = be.take();
            if (!s.isEmpty()) {
                popResource(level, pos, s);
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder b) {
        return List.of();
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return new ItemStack(CreateArmor.ELYTRA.get());
    }
}
