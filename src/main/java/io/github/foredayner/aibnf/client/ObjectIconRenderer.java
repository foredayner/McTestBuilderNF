package io.github.foredayner.aibnf.client;

import io.github.foredayner.aibnf.AiBuildAssistantMod;
import io.github.foredayner.aibnf.ai.build.ObjectItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Draws an object item as its own front picture (16 x 16, carried in the item), like a flat
 * item sprite: in the inventory, the hotbar, the hand and on the ground. Pixels are coloured
 * quads, so no texture has to be made at runtime.
 */
public final class ObjectIconRenderer implements SpecialModelRenderer<int[]> {

    public static final ResourceLocation TYPE_ID = ResourceLocation.fromNamespaceAndPath(AiBuildAssistantMod.MOD_ID, "object_icon");
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath(AiBuildAssistantMod.MOD_ID, "textures/misc/white.png");
    private static final int N = BuildPreview.ICON;
    private static final int[] FALLBACK = fallback();

    public record Unbaked() implements SpecialModelRenderer.Unbaked {
        public static final MapCodec<io.github.foredayner.aibnf.client.ObjectIconRenderer.Unbaked> CODEC = MapCodec.unit(new io.github.foredayner.aibnf.client.ObjectIconRenderer.Unbaked());

        @Override
        public SpecialModelRenderer<?> bake(EntityModelSet entityModels) {
            return new ObjectIconRenderer();
        }

        @Override
        public MapCodec<? extends SpecialModelRenderer.Unbaked> type() {
            return CODEC;
        }
    }

    @Override
    public int[] getData(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return FALLBACK;
        }
        int[] icon = data.copyTag().getIntArray(ObjectItems.ICON);
        return icon.length == N * N ? icon : FALLBACK;
    }

    @Override
    public void render(int[] icon, ItemDisplayContext mode, PoseStack matrices, MultiBufferSource vertexConsumers,
                       int light, int overlay, boolean glint) {
        int[] img = icon == null ? FALLBACK : icon;
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        PoseStack.Pose entry = matrices.last();
        float px = 1.0F / N;
        float front = 8.5F / 16, back = 7.5F / 16;
        for (int y = 0; y < N; y++) {
            int x = 0;
            while (x < N) {
                int c = img[y * N + x];
                if ((c >>> 24) == 0) {
                    x++;
                    continue;
                }
                int end = x + 1;
                while (end < N && img[y * N + end] == c) {
                    end++;
                }
                float x0 = x * px, x1 = end * px;
                float y1 = 1 - y * px, y0 = 1 - (y + 1) * px;
                int argb = 0xFF000000 | c;
                quad(buffer, entry, x0, y0, x1, y1, front, 1, argb, light, overlay);
                quad(buffer, entry, x0, y0, x1, y1, back, -1, argb, light, overlay);
                x = end;
            }
        }
    }

    private static void quad(VertexConsumer b, PoseStack.Pose e, float x0, float y0, float x1, float y1, float z, int nz,
                             int argb, int light, int overlay) {
        float[][] corners = nz > 0
            ? new float[][] { { x0, y0 }, { x1, y0 }, { x1, y1 }, { x0, y1 } }
            : new float[][] { { x1, y0 }, { x0, y0 }, { x0, y1 }, { x1, y1 } };
        for (float[] c : corners) {
            b.addVertex(e, c[0], c[1], z).setColor(argb).setUv(0.5F, 0.5F).setOverlay(overlay).setLight(light).setNormal(e, 0, 0, nz);
        }
    }

    /** A small wooden crate, for an object item without a picture. */
    private static int[] fallback() {
        int[] img = new int[N * N];
        for (int y = 3; y < 15; y++) {
            for (int x = 2; x < 14; x++) {
                boolean edge = y == 3 || y == 14 || x == 2 || x == 13 || x == y - 1 || x == 16 - y;
                img[y * N + x] = edge ? 0xFF6B3A1A : 0xFFC98A4B;
            }
        }
        return img;
    }
}
