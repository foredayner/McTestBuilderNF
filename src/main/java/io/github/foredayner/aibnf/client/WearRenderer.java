package io.github.foredayner.aibnf.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Draws worn AI equipment (helmets, hats, armour, back items) on players, zombies, armour stands
 * and other biped models, attached to the head or the torso so it follows their movement.
 *
 * <p>The AI designs with +Y up; entity models have +Y down, so the model is flipped on Y (which
 * also turns its faces inside out, hence a no-cull layer).
 *
 * <p>1.21 (Fabric) hooks this in as an armour renderer; 1.20.1 (Forge) adds it as an extra layer
 * to every renderer that draws a humanoid model.
 */
public final class WearRenderer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {

    private static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST };

    public WearRenderer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    /** Adds the layer to every player skin and every entity renderer with a humanoid model. */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            LivingEntityRenderer renderer = event.getSkin(skin);
            if (renderer != null) {
                renderer.addLayer(new WearRenderer(renderer));
            }
        }
        for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES.getValues()) {
            EntityRenderer<?> renderer;
            try {
                renderer = event.getRenderer((EntityType) type);
            } catch (ClassCastException exception) {
                continue;
            }
            if (renderer instanceof LivingEntityRenderer living && living.getModel() instanceof HumanoidModel) {
                living.addLayer(new WearRenderer(living));
            }
        }
    }

    @Override
    public void render(PoseStack matrices, MultiBufferSource vertexConsumers, int light, T entity, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        for (EquipmentSlot slot : SLOTS) {
            renderSlot(matrices, vertexConsumers, entity.getItemBySlot(slot), slot, light, getParentModel());
        }
    }

    private static void renderSlot(PoseStack matrices, MultiBufferSource vertexConsumers, ItemStack stack, EquipmentSlot slot,
                                   int light, HumanoidModel<?> contextModel) {
        if (!(stack.getItem() instanceof io.github.foredayner.aibnf.content.GearItem gear) || gear.kind().slot != slot) {
            return;
        }
        GearModelRenderer.Model model = GearModelRenderer.modelFor(stack);
        if (model == null || model.parts().isEmpty()) {
            return;
        }
        ModelPart bone = slot == EquipmentSlot.HEAD ? contextModel.head : contextModel.body;
        if (!bone.visible) {
            return;
        }
        matrices.pushPose();
        bone.translateAndRotate(matrices);
        matrices.scale(1.0F, -1.0F, 1.0F);
        GearModelRenderer.draw(matrices, vertexConsumers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS)),
            model, light, OverlayTexture.NO_OVERLAY);
        GearModelRenderer.drawAura(matrices, vertexConsumers, model, OverlayTexture.NO_OVERLAY);
        matrices.popPose();
    }
}
