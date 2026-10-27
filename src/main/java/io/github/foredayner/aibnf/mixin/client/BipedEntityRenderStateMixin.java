package io.github.foredayner.aibnf.mixin.client;

import io.github.foredayner.aibnf.client.AibMotionState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(HumanoidRenderState.class)
public abstract class BipedEntityRenderStateMixin implements AibMotionState {

    @Unique
    private String aib$motion = "";

    @Unique
    private boolean aib$twoHanded;

    @Unique
    private float aib$motionProgress = -1;

    @Override
    public float aib$getMotionProgress() {
        return aib$motionProgress;
    }

    @Override
    public void aib$setMotionProgress(float progress) {
        aib$motionProgress = progress;
    }

    @Override
    public boolean aib$isTwoHanded() {
        return aib$twoHanded;
    }

    @Override
    public void aib$setTwoHanded(boolean twoHanded) {
        aib$twoHanded = twoHanded;
    }

    @Override
    public String aib$getMotion() {
        return aib$motion;
    }

    @Override
    public void aib$setMotion(String motion) {
        aib$motion = motion == null ? "" : motion;
    }
}
