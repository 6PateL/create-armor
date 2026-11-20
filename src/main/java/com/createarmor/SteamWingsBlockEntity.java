package com.createarmor;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Крила на зарядці: кінетичний блок Create, що бере стрес; чим швидше обертання, тим швидше набирається пара. */
public class SteamWingsBlockEntity extends KineticBlockEntity implements GeoBlockEntity {
    /** Стрес (одиниць на 1 об/хв), який беруть крила під час зарядки. */
    public static final double STRESS = 4.0;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private ItemStack stack = ItemStack.EMPTY;
    /** 0 — стоїть, 1 — заряджається, 2 — повна. */
    private int mode;

    public SteamWingsBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public SteamWingsBlockEntity(BlockPos pos, BlockState state) {
        this(CreateArmor.WINGS_BE.get(), pos, state);
    }

    public void put(ItemStack s) {
        stack = s.copyWithCount(1);
        refreshStress();
        notifyUpdate();
    }

    public ItemStack take() {
        ItemStack s = stack;
        stack = ItemStack.EMPTY;
        return s;
    }

    public int energy() {
        return Steam.get(stack);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || stack.isEmpty()) {
            return;
        }
        float speed = Math.abs(getSpeed());
        int have = Steam.get(stack);
        int newMode;
        if (have >= Steam.MAX) {
            newMode = 2;
        } else if (speed > 0 && !isOverStressed()) {
            newMode = 1;
            Steam.set(stack, have + Math.max(1, Math.round(speed / 16F)));
            setChanged();
            if (level.getGameTime() % 10 == 0 && level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.CLOUD, worldPosition.getX() + 0.5, worldPosition.getY() + 0.9, worldPosition.getZ() + 0.5,
                        2, 0.15, 0.05, 0.15, 0.01);
            }
            if (Steam.get(stack) >= Steam.MAX) {
                level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.6F, 1.6F);
            }
        } else {
            newMode = 0;
        }
        if (newMode != mode || newMode == 1 && level.getGameTime() % 20 == 0) {
            if ((newMode == 2) != (mode == 2)) {
                refreshStress();
            }
            mode = newMode;
            sendData();
        }
    }

    private boolean wantsSteam() {
        return !stack.isEmpty() && Steam.get(stack) < Steam.MAX;
    }

    /** Стрес беруть лише крила, яким ще треба пара; повні або порожній блок мережу не навантажують. */
    @Override
    public float calculateStressApplied() {
        float impact = wantsSteam() ? (float) STRESS : 0F;
        lastStressApplied = impact;
        return impact;
    }

    private void refreshStress() {
        if (hasNetwork()) {
            getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
        }
    }

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        tag.put("Wings", stack.save(new CompoundTag()));
        tag.putInt("Mode", mode);
    }

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        stack = ItemStack.of(tag.getCompound("Wings"));
        mode = tag.getInt("Mode");
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
