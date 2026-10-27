package com.example.mixin.client;

import com.example.client.AibMotionState;
import com.example.client.GearMotionClient;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Third person: an AI sword with an ordered attack motion moves the arm with that motion. */
@Mixin(HumanoidModel.class)
public abstract class BipedEntityModelMixin {

    @Shadow
    @Final
    public ModelPart body;

    @Shadow
    @Final
    public ModelPart rightArm;

    @Shadow
    @Final
    public ModelPart leftArm;

    @Inject(method = "setupAttackAnimation", at = @At("HEAD"), cancellable = true)
    private void aib$motion(HumanoidRenderState state, float animationProgress, CallbackInfo ci) {
        if (!(state instanceof AibMotionState m)) {
            return;
        }
        boolean right = state.attackArm == HumanoidArm.RIGHT;
        if (m.aib$isTwoHanded()) {
            // A long weapon: both hands on the grip, held ready in front (the swing below moves the main arm).
            GearMotionClient.twoHandedHold(right ? rightArm : leftArm, right ? leftArm : rightArm, right);
        }
        if (m.aib$getMotion().isEmpty()) {
            return;
        }
        float p = m.aib$getMotionProgress();
        if (p < 0) {
            return;
        }
        GearMotionClient.thirdPerson(right ? rightArm : leftArm, right ? leftArm : rightArm, body, right, m.aib$getMotion(), p);
        ci.cancel();
    }
}
