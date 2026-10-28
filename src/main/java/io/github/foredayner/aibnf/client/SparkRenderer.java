package io.github.foredayner.aibnf.client;

import io.github.foredayner.aibnf.AiBuildAssistantMod;
import io.github.foredayner.aibnf.ai.build.GearEffects;
import io.github.foredayner.aibnf.ai.build.Spark;
import io.github.foredayner.aibnf.content.AibArrowEntity;
import io.github.foredayner.aibnf.content.GearItem;
import io.github.foredayner.aibnf.content.GearKind;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws the colour-dot effects of AI equipment: attack and landing effects the server announces,
 * and, found by this game itself, the effects of equipment held or worn by anyone nearby and the
 * trails of arrows from AI bows. Every dot is a small glowing sprite facing the camera.
 */
public final class SparkRenderer {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(AiBuildAssistantMod.MOD_ID, "textures/misc/spark.png");
    private static final int MAX_EFFECTS = 160;
    private static final int MAX_DOTS_PER_FRAME = 9000;
    private static final double RANGE = 48;

    /** One effect being drawn. */
    private static final class Effect {
        final Spark spark;
        final List<double[]> dots;
        Vec3 origin;
        Vec3 dir;
        final double scale;
        final double start;
        /** Loops in place, following an entity (idle effects and trails without copies). */
        final Entity follow;
        final String anchor;
        boolean seen = true;

        Effect(Spark spark, Vec3 origin, Vec3 dir, double scale, double start, Entity follow, String anchor) {
            this.spark = spark;
            this.dots = spark.dots();
            this.origin = origin;
            this.dir = dir;
            this.scale = scale;
            this.start = start;
            this.follow = follow;
            this.anchor = anchor;
        }
    }

    private static final List<Effect> EFFECTS = new ArrayList<>();
    /** Following effects by "entity id / slot / index", kept while the equipment stays on. */
    private static final Map<String, Effect> FOLLOWING = new HashMap<>();
    private static final TagCache<Spark.Effects> CACHE = new TagCache<>(256);

    private SparkRenderer() {
    }

