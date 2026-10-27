package com.example.ai.build;

import com.example.content.AibContent;
import com.example.content.GearKind;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/**
 * Equipment as items: the stack carries the whole model (gzipped parts), so it looks right on any
 * client, in any world, even after it is deleted from 보관함.
 */
public final class GearItems {

    public static final String KEY = "aib_gear";
    public static final String MODEL = "aib_model";
    /** Largest model a gear item may carry (gzipped bytes). */
    public static final int MAX_MODEL_BYTES = 60_000;

    private GearItems() {
    }

    public static GearKind kindOf(BuildRecord record) {
        return GearKind.of(record.gear);
    }

    /** Only the parts, gzipped: what the item's renderer needs. */
    public static byte[] modelBytes(BuildPlan plan) {
        BuildRecord model = new BuildRecord();
        model.kind = "gear";
        model.plan = new BuildPlan();
        model.plan.facing = plan.facing;
        model.plan.parts = new java.util.ArrayList<>(plan.parts);
        model.plan.anims = plan.anims == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(plan.anims);
        model.plan.aura = plan.aura;
        return model.toGzip();
    }

    public static ItemStack stackFor(BuildRecord record) {
        GearKind kind = kindOf(record);
        ItemStack stack = new ItemStack(AibContent.GEAR.get(kind));
        CompoundTag nbt = new CompoundTag();
        nbt.putString(KEY, record.id);
        byte[] model = modelBytes(record.plan);
        if (model.length <= MAX_MODEL_BYTES) {
            nbt.putByteArray(MODEL, model);
        }
        GearEffects.write(nbt, record.plan.fx);
        String motion = com.example.content.GearMotions.normalize(record.plan.motion);
        if (!motion.isEmpty()) {
            nbt.putString(com.example.content.GearMotions.KEY, motion);
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(RebuildService.title(record))
            .setStyle(Style.EMPTY.withItalic(false).withColor(ChatFormatting.AQUA)));
        Style lore = Style.EMPTY.withItalic(false).withColor(ChatFormatting.GRAY);
        stack.set(DataComponents.LORE, new ItemLore(List.of(
            Component.literal("AI 장비 · " + kind.korean + " (철 " + kind.korean + " 성능)").setStyle(lore))));
        return stack;
    }

    /** Decode the model on a gear stack, or null. */
    public static BuildPlan modelOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        byte[] bytes = data.copyTag().getByteArray(MODEL);
        if (bytes.length == 0) {
            return null;
        }
        try {
            return BuildRecord.fromGzip(bytes).plan;
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
