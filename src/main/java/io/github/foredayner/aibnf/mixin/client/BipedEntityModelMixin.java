package io.github.foredayner.aibnf.mixin.client;

import io.github.foredayner.aibnf.client.GearModelRenderer;
import io.github.foredayner.aibnf.client.GearMotionClient;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Third person: an AI sword with an ordered attack motion moves the arm with that motion, and a
 * long AI weapon is held with both hands. (1.21 reads these from the render state; in 1.20.1
 * the model gets the entity itself.)
 */
@Mixin(HumanoidModel.class)
public abstract class BipedEntityModelMixin<T extends LivingEntity> {

    @Shadow
    @Final
    public ModelPart body;

    @Shadow
    @Final
    public ModelPart rightArm;

    @Shadow
    @Final
    public ModelPart leftArm;

    @Shadow
    protected abstract HumanoidArm getAttackArm(T entity);

    @Inject(method = "setupAttackAnimation", at = @At("HEAD"), cancellable = true)
    private void aib$motion(T entity, float ageInTicks, CallbackInfo ci) {
        boolean right = getAttackArm(entity) == HumanoidArm.RIGHT;
        if (!entity.isUsingItem() && GearModelRenderer.twoHanded(entity.getMainHandItem())) {
            // A long weapon: both hands on the grip, held ready in front (the swing below moves the main arm).
            GearMotionClient.twoHandedHold(right ? rightArm : leftArm, right ? leftArm : rightArm, right);
        }
        String motion = entity.swingingArm == InteractionHand.OFF_HAND ? "" : GearMotionClient.motionOf(entity.getMainHandItem());
        if (motion.isEmpty()) {
            return;
        }
        float tickDelta = ageInTicks - entity.tickCount;
        float p = GearMotionClient.motionProgress(entity, tickDelta);
        if (p < 0) {
            return;
        }
        GearMotionClient.thirdPerson(right ? rightArm : leftArm, right ? leftArm : rightArm, body, right, motion, p);
        ci.cancel();
    }
}
