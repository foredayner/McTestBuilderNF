package io.github.foredayner.aibnf.content;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/**
 * Item data through one small API (shared with the 1.20.1 port, which keeps it in NBT):
 * 1.21 keeps a stack's extras in data components (custom data, name, lore, glint).
 */
public final class ItemData {

    private ItemData() {
    }

    /** A copy of the mod's data on a stack, or null if it has none. */
    public static CompoundTag get(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
    }

    /** The custom data object itself (for caches keyed by it), or null. */
    public static CustomData raw(ItemStack stack) {
        return stack == null ? null : stack.get(DataComponents.CUSTOM_DATA);
    }

    /** Adds every key of {@code data} to the stack's custom data. */
    public static void put(ItemStack stack, CompoundTag data) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.merge(data));
    }

    public static void setName(ItemStack stack, Component name) {
        stack.set(DataComponents.CUSTOM_NAME, name);
    }

    public static void setLore(ItemStack stack, List<Component> lines) {
        stack.set(DataComponents.LORE, new ItemLore(lines));
    }

    public static void setGlint(ItemStack stack) {
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
    }
}
