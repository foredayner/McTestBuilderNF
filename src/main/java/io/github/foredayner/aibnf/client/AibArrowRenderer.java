package io.github.foredayner.aibnf.client;

import io.github.foredayner.aibnf.content.AibArrowEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Arrows from AI bows: drawn with the arrow shape designed with the bow (pointing where it
 * flies), or as a normal arrow when the bow has none.
 */
public class AibArrowRenderer extends ArrowRenderer<AibArrowEntity> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/entity/projectiles/arrow.png");

    public AibArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(AibArrowEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(AibArrowEntity entity, float yaw, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers,
                       int light) {
        GearModelRenderer.Model bow = GearModelRenderer.modelFor(entity.bow());
        GearModelRenderer.Model arrow = bow == null ? null : bow.arrow();
        if (arrow == null || arrow.parts().isEmpty()) {
            super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
            return;
        }
        matrices.pushPose();
        // Same turn as a vanilla arrow (which lies along +X, pointing where it flies); the AI's arrow points up (+Y).
        matrices.mulPose(Axis.YP.rotationDegrees(Mth.lerp(tickDelta, entity.yRotO, entity.getYRot()) - 90.0F));
        matrices.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(tickDelta, entity.xRotO, entity.getXRot())));
        matrices.mulPose(Axis.ZP.rotationDegrees(-90.0F));
        matrices.translate(0, -arrow.reach() * 0.6F, 0);
        GearModelRenderer.draw(matrices, vertexConsumers.getBuffer(Sheets.cutoutBlockSheet()), arrow, light,
            OverlayTexture.NO_OVERLAY);
        matrices.popPose();
    }
}
