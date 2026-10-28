package io.github.foredayner.aibnf.content;

import io.github.foredayner.aibnf.AiBuildAssistantMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Things this mod adds to the game: the solid object hitbox, the arrow, the object item and the gear items. */
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

    /** NeoForge hands out registries at a fixed point while the game loads; register then. */
    public static void register(IEventBus modBus) {
        modBus.addListener(AibContent::onRegister);
    }

    private static void onRegister(RegisterEvent event) {
        event.register(Registries.ENTITY_TYPE, helper -> {
            HITBOX = EntityType.Builder.<SolidHitboxEntity>of(SolidHitboxEntity::new, MobCategory.MISC)
                .sized(0.0F, 0.0F)
                .clientTrackingRange(10)
                .build(HITBOX_ID.toString());
            helper.register(HITBOX_ID, HITBOX);

            ARROW = EntityType.Builder.<AibArrowEntity>of(AibArrowEntity::new, MobCategory.MISC)
                .sized(0.5F, 0.5F)
                .eyeHeight(0.13F)
                .clientTrackingRange(4)
                .updateInterval(20)
                .build(ARROW_ID.toString());
            helper.register(ARROW_ID, ARROW);
        });

        event.register(Registries.ITEM, helper -> {
            OBJECT_ITEM = new ObjectItem(new Item.Properties());
            helper.register(OBJECT_ID, OBJECT_ITEM);

            for (GearKind kind : GearKind.values()) {
                Item.Properties settings = new Item.Properties().stacksTo(1);
                // The vanilla item's stats ride on data components, as in 1.21.4 (enchanting and
                // repairing are decided by item tags and the item code here: 1.21.1 has no
                // enchantable / repairable / equippable components yet).
                copy(settings, kind.base, net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS);
                copy(settings, kind.base, net.minecraft.core.component.DataComponents.TOOL);
                copy(settings, kind.base, net.minecraft.core.component.DataComponents.MAX_DAMAGE);
                copy(settings, kind.base, net.minecraft.core.component.DataComponents.DAMAGE);
                Item item = kind == GearKind.BOW ? new GearBowItem(settings)
                    : kind.worn() ? new GearWornItem(kind, settings) : new GearToolItem(kind, settings);
                GEAR.put(kind, item);
                helper.register(ResourceLocation.fromNamespaceAndPath(AiBuildAssistantMod.MOD_ID, "gear_" + kind.id()), item);
            }
        });
    }
}
