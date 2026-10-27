package com.example.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public final class BlockIconHelper {

    private static List<String> pickableBlockIds;

    private BlockIconHelper() {
    }

    public static List<String> getPickableBlockIds() {
        if (pickableBlockIds == null) {
            pickableBlockIds = buildPickableBlockIds();
        }
        return pickableBlockIds;
    }

    public static ItemStack createStack(String blockId) {
        Block block = resolveBlock(blockId);
        if (block == null || block == Blocks.AIR) {
            return ItemStack.EMPTY;
        }

        Item item = block.asItem();
        if (item == Items.AIR) {
            return new ItemStack(block);
        }

        return new ItemStack(item);
    }

    public static String shortName(String blockId) {
        int colon = blockId.indexOf(':');
        return colon >= 0 ? blockId.substring(colon + 1) : blockId;
    }

    public static void drawBlockIcon(GuiGraphics drawContext, String blockId, int x, int y) {
        ItemStack stack = createStack(blockId);
        if (stack.isEmpty()) {
            return;
        }

        drawContext.renderItem(stack, x, y);
    }

    public static void drawBlockTooltip(
        GuiGraphics drawContext,
        String blockId,
        int x,
        int y
    ) {
        ItemStack stack = createStack(blockId);
        if (stack.isEmpty()) {
            return;
        }

        drawContext.renderTooltip(Minecraft.getInstance().font, stack, x, y);
    }

    private static List<String> buildPickableBlockIds() {
        List<String> ids = new ArrayList<>();

        for (Block block : BuiltInRegistries.BLOCK) {
            if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR) {
                continue;
            }

            String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();
            if (!createStack(blockId).isEmpty()) {
                ids.add(blockId);
            }
        }

        ids.sort(Comparator.naturalOrder());
        return List.copyOf(ids);
    }

    private static Block resolveBlock(String blockId) {
        String normalized = blockId == null ? null : blockId.trim().toLowerCase();
        if (normalized == null || normalized.isEmpty()) {
            return null;
        }

        if (!normalized.contains(":")) {
            normalized = "minecraft:" + normalized;
        }

        try {
            return BuiltInRegistries.BLOCK.getValue(ResourceLocation.parse(normalized));
        } catch (Exception exception) {
            return null;
        }
    }
}