    /** The server says: these effects play here now. */
    public static void play(String json, Vec3 at, Vec3 dir, double scale) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }
        double now = now(client);
        for (Spark s : Spark.listFromJson(json)) {
            add(new Effect(s, at, dir, scale, now, null, ""));
        }
    }

    private static void add(Effect e) {
        if (EFFECTS.size() >= MAX_EFFECTS) {
            EFFECTS.remove(0);
        }
        EFFECTS.add(e);
    }

    private static double now(Minecraft client) {
        return client.level.getGameTime() + client.getTimer().getGameTimeDeltaPartialTick(true);
    }

    private static Spark.Effects effectsOf(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof GearItem)) {
            return null;
        }
        net.minecraft.world.item.component.CustomData data = io.github.foredayner.aibnf.content.ItemData.raw(stack);
        if (data == null || !data.contains(GearEffects.KEY)) {
            return null;
        }
        return CACHE.get(data, () -> GearEffects.of(stack));
    }

    private static final EquipmentSlot[] SLOTS = { EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST };

    /** Every client tick: effects of equipment held or worn nearby, and arrow trails. */
    public static void tick(Minecraft client) {
        if (client.level == null || client.player == null) {
            EFFECTS.clear();
            FOLLOWING.clear();
            return;
        }
        long time = client.level.getGameTime();
        double now = time;
        for (Effect e : FOLLOWING.values()) {
            e.seen = false;
        }
        Vec3 eye = client.player.getEyePosition();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity.distanceToSqr(eye) > RANGE * RANGE) {
                continue;
            }
            if (entity instanceof LivingEntity living) {
                for (EquipmentSlot slot : SLOTS) {
                    ItemStack stack = living.getItemBySlot(slot);
                    Spark.Effects fx = effectsOf(stack);
                    if (fx == null || fx.idle.isEmpty()) {
                        continue;
                    }
                    GearKind kind = ((GearItem) stack.getItem()).kind();
                    if (kind.worn() != (slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST) || (kind.worn() && kind.slot != slot)) {
                        continue; // a worn piece only counts where it is worn; hand items only in hands
                    }
                    for (int i = 0; i < fx.idle.size(); i++) {
                        Spark s = fx.idle.get(i);
                        String anchor = !s.anchor.isEmpty() ? s.anchor : defaultAnchor(kind, slot);
                        emitOrFollow(entity, s, anchor, slot.name() + i, time, now);
                    }
                }
            } else if (entity instanceof AibArrowEntity arrow && arrow.isFlying()) {
                Spark.Effects fx = effectsOf(arrow.bow());
                if (fx != null && !fx.trail.isEmpty()) {
                    emitOrFollow(arrow, fx.trail.get(0), "arrow", "trail", time, now);
                }
            }
        }
        FOLLOWING.values().removeIf(e -> {
            if (!e.seen) {
                EFFECTS.remove(e);
                return true;
            }
            return false;
        });
    }

    private static void emitOrFollow(Entity entity, Spark s, String anchor, String key, long time, double now) {
        if (s.every > 0) {
            if ((time + entity.getId()) % s.every == 0) {
                add(new Effect(s, anchorPos(entity, anchor, 1.0F), lookOf(entity), 1, now, null, anchor));
            }
            return;
        }
        String id = entity.getId() + "/" + key;
        Effect e = FOLLOWING.get(id);
        if (e == null || e.spark != s) {
            if (e != null) {
                EFFECTS.remove(e);
            }
            e = new Effect(s, anchorPos(entity, anchor, 1.0F), lookOf(entity), 1, now, entity, anchor);
            FOLLOWING.put(id, e);
            add(e);
        }
        e.seen = true;
    }

    private static String defaultAnchor(GearKind kind, EquipmentSlot slot) {
        if (!kind.worn()) {
            return "item";
        }
        if (kind.slot == EquipmentSlot.HEAD) {
            return "head";
        }
        return kind == GearKind.BACK ? "back" : "body";
    }

    private static Vec3 lookOf(Entity entity) {
        if (entity instanceof AibArrowEntity arrow) {
            Vec3 v = arrow.getDeltaMovement();
            return v.lengthSqr() < 1e-6 ? arrow.getViewVector(1.0F) : v.normalize();
        }
        float yaw = (entity instanceof LivingEntity l ? l.getVisualRotationYInDegrees() : entity.getYRot()) * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
    }

    /** Where an effect sits on an entity. */
    private static Vec3 anchorPos(Entity entity, String anchor, float delta) {
        Vec3 base = entity.getPosition(delta);
        Vec3 forward = lookOf(entity);
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        double h = entity.getBbHeight();
        return switch (anchor) {
            case "arrow" -> base;
            case "head" -> base.add(0, entity.getEyeHeight() + 0.45, 0);
            case "body", "around" -> base.add(0, h * 0.55, 0);
            case "back" -> base.add(0, h * 0.62, 0).subtract(forward.scale(0.4));
            case "feet" -> base.add(0, 0.05, 0);
            default -> base.add(0, h * 0.5, 0).add(right.scale(-0.38)).add(forward.scale(0.35)); // right hand
        };
    }

    // ------------------------------------------------------------------ drawing

    /** Drawn right after the entities (Forge's level render stage event). */
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || EFFECTS.isEmpty()) {
            return;
        }
        // In 1.20.1 the level's pose stack holds the camera turn; positions below are camera-relative.
        PoseStack.Pose pose = event.getPoseStack().last();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Quaternionf rot = camera.rotation();
        Vector3f camRight = rot.transform(new Vector3f(1, 0, 0));
        Vector3f camUp = rot.transform(new Vector3f(0, 1, 0));
        float delta = client.getTimer().getGameTimeDeltaPartialTick(true);
        double now = client.level.getGameTime() + delta;
        MultiBufferSource.BufferSource immediate = client.renderBuffers().bufferSource();
        RenderType layer = RenderType.entityTranslucentEmissive(TEXTURE);
        VertexConsumer buffer = immediate.getBuffer(layer);
        int budget = MAX_DOTS_PER_FRAME;
        for (Iterator<Effect> it = EFFECTS.iterator(); it.hasNext(); ) {
            Effect e = it.next();
            Spark s = e.spark;
            double age = Math.max(0, now - e.start);
            boolean loops = e.follow != null;
            double t = age / s.ticks;
            if (!loops && t >= 1) {
                it.remove();
                continue;
            }
            if (loops) {
                if (e.follow.isRemoved()) {
                    continue;
                }
                e.origin = anchorPos(e.follow, e.anchor, delta);
                e.dir = lookOf(e.follow);
                t = t - Math.floor(t);
            }
            budget -= draw(buffer, pose, e, t, age / 20.0, cam, camRight, camUp, budget);
            if (budget <= 0) {
                break;
            }
        }
        immediate.endBatch(layer);
    }

    /** Draws one effect at life fraction t (0-1), age in seconds; returns the dots drawn. */
    private static int draw(VertexConsumer buffer, PoseStack.Pose pose, Effect e, double t, double seconds, Vec3 cam, Vector3f camRight, Vector3f camUp,
                            int budget) {
        Spark s = e.spark;
        List<String> m = s.moves;
        Vec3 d = e.dir.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : e.dir.normalize();
        Vec3 upWorld = new Vec3(0, 1, 0);
        Vec3 right = d.cross(upWorld);
        right = right.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(d).normalize();
        Vec3 flatForward = new Vec3(d.x, 0, d.z);
        flatForward = flatForward.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : flatForward.normalize();
        Vec3 flatRight = new Vec3(-flatForward.z, 0, flatForward.x);
        // Shape axes in the world: u, v (the shape's plane) and w (out of it).
        Vec3 u, v, w;
        switch (s.plane) {
            case "flat" -> {
                u = flatRight;
                v = flatForward;
                w = upWorld;
            }
            case "side" -> {
                u = d;
                v = up;
                w = right;
            }
            default -> {
                u = right;
                v = up;
                w = d.scale(-1);
            }
        }
        double size = s.size * e.scale;
        if (m.contains("expand")) {
            size *= 1 + 1.6 * t;
        }
        if (m.contains("shrink")) {
            size *= 1 - 0.9 * t;
        }
        if (m.contains("pulse")) {
            size *= 1 + 0.18 * Math.sin(seconds * Math.PI * 2 * Math.max(0.5, s.speed));
        }
        double spin = m.contains("spin") ? seconds * Math.PI * 2 * s.speed : 0;
        double cos = Math.cos(spin), sin = Math.sin(spin);
        Vec3 center = e.origin;
        double travel = seconds * s.speed;
        if (m.contains("forward")) {
            center = center.add(d.scale(travel));
        }
        if (m.contains("back")) {
            center = center.subtract(d.scale(travel));
        }
        if (m.contains("rise")) {
            center = center.add(0, travel, 0);
        }
        if (m.contains("fall")) {
            center = center.add(0, -travel, 0);
        }
        if (m.contains("orbit")) {
            double a = seconds * Math.PI * 2 * Math.max(0.2, s.speed);
            center = center.add(flatRight.scale(Math.cos(a) * 0.6 * e.scale)).add(flatForward.scale(Math.sin(a) * 0.6 * e.scale));
        }
        float alpha = 1;
        if (m.contains("fade")) {
            alpha = (float) Math.max(0, 1 - t * t);
        }
        int rgb = s.color;
        if (s.color2 >= 0) {
            rgb = lerpColor(s.color, s.color2, t);
        }
        double half = s.dot * e.scale * 0.5 * (m.contains("shrink") ? Math.max(0.3, 1 - t) : 1);
        int n = Math.min(e.dots.size(), budget);
        int drawn = 0;
        for (int i = 0; i < n; i++) {
            double[] p = e.dots.get(i);
            if (m.contains("reveal") && (i + 0.5) / n > Math.min(1, t * 2.2)) {
                continue;
            }
            double pu = p[0] * cos - p[1] * sin;
            double pv = p[0] * sin + p[1] * cos;
            double pw = p[2];
            if (m.contains("wave")) {
                pv += 0.15 * Math.sin(p[0] * Math.PI * 2 + seconds * 6);
            }
            double radial = 1;
            if (m.contains("scatter")) {
                radial += travel / Math.max(0.1, size) * 1.0;
            }
            if (m.contains("gather")) {
                radial *= 1 + 2.5 * (1 - t);
            }
            double k = size * radial;
            double x = center.x + (u.x * pu + v.x * pv + w.x * pw) * k;
            double y = center.y + (u.y * pu + v.y * pv + w.y * pw) * k;
            double z = center.z + (u.z * pu + v.z * pv + w.z * pw) * k;
            float a = alpha;
            if (m.contains("flicker")) {
                a *= 0.35F + 0.65F * (float) Math.abs(Math.sin(seconds * 23 + i * 1.7));
            }
            if (a <= 0.02F) {
                continue;
            }
            int argb = ((int) (a * 255) << 24) | rgb;
            quad(buffer, pose, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z), (float) half, camRight, camUp, argb);
            drawn++;
        }
        return drawn;
    }

    private static void quad(VertexConsumer b, PoseStack.Pose pose, float x, float y, float z, float h, Vector3f r, Vector3f u, int argb) {
        float rx = r.x * h, ry = r.y * h, rz = r.z * h, ux = u.x * h, uy = u.y * h, uz = u.z * h;
        Vtx.put(b, pose, x - rx - ux, y - ry - uy, z - rz - uz, argb, 0, 1, OverlayTexture.NO_OVERLAY, 0xF000F0, 0, 1, 0);
        Vtx.put(b, pose, x + rx - ux, y + ry - uy, z + rz - uz, argb, 1, 1, OverlayTexture.NO_OVERLAY, 0xF000F0, 0, 1, 0);
        Vtx.put(b, pose, x + rx + ux, y + ry + uy, z + rz + uz, argb, 1, 0, OverlayTexture.NO_OVERLAY, 0xF000F0, 0, 1, 0);
        Vtx.put(b, pose, x - rx + ux, y - ry + uy, z - rz + uz, argb, 0, 0, OverlayTexture.NO_OVERLAY, 0xF000F0, 0, 1, 0);
    }

    private static int lerpColor(int a, int b, double t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255);
        int g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
        int bl = (int) Mth.lerp(t, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }

    /** Tests: how many effects are being drawn. */
    public static int activeCount() {
        return EFFECTS.size();
    }
}
