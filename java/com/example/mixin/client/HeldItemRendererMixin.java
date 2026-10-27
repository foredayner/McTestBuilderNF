package com.example.mixin.client;

import com.example.client.GearMotionClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** First person: an AI sword with an ordered attack motion swings with that motion. */
@Mixin(ItemInHandRenderer.class)
public abstract class HeldItemRendererMixin {

    @Unique
    private String aib$motion = "";

    @Unique
    private float aib$progress = -1;

    @Inject(method = "renderArmWithItem", at = @At("HEAD"))
    private void aib$pickMotion(AbstractClientPlayer player, float tickDelta, float pitch, InteractionHand hand, float swingProgress,
                                ItemStack item, float equipProgress, PoseStack matrices, MultiBufferSource vertexConsumers,
                                int light, CallbackInfo ci) {
        aib$motion = player.isUsingItem() || hand == InteractionHand.OFF_HAND ? "" : GearMotionClient.motionOf(item);
        aib$progress = aib$motion.isEmpty() ? -1 : GearMotionClient.motionProgress(player, tickDelta);
    }

    /** The normal swing turn (called from the item drawing above): replaced by the ordered motion. */
    @Inject(method = "applyItemArmAttackTransform", at = @At("HEAD"), cancellable = true)
    private void aib$swing(PoseStack matrices, HumanoidArm arm, float swingProgress, CallbackInfo ci) {
        if (aib$motion.isEmpty()) {
            return;
        }
        // The ordered motion (on its own clock) instead of the normal swing turn.
        GearMotionClient.firstPerson(matrices, arm, aib$motion, aib$progress);
        ci.cancel();
    }

    @Inject(method = "renderArmWithItem", at = @At("RETURN"))
    private void aib$clearMotion(AbstractClientPlayer player, float tickDelta, float pitch, InteractionHand hand, float swingProgress,
                                 ItemStack item, float equipProgress, PoseStack matrices, MultiBufferSource vertexConsumers,
                                 int light, CallbackInfo ci) {
        aib$motion = "";
    }
}
