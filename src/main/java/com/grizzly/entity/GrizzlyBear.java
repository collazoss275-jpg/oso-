package com.grizzly.entity;

import com.grizzly.registry.ModEntities;
import com.grizzly.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Oso pardo hostil. Si un jugador le da {@link #HONEY_NEEDED} botes de miel,
 * el oso deja de atacarlo (pero no se vuelve su amigo: no lo sigue ni se domestica).
 */
public class GrizzlyBear extends Animal {

    public static final int HONEY_NEEDED = 3;
    public static final int EAT_DURATION = 40;

    private static final EntityDataAccessor<Integer> DATA_EAT_TICKS =
            SynchedEntityData.defineId(GrizzlyBear.class, EntityDataSerializers.INT);

    /** Cría: 0.9 de ancho x 1 de alto. */
    private static final EntityDimensions BABY_DIMENSIONS = EntityDimensions.scalable(0.9F, 1.0F);

    /** Botes de miel recibidos por cada jugador. */
    private final Map<UUID, Integer> honeyFed = new HashMap<>();

    public GrizzlyBear(EntityType<? extends GrizzlyBear> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 20.0D)
                .add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_EAT_TICKS, 0);
    }

    public int getEatTicks() {
        return this.entityData.get(DATA_EAT_TICKS);
    }

    private void setEatTicks(int ticks) {
        this.entityData.set(DATA_EAT_TICKS, ticks);
    }

    public boolean isPacified(Player player) {
        return this.honeyFed.getOrDefault(player.getUUID(), 0) >= HONEY_NEEDED;
    }

    // Hitbox de la cría
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return this.isBaby() ? BABY_DIMENSIONS : this.getType().getDimensions();
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new EatFreezeGoal());
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25D, true));
        this.goalSelector.addGoal(3, new FollowParentGoal(this, 1.1D));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));

        // Si lo golpean, responde (las crías no pelean)
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return !GrizzlyBear.this.isBaby() && super.canUse();
            }
        });
        // Hostil: ataca a los jugadores, menos a los que ya le dieron 3 botes de miel
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, null) {
            @Override
            public boolean canUse() {
                return !GrizzlyBear.this.isBaby() && super.canUse();
            }

            @Override
            public void start() {
                super.start();
                if (GrizzlyBear.this.getTarget() instanceof Player p && GrizzlyBear.this.isPacified(p)) {
                    GrizzlyBear.this.setTarget(null);
                }
            }
        });
    }

    /** Se queda quieto mientras come. */
    private class EatFreezeGoal extends Goal {
        EatFreezeGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return GrizzlyBear.this.getEatTicks() > 0;
        }

        @Override
        public boolean canContinueToUse() {
            return GrizzlyBear.this.getEatTicks() > 0;
        }

        @Override
        public void start() {
            GrizzlyBear.this.getNavigation().stop();
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(ModItems.HONEY_JAR.get())) {
            if (this.getEatTicks() > 0 || this.isBaby()) {
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
            if (!this.level().isClientSide) {
                int count = this.honeyFed.merge(player.getUUID(), 1, Integer::sum);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                this.setEatTicks(EAT_DURATION);
                this.playSound(SoundEvents.HONEY_DRINK, 1.0F, 1.0F);

                if (count >= HONEY_NEEDED) {
                    if (this.getTarget() == player) {
                        this.setTarget(null);
                    }
                    this.setLastHurtByMob(null);
                    player.displayClientMessage(Component.translatable("message.grizzlybear.pacified"), true);
                    if (this.level() instanceof ServerLevel sl) {
                        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                                this.getX(), this.getY() + 1.6D, this.getZ(), 8, 0.6D, 0.4D, 0.6D, 0.0D);
                    }
                } else {
                    player.displayClientMessage(
                            Component.translatable("message.grizzlybear.honey_progress", count, HONEY_NEEDED), true);
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            int ticks = this.getEatTicks();
            if (ticks > 0) {
                this.setEatTicks(ticks - 1);
                if (ticks % 4 == 0 && this.level() instanceof ServerLevel sl) {
                    Vec3 look = this.getViewVector(1.0F);
                    sl.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(ModItems.HONEY_JAR.get())),
                            this.getX() + look.x * 1.4D, this.getEyeY() - 0.3D, this.getZ() + look.z * 1.4D,
                            3, 0.15D, 0.15D, 0.15D, 0.05D);
                }
                if (ticks % 10 == 0) {
                    this.playSound(SoundEvents.GENERIC_EAT, 0.8F, 0.9F);
                }
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Integer> e : this.honeyFed.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", e.getKey());
            entry.putInt("Count", e.getValue());
            list.add(entry);
        }
        tag.put("HoneyFed", list);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.honeyFed.clear();
        ListTag list = tag.getList("HoneyFed", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            this.honeyFed.put(entry.getUUID("Player"), entry.getInt("Count"));
        }
    }

    /** No se reproduce ni se alimenta como animal normal. */
    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return ModEntities.GRIZZLY_BEAR.get().create(level);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return this.isBaby() ? SoundEvents.POLAR_BEAR_AMBIENT_BABY : SoundEvents.POLAR_BEAR_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.POLAR_BEAR_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.POLAR_BEAR_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.POLAR_BEAR_STEP, 0.15F, 1.0F);
    }
}
