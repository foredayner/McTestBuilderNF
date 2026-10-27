package com.example.content;

import com.example.AiBuildAssistantMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;

/** Things this mod adds to the game: the solid object hitbox and the object item. */
public final class AibContent {

    public static final ResourceLocation HITBOX_ID = ResourceLocation.fromNamespaceAndPath(AiBuildAssistantMod.MOD_ID, "hitbox");
    public static final ResourceLocation OBJECT_ID = ResourceLocation.fromNamespaceAndPath(AiBuildAssistantMod.MOD_ID, "object");

    public static EntityType<SolidHitboxEntity> HITBOX;
    public static final ResourceLocation ARROW_ID = ResourceLocation.fromNamespaceAndPath(AiBuildAssistantMod.MOD_ID, "arrow");
    /** Arrows shot from AI bows. */
    public static EntityType<AibArrowEntity> ARROW;
    public static Item OBJECT_ITEM;
    /** One item per equipment kind (so tags like #minecraft:swords and enchantments work). */
    public static final java.util.Map<GearKind, Item> GEAR = new java.util.EnumMap<>(GearKind.class);

    private AibContent() {
    }

    private static <T> void copy(Item.Properties settings, Item from, net.minecraft.core.component.DataComponentType<T> type) {
        T value = from.components().get(type);
        if (value != null) {
            settings.component(type, value);
        }
    }

    public static void register() {
        ResourceKey<EntityType<?>> entityKey = ResourceKey.create(Registries.ENTITY_TYPE, HITBOX_ID);
        HITBOX = Registry.register(BuiltInRegistries.ENTITY_TYPE, entityKey,
            EntityType.Builder.<SolidHitboxEntity>of(SolidHitboxEntity::new, MobCategory.MISC)
                .sized(0.0F, 0.0F)
                .clientTrackingRange(10)
                .build(entityKey));

        ResourceKey<EntityType<?>> arrowKey = ResourceKey.create(Registries.ENTITY_TYPE, ARROW_ID);
        ARROW = Registry.register(BuiltInRegistries.ENTITY_TYPE, arrowKey,
            EntityType.Builder.<AibArrowEntity>of(AibArrowEntity::new, MobCategory.MISC)
                .sized(0.5F, 0.5F)
                .eyeHeight(0.13F)
                .clientTrackingRange(4)
                .updateInterval(20)
                .build(arrowKey));

        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, OBJECT_ID);
        OBJECT_ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey, new ObjectItem(new Item.Properties().setId(itemKey)));

        for (GearKind kind : GearKind.values()) {
            ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(AiBuildAssistantMod.MOD_ID, "gear_" + kind.id()));
            Item.Properties settings = new Item.Properties().setId(key).stacksTo(1);
            copy(settings, kind.base, net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS);
            copy(settings, kind.base, net.minecraft.core.component.DataComponents.TOOL);
            copy(settings, kind.base, net.minecraft.core.component.DataComponents.MAX_DAMAGE);
            copy(settings, kind.base, net.minecraft.core.component.DataComponents.DAMAGE);
            copy(settings, kind.base, net.minecraft.core.component.DataComponents.ENCHANTABLE);
            copy(settings, kind.base, net.minecraft.core.component.DataComponents.REPAIRABLE);
            copy(settings, kind.base, net.minecraft.core.component.DataComponents.EQUIPPABLE); // worn kinds only
            Item item = kind == GearKind.BOW ? new GearBowItem(settings) : new GearToolItem(kind, settings);
            GEAR.put(kind, Registry.register(BuiltInRegistries.ITEM, key, item));
        }
    }
}
