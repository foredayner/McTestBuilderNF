package io.github.foredayner.aibnf.content;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Item data in 1.20.1 terms. 1.21 keeps a stack's extras in data components (custom data,
 * name, lore, glint); 1.20.1 keeps all of them in the stack's NBT tag.
 */
public final class ItemData {

    /** Set on blueprint papers so they shine like enchanted items (see the items' isFoil). */
    public static final String GLINT = "aib_glint";

    private ItemData() {
    }

    /** The mod's data on a stack (the stack's own tag, not a copy), or null if it has none. */
    public static CompoundTag get(ItemStack stack) {
        return stack == null ? null : stack.getTag();
    }

    /** Adds every key of {@code data} to the stack's tag. */
    public static void put(ItemStack stack, CompoundTag data) {
        stack.getOrCreateTag().merge(data);
    }

    public static void setName(ItemStack stack, Component name) {
        stack.setHoverName(name);
    }

    public static void setLore(ItemStack stack, List<Component> lines) {
        ListTag lore = new ListTag();
        for (Component line : lines) {
            lore.add(StringTag.valueOf(Component.Serializer.toJson(line)));
        }
        stack.getOrCreateTagElement(ItemStack.TAG_DISPLAY).put(ItemStack.TAG_LORE, lore);
    }

    /**
     * Makes a vanilla item (blueprint paper) shine like an enchanted one. 1.20.1 has no glint
     * switch, so the stack gets one empty enchantment entry: it shows the glint, but names no
     * enchantment, so nothing is listed or applied.
     */
    public static void setGlint(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean(GLINT, true);
        if (!stack.isEnchanted()) {
            ListTag list = new ListTag();
            list.add(new CompoundTag());
            tag.put(ItemStack.TAG_ENCH, list);
        }
    }

    public static boolean hasGlint(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(GLINT);
    }
}
