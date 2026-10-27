package io.github.foredayner.aibnf.mixin.client;

import io.github.foredayner.aibnf.client.AibMotionState;
import io.github.foredayner.aibnf.client.GearMotionClient;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Third person: remember the main-hand sword's attack motion on the render state. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
        at = @At("TAIL"))
    private void aib$motion(LivingEntity entity, LivingEntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (state instanceof AibMotionState motion) {
            String m = entity.swingingArm == InteractionHand.OFF_HAND ? "" : GearMotionClient.motionOf(entity.getMainHandItem());
            motion.aib$setMotion(m);
            motion.aib$setMotionProgress(m.isEmpty() ? -1 : GearMotionClient.motionProgress(entity, tickDelta));
            motion.aib$setTwoHanded(!entity.isUsingItem()
                && io.github.foredayner.aibnf.client.GearModelRenderer.twoHanded(entity.getMainHandItem()));
        }
    }
}
