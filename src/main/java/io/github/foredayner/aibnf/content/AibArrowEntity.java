package io.github.foredayner.aibnf.content;

import io.github.foredayner.aibnf.ai.build.GearEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * An arrow shot from an AI bow. It carries the bow (synced to clients, which draw the bow's
 * arrow shape and trail) and plays the bow's attack effects where it lands.
 * Otherwise a normal arrow: damage, pickup and enchantments are vanilla.
 */
public class AibArrowEntity extends AbstractArrow {

    private static final EntityDataAccessor<ItemStack> BOW = SynchedEntityData.defineId(AibArrowEntity.class, EntityDataSerializers.ITEM_STACK);
    private boolean landed;

    public AibArrowEntity(EntityType<? extends AibArrowEntity> type, Level world) {
        super(type, world);
    }

    public AibArrowEntity(Level world, LivingEntity owner, ItemStack bow) {
        super(AibContent.ARROW, owner, world);
        this.entityData.set(BOW, bow.copyWithCount(1));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(BOW, ItemStack.EMPTY);
    }

    /** True once it has hit a block or an entity (its landing effect has played). */
    public boolean hasLanded() {
        return landed;
    }

    /** The bow this arrow was shot from (empty if unknown). */
    public ItemStack bow() {
        return this.entityData.get(BOW);
    }

    @Override
    protected ItemStack getPickupItem() {
        return new ItemStack(Items.ARROW);
    }

    /** In flight (not stuck in a block): the client draws the bow's trail behind it. */
    public boolean isFlying() {
        return !inGround && !landed;
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        Vec3 at = hit.getEntity().position(); // effects are placed from the feet of what was hit
        super.onHitEntity(hit);
        land(at);
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        land(hit.getLocation());
    }

    private void land(Vec3 at) {
        if (landed || !(level() instanceof ServerLevel world)) {
            return;
        }
        landed = true;
        GearEffects.arrowImpact(world, this, at);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        if (!bow().isEmpty()) {
            nbt.put("aib_bow", bow().save(new CompoundTag()));
        }
        nbt.putBoolean("aib_landed", landed);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("aib_bow")) {
            this.entityData.set(BOW, ItemStack.of(nbt.getCompound("aib_bow")));
        }
        landed = nbt.getBoolean("aib_landed");
    }
}
