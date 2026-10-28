package io.github.foredayner.aibnf.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Vector3f;

/** One full vertex (position, colour, texture, overlay, light, normal). */
final class Vtx {

    private Vtx() {
    }

    /** A vertex in the pose's space (position and normal are transformed by the pose). */
    static void put(VertexConsumer b, PoseStack.Pose e, float x, float y, float z, int argb, float u, float v,
                    int overlay, int light, float nx, float ny, float nz) {
        b.addVertex(e, x, y, z).setColor(argb).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(e, nx, ny, nz);
    }

    static void put(VertexConsumer b, PoseStack.Pose e, Vector3f p, int argb, float u, float v,
                    int overlay, int light, float nx, float ny, float nz) {
        put(b, e, p.x(), p.y(), p.z(), argb, u, v, overlay, light, nx, ny, nz);
    }
}
