package com.example.client;

import com.example.content.AibArrowEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Arrows from AI bows: drawn with the arrow shape designed with the bow (pointing where it
 * flies), or as a normal arrow when the bow has none.
 */
public class AibArrowRenderer extends ArrowRenderer<AibArrowEntity, AibArrowRenderer.State> {

    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/projectiles/arrow.png");

    public static class State extends ArrowRenderState {
        GearModelRenderer.Model arrow;
    }

    public AibArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected ResourceLocation getTexture(State state) {
        return TEXTURE;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void updateRenderState(AibArrowEntity entity, State state, float tickDelta) {
        super.extractRenderState(entity, state, tickDelta);
        GearModelRenderer.Model bow = GearModelRenderer.modelFor(entity.bow());
        state.arrow = bow == null ? null : bow.arrow();
    }

    @Override
    public void render(State state, PoseStack matrices, MultiBufferSource vertexConsumers, int light) {
        if (state.arrow == null || state.arrow.parts().isEmpty()) {
            super.render(state, matrices, vertexConsumers, light);
            return;
        }
        matrices.pushPose();
        // Same turn as a vanilla arrow (which lies along +X, pointing where it flies); the AI's arrow points up (+Y).
        matrices.mulPose(Axis.YP.rotationDegrees(state.yRot - 90.0F));
        matrices.mulPose(Axis.ZP.rotationDegrees(state.xRot));
        matrices.mulPose(Axis.ZP.rotationDegrees(-90.0F));
        matrices.translate(0, -state.arrow.reach() * 0.6F, 0);
        GearModelRenderer.draw(matrices, vertexConsumers.getBuffer(Sheets.cutoutBlockSheet()), state.arrow, light,
            OverlayTexture.NO_OVERLAY);
        matrices.popPose();
    }
}
