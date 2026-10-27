package io.github.foredayner.aibnf.client;

import io.github.foredayner.aibnf.AiBuildAssistantMod;
import io.github.foredayner.aibnf.ai.build.BuildPlan;
import io.github.foredayner.aibnf.ai.build.GearItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.MapCodec;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws an AI equipment item as its 3D model: every part is a small box with its block's
 * texture. The model's grip point (0, 0, 0) sits where a vanilla sword's handle is and the
 * model, which points up (+Y), is tilted like a sword sprite, so the game's hand-held
 * positions (first and third person) work for it. In inventories it is scaled to fit a slot.
 */
public final class GearModelRenderer implements SpecialModelRenderer<GearModelRenderer.Model> {

    public static final ResourceLocation TYPE_ID = ResourceLocation.fromNamespaceAndPath(AiBuildAssistantMod.MOD_ID, "gear_model");

    /** A decoded model: parts with their textures, and its outline after the sword tilt. */
    public record Model(List<BuildPlan.Part> parts, List<TextureAtlasSprite> sprites, float[] tilted, boolean worn,
                        Map<Integer, List<BuildPlan.Anim>> anims, BuildPlan.Aura aura, float reach, boolean bow, Model arrow,
                        float length) {
    }

    /** First person, long weapons: extra tilt (degrees) and how far up the shaft it looks held (per block). */
    static final float FIRST_PERSON_LONG_TILT = 30.0F;
    static final float FIRST_PERSON_LONG_SLIDE = 0.5F;

    /** Hand-held models at least this long (blocks) are held with both hands in third person. */
    public static final float TWO_HANDED_LENGTH = 2.0F;

    /**
     * Scale of a hand-held model. Up to vanilla size it is drawn like before (a 1.45 sword about as
     * big as a vanilla one); longer ones grow toward their real size in third person (a 3-block
     * greatsword is 3 blocks), and in first person they are kept short enough to fit the view.
     */
    static float handScale(float length, boolean firstPerson) {
        if (firstPerson) {
            return HAND_SCALE * Math.min(1.0F, 1.8F / Math.max(0.1F, length));
        }
        float t = Math.max(0, Math.min(1, (length - 1.6F) / 1.4F));
        return HAND_SCALE + t * (1.0F / 0.85F - HAND_SCALE);
    }

    /** True when the main-hand stack is a long AI weapon (held with both hands in third person). */
    public static boolean twoHanded(ItemStack stack) {
        if (!(stack.getItem() instanceof io.github.foredayner.aibnf.content.GearItem gear) || gear.kind().worn()
            || gear.kind() == io.github.foredayner.aibnf.content.GearKind.BOW) {
            return false;
        }
        Model model = modelFor(stack);
        return model != null && model.length() >= TWO_HANDED_LENGTH;
    }

