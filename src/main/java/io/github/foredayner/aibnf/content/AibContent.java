package io.github.foredayner.aibnf.content;

import io.github.foredayner.aibnf.AiBuildAssistantMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/** Things this mod adds to the game: the solid object hitbox, the arrow, the object item and the gear items. */
public final class AibContent {

    public static final ResourceLocation HITBOX_ID = new ResourceLocation(AiBuildAssistantMod.MOD_ID, "hitbox");
    public static final ResourceLocation OBJECT_ID = new ResourceLocation(AiBuildAssistantMod.MOD_ID, "object");

    public static EntityType<SolidHitboxEntity> HITBOX;
    public static final ResourceLocation ARROW_ID = new ResourceLocation(AiBuildAssistantMod.MOD_ID, "arrow");
    /** Arrows shot from AI bows. */
    public static EntityType<AibArrowEntity> ARROW;
    public static Item OBJECT_ITEM;
    /** One item per equipment kind (so tags like #minecraft:swords and enchantments work). */
    public static final java.util.Map<GearKind, Item> GEAR = new java.util.EnumMap<>(GearKind.class);

    private AibContent() {
    }

    /** Forge hands out registries at a fixed point while the game loads; register then. */
    public static void register(IEventBus modBus) {
        modBus.addListener(AibContent::onRegister);
    }

    private static void onRegister(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.ENTITY_TYPES, helper -> {
            HITBOX = EntityType.Builder.<SolidHitboxEntity>of(SolidHitboxEntity::new, MobCategory.MISC)
                .sized(0.0F, 0.0F)
                .clientTrackingRange(10)
                .build(HITBOX_ID.toString());
            helper.register(HITBOX_ID, HITBOX);

            ARROW = EntityType.Builder.<AibArrowEntity>of(AibArrowEntity::new, MobCategory.MISC)
                .sized(0.5F, 0.5F)
                .clientTrackingRange(4)
                .updateInterval(20)
                .build(ARROW_ID.toString());
            helper.register(ARROW_ID, ARROW);
        });

        event.register(ForgeRegistries.Keys.ITEMS, helper -> {
            OBJECT_ITEM = new ObjectItem(new Item.Properties());
            helper.register(OBJECT_ID, OBJECT_ITEM);

            for (GearKind kind : GearKind.values()) {
                Item.Properties settings = new Item.Properties().stacksTo(1);
                if (kind.base.getMaxDamage() > 0) {
                    settings.durability(kind.base.getMaxDamage());
                }
                Item item = kind == GearKind.BOW ? new GearBowItem(settings)
                    : kind.worn() ? new GearWornItem(kind, settings) : new GearToolItem(kind, settings);
                GEAR.put(kind, item);
                helper.register(new ResourceLocation(AiBuildAssistantMod.MOD_ID, "gear_" + kind.id()), item);
            }
        });
    }
}
