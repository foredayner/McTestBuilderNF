package io.github.foredayner.aibnf.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * The client side of AI equipment items: their 3D model is drawn by GearModelRenderer
 * (1.21.4 used a "special" item model for this; 1.20.1 uses Forge's custom item renderer).
 */
public final class GearClientExtensions implements IClientItemExtensions {

    public static final GearClientExtensions INSTANCE = new GearClientExtensions();
    private static final GearModelRenderer MODEL = new GearModelRenderer();

    private Renderer renderer;

    private GearClientExtensions() {
    }

    @Override
    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        if (renderer == null) {
            renderer = new Renderer();
        }
        return renderer;
    }

    private static final class Renderer extends BlockEntityWithoutLevelRenderer {
        Renderer() {
            super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        }

        @Override
        public void renderByItem(ItemStack stack, ItemDisplayContext mode, PoseStack matrices, MultiBufferSource buffers,
                                 int light, int overlay) {
            MODEL.render(GearModelRenderer.modelFor(stack), mode, matrices, buffers, light, overlay, false);
        }
    }
}
