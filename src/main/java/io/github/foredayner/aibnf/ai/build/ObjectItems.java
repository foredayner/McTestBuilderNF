package io.github.foredayner.aibnf.ai.build;

import io.github.foredayner.aibnf.content.AibContent;
import io.github.foredayner.aibnf.content.SolidHitboxEntity;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Objects as items: handing one out, setting it down on a block, and mining it back.
 *
 * <p>The object itself is saved with the world (ai_builder/blueprints/<id>.json, like
 * blueprints); the item holds its id and a 16 x 16 picture for the icon. A placed object's
 * hitboxes carry "aibid_<id>" so mining it gives the same item back.
 */
public final class ObjectItems {

    public static final String KEY = "aib_object";
    public static final String ICON = "aib_icon";
    public static final String ID_TAG = "aibid_";
    private static final Pattern ID = Pattern.compile("^[0-9a-z_]{1,40}$");
    /** How far (from the eyes) a player may mine an object. */
    private static final double REACH = 7;

    private ObjectItems() {
    }

    public static ItemStack stackFor(BuildRecord record) {
        ItemStack stack = new ItemStack(AibContent.OBJECT_ITEM);
        CompoundTag nbt = new CompoundTag();
        nbt.putString(KEY, record.id);
        if (record.icon != null && record.icon.length == 16 * 16) {
            nbt.putIntArray(ICON, record.icon);
        }
        io.github.foredayner.aibnf.content.ItemData.put(stack, nbt);
        io.github.foredayner.aibnf.content.ItemData.setName(stack, Component.literal(RebuildService.title(record))
            .setStyle(Style.EMPTY.withItalic(false).withColor(ChatFormatting.WHITE)));
        double[] b = record.plan.partBounds();
        Style lore = Style.EMPTY.withItalic(false).withColor(ChatFormatting.GRAY);
        io.github.foredayner.aibnf.content.ItemData.setLore(stack, (List.of(
            Component.literal(String.format(Locale.ROOT, "AI 소품 · 크기 %.1f × %.1f × %.1f", b[3] - b[0], b[4] - b[1], b[5] - b[2])).setStyle(lore),
            Component.literal("블록 윗면을 우클릭하면 그 위에 놓여요").setStyle(lore),
            Component.literal("돌처럼 캐면 다시 아이템이 돼요").setStyle(lore))));
        return stack;
    }

    /** The saved object id on an object item, or null. */
    public static String idOf(ItemStack stack) {
        if (stack == null || AibContent.OBJECT_ITEM == null || !stack.is(AibContent.OBJECT_ITEM)) {
            return null;
        }
        net.minecraft.nbt.CompoundTag data = io.github.foredayner.aibnf.content.ItemData.get(stack);
        if (data == null) {
            return null;
        }
        String id = data.copy().getString(KEY);
        return ID.matcher(id).matches() ? id : null;
    }

    /** Right-click on the top of {@code clicked}: set the object down there. True if placed. */
    public static boolean use(ServerPlayer player, ItemStack stack, BlockPos clicked) {
        String id = idOf(stack);
        if (id == null) {
            return false;
        }
        BuildRecord record = BlueprintStore.load(player.getServer(), id);
        if (record == null || !record.plan.isObject() || RebuildService.validate(record.plan) != null) {
            player.displayClientMessage(Component.literal("이 소품의 내용을 이 월드에서 찾을 수 없어요").withStyle(ChatFormatting.RED), true);
            return false;
        }
        RebuildService.placeObjectOn(player, record, clicked);
        player.serverLevel().playSound(null, clicked.above(), SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0F, 0.9F);
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        return true;
    }

    /** The saved object a placed hitbox belongs to, or null (objects placed before items existed). */
    public static String savedId(Entity entity) {
        for (String t : entity.getTags()) {
            if (t.startsWith(ID_TAG)) {
                String id = t.substring(ID_TAG.length());
                return ID.matcher(id).matches() ? id : null;
            }
        }
        return null;
    }

    /**
     * The player finished mining an object (the client times it like stone). Removes every part
     * and hitbox and drops the object item, except in creative mode (like blocks).
     */
    public static void breakObject(ServerPlayer player, int entityId) {
        breakObject(player, entityId, !player.isCreative());
    }

    /** drop: whether the item comes out (not in creative). */
    public static void breakObject(ServerPlayer player, int entityId, boolean drop) {
        ServerLevel world = player.serverLevel();
        Entity target = world.getEntity(entityId);
        if (!(target instanceof SolidHitboxEntity) || target.isRemoved() || player.isSpectator()) {
            return;
        }
        if (target.getBoundingBox().distanceToSqr(player.getEyePosition()) > REACH * REACH) {
            return;
        }
        String tag = ObjectService.objectTag(target);
        String id = savedId(target);
        if (tag == null || id == null) {
            player.displayClientMessage(Component.literal("이 소품은 캐서 가져갈 수 없어요 (Delete 키로 지울 수 있어요)").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        List<Entity> all = world.getEntitiesOfClass(Entity.class, target.getBoundingBox().inflate(48),
            e -> e.getTags().contains(tag));
        double x = 0, y = Double.MAX_VALUE, z = 0;
        int n = 0;
        for (Entity e : all) {
            if (e instanceof SolidHitboxEntity) {
                x += e.getX();
                z += e.getZ();
                y = Math.min(y, e.getY());
                n++;
            }
            e.discard();
        }
        if (n == 0) {
            x = target.getX();
            y = target.getY();
            z = target.getZ();
        } else {
            x /= n;
            z /= n;
        }
        world.playSound(null, BlockPos.containing(x, y, z), SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.0F, 0.8F);
        if (!drop) {
            return;
        }
        BuildRecord record = BlueprintStore.load(world.getServer(), id);
        if (record != null) {
            ItemEntity item = new ItemEntity(world, x, y + 0.25, z, stackFor(record));
            item.setDefaultPickUpDelay();
            world.addFreshEntity(item);
        }
    }
}
