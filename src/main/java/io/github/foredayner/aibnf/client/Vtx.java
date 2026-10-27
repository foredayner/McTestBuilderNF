package io.github.foredayner.aibnf.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Vector3f;

/**
 * One full vertex (position, colour, texture, overlay, light, normal) in 1.20.1's vertex
 * format calls. 1.21 writes the same thing as addVertex(...).setColor(...)....setNormal(...).
 */
final class Vtx {

    private Vtx() {
    }

    /** A vertex in the pose's space (position and normal are transformed by the pose). */
    static void put(VertexConsumer b, PoseStack.Pose e, float x, float y, float z, int argb, float u, float v,
                    int overlay, int light, float nx, float ny, float nz) {
        b.vertex(e.pose(), x, y, z)
            .color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
            .uv(u, v)
            .overlayCoords(overlay)
            .uv2(light)
            .normal(e.normal(), nx, ny, nz)
            .endVertex();
    }

    static void put(VertexConsumer b, PoseStack.Pose e, Vector3f p, int argb, float u, float v,
                    int overlay, int light, float nx, float ny, float nz) {
        put(b, e, p.x(), p.y(), p.z(), argb, u, v, overlay, light, nx, ny, nz);
    }

    /** A vertex already in camera space (no pose). */
    static void raw(VertexConsumer b, float x, float y, float z, int argb, float u, float v,
                    int overlay, int light, float nx, float ny, float nz) {
        b.vertex(x, y, z)
            .color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
            .uv(u, v)
            .overlayCoords(overlay)
            .uv2(light)
            .normal(nx, ny, nz)
            .endVertex();
    }
}