    /**
     * How far (blocks) the biggest AI equipment this entity holds or wears reaches from it, or 0.
     * Used to keep big equipment (a balloon above the head) from vanishing when the wearer's own
     * outline is off screen.
     */
    public static float reachOf(net.minecraft.world.entity.LivingEntity entity) {
        float reach = 0;
        for (net.minecraft.world.entity.EquipmentSlot slot : REACH_SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.getItem() instanceof io.github.foredayner.aibnf.content.GearItem) {
                Model model = modelFor(stack);
                if (model != null) {
                    reach = Math.max(reach, model.reach());
                }
            }
        }
        return reach;
    }

    private static final net.minecraft.world.entity.EquipmentSlot[] REACH_SLOTS = {
        net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
        net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.entity.EquipmentSlot.OFFHAND };

    public record Unbaked() implements SpecialModelRenderer.Unbaked {
        public static final MapCodec<io.github.foredayner.aibnf.client.GearModelRenderer.Unbaked> CODEC = MapCodec.unit(new io.github.foredayner.aibnf.client.GearModelRenderer.Unbaked());

        @Override
        public SpecialModelRenderer<?> bake(EntityModelSet entityModels) {
            return new GearModelRenderer();
        }

        @Override
        public MapCodec<? extends SpecialModelRenderer.Unbaked> type() {
            return CODEC;
        }
    }

    private static final float GRIP_X = 0.2F;
    private static final float GRIP_Y = 0.2F;
    private static final float TILT = (float) Math.toRadians(-45);
    private static final float HAND_SCALE = 0.8F;
    /** The grip of the vanilla bow sprite (its limbs run along the same diagonal as a sword's blade). */
    private static final float BOW_GRIP_X = 0.31F;
    private static final float BOW_GRIP_Y = 0.69F;

    /** Decoded models by item data, so the gzip and JSON are read once per stack, not every frame. */
    private static final Map<Integer, Map.Entry<WeakReference<CustomData>, Model>> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Map.Entry<WeakReference<CustomData>, Model>> eldest) {
            return size() > 64;
        }
    };

    @Override
    public Model extractArgument(ItemStack stack) {
        return modelFor(stack);
    }

    /** The decoded model on a gear stack (cached), or null. */
    public static Model modelFor(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        int key = System.identityHashCode(data);
        Map.Entry<WeakReference<CustomData>, Model> hit = CACHE.get(key);
        if (hit != null && hit.getKey().get() == data) {
            return hit.getValue();
        }
        BuildPlan plan = GearItems.modelOf(stack);
        boolean worn = stack.getItem() instanceof io.github.foredayner.aibnf.content.GearItem gear && gear.kind().worn();
        boolean bow = stack.getItem() instanceof io.github.foredayner.aibnf.content.GearItem gear2 && gear2.kind() == io.github.foredayner.aibnf.content.GearKind.BOW;
        Model model = plan == null || plan.parts.isEmpty() ? null : decode(plan, worn, bow);
        CACHE.put(key, Map.entry(new WeakReference<>(data), model == null ? EMPTY : model));
        return model;
    }

    private static final Model EMPTY = new Model(List.of(), List.of(), new float[4], false, Map.of(), null, 0, false, null, 0);

    private static Model decode(BuildPlan plan, boolean worn, boolean bow) {
        // A bow's arrow pieces are not part of the bow: they are the shape of the arrows it shoots.
        List<BuildPlan.Part> arrowParts = new ArrayList<>();
        List<BuildPlan.Part> own = new ArrayList<>();
        for (BuildPlan.Part p : plan.parts) {
            (p.group == BuildPlan.ARROW_GROUP ? arrowParts : own).add(p);
        }
        Model arrow = null;
        if (bow && !arrowParts.isEmpty()) {
            List<TextureAtlasSprite> arrowSprites = new ArrayList<>();
            float reach = 0;
            for (BuildPlan.Part p : arrowParts) {
                arrowSprites.add(spriteOf(p.block));
                reach = (float) Math.max(reach, p.y + p.sy / 2);
            }
            arrow = new Model(arrowParts, arrowSprites, new float[4], false, Map.of(), null, reach, false, null, reach);
        }
        if (own.isEmpty()) {
            own = arrowParts; // only an arrow: show it
        }
        plan = copyWithParts(plan, own);
        List<TextureAtlasSprite> sprites = new ArrayList<>();
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float tilt = worn ? 0 : TILT;
        float c = (float) Math.cos(tilt), s = (float) Math.sin(tilt);
        float reach = 0;
        float lowY = Float.MAX_VALUE, highY = -Float.MAX_VALUE;
        for (BuildPlan.Part p : plan.parts) {
            lowY = (float) Math.min(lowY, p.y - p.sy / 2);
            highY = (float) Math.max(highY, p.y + p.sy / 2);
            sprites.add(spriteOf(p.block));
            double r = Math.sqrt(p.sx * p.sx + p.sy * p.sy + p.sz * p.sz) / 2;
            reach = (float) Math.max(reach, Math.sqrt(p.x * p.x + p.y * p.y + p.z * p.z) + r);
            for (int i = 0; i < 4; i++) {
                float x = (float) (p.x + ((i & 1) == 0 ? -r : r));
                float y = (float) (p.y + ((i & 2) == 0 ? -r : r));
                float tx = x * c - y * s, ty = x * s + y * c;
                minX = Math.min(minX, tx); maxX = Math.max(maxX, tx);
                minY = Math.min(minY, ty); maxY = Math.max(maxY, ty);
            }
        }
        Map<Integer, List<BuildPlan.Anim>> anims = new java.util.HashMap<>();
        if (plan.anims != null) {
            for (BuildPlan.Anim a : plan.anims) {
                anims.computeIfAbsent(a.group, k -> new ArrayList<>()).add(a);
            }
        }
        return new Model(plan.parts, sprites, new float[] { minX, minY, maxX, maxY }, worn, anims, plan.aura, reach, bow, arrow,
            plan.parts.isEmpty() ? 0 : highY - lowY);
    }

    private static BuildPlan copyWithParts(BuildPlan plan, List<BuildPlan.Part> parts) {
        BuildPlan copy = new BuildPlan();
        copy.parts = parts;
        copy.anims = plan.anims;
        copy.aura = plan.aura;
        return copy;
    }

    private static TextureAtlasSprite spriteOf(String block) {
        String id = block.contains("[") ? block.substring(0, block.indexOf('[')) : block;
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        Block b = BuiltInRegistries.BLOCK.getValue(parsed == null ? ResourceLocation.withDefaultNamespace("stone") : parsed);
        BlockState state = b.defaultBlockState();
        return Minecraft.getInstance().getModelManager().getBlockModelShaper().getParticleIcon(state);
    }

    @Override
    public void render(Model model, ItemDisplayContext mode, PoseStack matrices, MultiBufferSource vertexConsumers,
                       int light, int overlay, boolean glint) {
        if (model == null || model.parts().isEmpty() || mode == ItemDisplayContext.HEAD) {
            return; // worn equipment on the head is drawn by WearRenderer
        }
        matrices.pushPose();
        if (model.worn()) {
            // Helmets, armour and back items as an item: upright, fitted (a little smaller in the hand).
            float[] t = model.tilted();
            float size = Math.max(t[2] - t[0], t[3] - t[1]);
            boolean slot = mode == ItemDisplayContext.GUI || mode == ItemDisplayContext.FIXED;
            float scale = (slot ? 0.95F : 0.6F) / Math.max(0.2F, size);
            matrices.translate(0.5F, 0.5F, 0.5F);
            matrices.scale(scale, scale, scale);
            matrices.translate(-(t[0] + t[2]) / 2, -(t[1] + t[3]) / 2, 0);
            draw(matrices, vertexConsumers.getBuffer(Sheets.cutoutBlockSheet()), model, light, overlay);
            matrices.popPose();
            return;
        }
        if (mode == ItemDisplayContext.GUI || mode == ItemDisplayContext.FIXED) {
            // Fit the tilted model into the slot, centred.
            float[] t = model.tilted();
            float size = Math.max(t[2] - t[0], t[3] - t[1]);
            float scale = 0.95F / Math.max(0.2F, size);
            matrices.translate(0.5F, 0.5F, 0.5F);
            matrices.scale(scale, scale, scale);
            matrices.translate(-(t[0] + t[2]) / 2, -(t[1] + t[3]) / 2, 0);
        } else if (model.bow()) {
            // A bow: its grip where the vanilla bow sprite's grip is, so the bow poses (drawing too) fit it.
            matrices.translate(BOW_GRIP_X, BOW_GRIP_Y, 0.5F);
        } else {
            // In the hand: a 1.45-block AI sword shows about as big as a vanilla sword; big ones grow.
            float scale = handScale(model.length(), mode.firstPerson());
            matrices.translate(GRIP_X, GRIP_Y, 0.5F);
            matrices.scale(scale, scale, scale);
        }
        matrices.mulPose(new Quaternionf().rotateZ(TILT));
        if (mode.firstPerson() && !model.bow() && !model.worn() && model.length() >= TWO_HANDED_LENGTH) {
            // A long weapon in first person: laid a little lower and held further up the shaft (in look only),
            // so its head or blade is in view instead of above the screen.
            matrices.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(FIRST_PERSON_LONG_TILT)));
            matrices.translate(0, -(model.length() - 1.6F) * FIRST_PERSON_LONG_SLIDE, 0);
        }
        draw(matrices, vertexConsumers.getBuffer(Sheets.cutoutBlockSheet()), model, light, overlay);
        if (mode != ItemDisplayContext.GUI && mode != ItemDisplayContext.FIXED) {
            drawAura(matrices, vertexConsumers, model, overlay);
        }
        matrices.popPose();
    }

    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath(AiBuildAssistantMod.MOD_ID, "textures/misc/white.png");

    /**
     * The aura: two see-through, shining shells around every piece (a small and a wider one),
     * slowly breathing, in the aura's colour. Moving parts carry their shell with them.
     */
    static void drawAura(PoseStack matrices, MultiBufferSource vertexConsumers, Model model, int overlay) {
        BuildPlan.Aura aura = model.aura();
        if (aura == null || model.parts().isEmpty()) {
            return;
        }
        VertexConsumer buffer = vertexConsumers.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucentEmissive(WHITE));
        double seconds = seconds();
        double breathe = 0.8 + 0.2 * Math.sin(2 * Math.PI * 0.6 * seconds);
        double[][] shells = { { 0.025, 0.30 }, { 0.07, 0.13 } };
        for (double[] shell : shells) {
            int alpha = (int) Math.round(255 * Math.min(0.6, shell[1] * aura.strength * breathe));
            int argb = (alpha << 24) | (aura.color & 0xFFFFFF);
            for (BuildPlan.Part part : model.parts()) {
                List<BuildPlan.Anim> anims = part.group == 0 ? null : model.anims().get(part.group);
                matrices.pushPose();
                if (anims != null) {
                    for (BuildPlan.Anim a : anims) {
                        animate(matrices, a, seconds, 0);
                    }
                }
                shellBox(matrices, buffer, part, (float) shell[0], argb, overlay);
                matrices.popPose();
            }
        }
    }

    private static void shellBox(PoseStack matrices, VertexConsumer buffer, BuildPlan.Part p, float margin, int argb, int overlay) {
        matrices.pushPose();
        matrices.translate((float) p.x, (float) p.y, (float) p.z);
        matrices.mulPose(new Quaternionf()
            .rotateY((float) Math.toRadians(p.yaw))
            .rotateX((float) Math.toRadians(p.pitch))
            .rotateZ((float) Math.toRadians(p.roll)));
        PoseStack.Pose e = matrices.last();
        float hx = (float) p.sx / 2 + margin, hy = (float) p.sy / 2 + margin, hz = (float) p.sz / 2 + margin;
        Vector3f[] corner = new Vector3f[8];
        for (int i = 0; i < 8; i++) {
            corner[i] = new Vector3f((i & 1) == 0 ? -hx : hx, (i & 2) == 0 ? -hy : hy, (i & 4) == 0 ? -hz : hz);
        }
        for (int[] f : FACES) {
            for (int k = 0; k < 4; k++) {
                buffer.addVertex(e, corner[f[k]]).setColor(argb).setUv(0.5F, 0.5F).setOverlay(overlay).setLight(0xF000F0)
                    .setNormal(e, f[4], f[5], f[6]);
            }
        }
        matrices.popPose();
    }

    /** Every part of the model as a textured box, in the current matrix, moving parts at this moment. */
    static void draw(PoseStack matrices, VertexConsumer buffer, Model model, int light, int overlay) {
        double seconds = seconds();
        for (int i = 0; i < model.parts().size(); i++) {
            BuildPlan.Part part = model.parts().get(i);
            List<BuildPlan.Anim> anims = part.group == 0 ? null : model.anims().get(part.group);
            if (anims == null) {
                box(matrices, buffer, part, model.sprites().get(i), light, overlay);
                continue;
            }
            matrices.pushPose();
            int partLight = light;
            for (BuildPlan.Anim a : anims) {
                partLight = animate(matrices, a, seconds, partLight);
            }
            box(matrices, buffer, part, model.sprites().get(i), partLight, overlay);
            matrices.popPose();
        }
    }

    /** Game time in seconds (smooth between ticks), so every player sees the same movement. */
    private static double seconds() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return System.currentTimeMillis() / 1000.0;
        }
        return (client.level.getGameTime() + client.getDeltaTracker().getGameTimeDeltaPartialTick(true)) / 20.0;
    }

    /** Apply one movement to the matrix; returns the light to use (glow = full brightness). */
    private static int animate(PoseStack m, BuildPlan.Anim a, double t, int light) {
        if ("glow".equals(a.type)) {
            return 0xF000F0;
        }
        double wave = Math.sin(2 * Math.PI * a.speed * t);
        org.joml.Vector3f axis = switch (a.axis == null ? "y" : a.axis) {
            case "x" -> new org.joml.Vector3f(1, 0, 0);
            case "z" -> new org.joml.Vector3f(0, 0, 1);
            default -> new org.joml.Vector3f(0, 1, 0);
        };
        float px = (float) a.px, py = (float) a.py, pz = (float) a.pz;
        switch (a.type) {
            case "spin" -> {
                double turns = (a.speed * t) % 1.0;
                m.translate(px, py, pz);
                m.mulPose(new Quaternionf().rotateAxis((float) (turns * 2 * Math.PI), axis));
                m.translate(-px, -py, -pz);
            }
            case "swing" -> {
                m.translate(px, py, pz);
                m.mulPose(new Quaternionf().rotateAxis((float) Math.toRadians(a.amount * wave), axis));
                m.translate(-px, -py, -pz);
            }
            case "bob" -> m.translate(axis.x * (float) (a.amount * wave), axis.y * (float) (a.amount * wave),
                axis.z * (float) (a.amount * wave));
            case "pulse" -> {
                float s = (float) (1 + a.amount * wave);
                m.translate(px, py, pz);
                m.scale(s, s, s);
                m.translate(-px, -py, -pz);
            }
            default -> {
            }
        }
        return light;
    }

    private static final int[][] FACES = {
        // corner indices (bit 0 = +x, bit 1 = +y, bit 2 = +z) for each face, counter-clockwise from outside, and the normal
        { 4, 5, 7, 6, 0, 0, 1 },   // +z
        { 1, 0, 2, 3, 0, 0, -1 },  // -z
        { 5, 1, 3, 7, 1, 0, 0 },   // +x
        { 0, 4, 6, 2, -1, 0, 0 },  // -x
        { 6, 7, 3, 2, 0, 1, 0 },   // +y
        { 0, 1, 5, 4, 0, -1, 0 },  // -y
    };

    private static void box(PoseStack matrices, VertexConsumer buffer, BuildPlan.Part p, TextureAtlasSprite sprite, int light, int overlay) {
        matrices.pushPose();
        matrices.translate((float) p.x, (float) p.y, (float) p.z);
        matrices.mulPose(new Quaternionf()
            .rotateY((float) Math.toRadians(p.yaw))
            .rotateX((float) Math.toRadians(p.pitch))
            .rotateZ((float) Math.toRadians(p.roll)));
        PoseStack.Pose e = matrices.last();
        float hx = (float) p.sx / 2, hy = (float) p.sy / 2, hz = (float) p.sz / 2;
        Vector3f[] corner = new Vector3f[8];
        for (int i = 0; i < 8; i++) {
            corner[i] = new Vector3f((i & 1) == 0 ? -hx : hx, (i & 2) == 0 ? -hy : hy, (i & 4) == 0 ? -hz : hz);
        }
        for (int[] f : FACES) {
            // Texture: one block's texture per block of size, cut to the face (bigger faces stretch).
            float w, h;
            if (f[6] != 0) { w = (float) p.sx; h = (float) p.sy; }
            else if (f[4] != 0) { w = (float) p.sz; h = (float) p.sy; }
            else { w = (float) p.sx; h = (float) p.sz; }
            float u0 = sprite.getU0(), v0 = sprite.getV0();
            float u1 = u0 + (sprite.getU1() - u0) * Math.min(1, w);
            float v1 = v0 + (sprite.getV1() - v0) * Math.min(1, h);
            float[][] uv = { { u0, v1 }, { u1, v1 }, { u1, v0 }, { u0, v0 } };
            for (int k = 0; k < 4; k++) {
                buffer.addVertex(e, corner[f[k]]).setColor(0xFFFFFFFF).setUv(uv[k][0], uv[k][1]).setOverlay(overlay).setLight(light)
                    .setNormal(e, f[4], f[5], f[6]);
            }
        }
        matrices.popPose();
    }
}
