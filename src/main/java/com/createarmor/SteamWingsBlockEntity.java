package com.createarmor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/** Крила на зарядці: читає швидкість обертання кінетичного блока Create над собою (або поруч) і набирає пару. */
public class SteamWingsBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final Map<Class<?>, Method> SPEED = new HashMap<>();
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private ItemStack stack = ItemStack.EMPTY;
    /** 0 — стоїть, 1 — заряджається, 2 — повна. Синхронізується з клієнтом для анімації. */
    private int mode;

    public SteamWingsBlockEntity(BlockPos pos, BlockState state) {
        super(CreateArmor.WINGS_BE.get(), pos, state);
    }

    public void put(ItemStack s) {
        stack = s.copyWithCount(1);
        sync();
    }

    public ItemStack take() {
        ItemStack s = stack;
        stack = ItemStack.EMPTY;
        return s;
    }

    public int energy() {
        return Steam.get(stack);
    }

    public int mode() {
        return mode;
    }

    static void serverTick(Level level, BlockPos pos, BlockState state, SteamWingsBlockEntity be) {
        if (be.stack.isEmpty()) {
            return;
        }
        float speed = 0;
        for (Direction d : new Direction[]{Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN}) {
            speed = Math.max(speed, Math.abs(speedOf(level.getBlockEntity(pos.relative(d)))));
        }
        int have = Steam.get(be.stack);
        int newMode;
        if (have >= Steam.MAX) {
            newMode = 2;
        } else if (speed > 0) {
            newMode = 1;
            int add = Math.max(1, Math.round(speed / 16F));
            Steam.set(be.stack, have + add);
            be.setChanged();
            if (level.getGameTime() % 10 == 0 && level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 2, 0.15, 0.05, 0.15, 0.01);
            }
            if (Steam.get(be.stack) >= Steam.MAX) {
                level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.6F, 1.6F);
            }
        } else {
            newMode = 0;
        }
        if (newMode != be.mode || newMode == 1 && level.getGameTime() % 20 == 0) {
            be.mode = newMode;
            be.sync();
        }
    }

    private static float speedOf(BlockEntity be) {
        if (be == null) {
            return 0;
        }
        try {
            Method m = SPEED.computeIfAbsent(be.getClass(), c -> {
                try {
                    return c.getMethod("getSpeed");
                } catch (NoSuchMethodException e) {
                    return null;
                }
            });
            if (m == null) {
                return 0;
            }
            Object v = m.invoke(be);
            return v instanceof Float f ? f : 0;
        } catch (ReflectiveOperationException e) {
            return 0;
        }
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Wings", stack.save(new CompoundTag()));
        tag.putInt("Mode", mode);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        stack = ItemStack.of(tag.getCompound("Wings"));
        mode = tag.getInt("Mode");
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, st -> switch (mode) {
            case 1 -> st.setAndContinue(RawAnimation.begin().thenLoop("animation.steam_elytra.elytra_charge"));
            case 2 -> st.setAndContinue(RawAnimation.begin().thenLoop("animation.steam_elytra.elytra_charged"));
            default -> st.setAndContinue(RawAnimation.begin().thenLoop("animation.steam_elytra.idle"));
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
