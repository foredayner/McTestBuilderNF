package io.github.foredayner.aibnf.ai.build;

import io.github.foredayner.aibnf.ai.api.MinecraftAI;
import io.github.foredayner.aibnf.network.AiNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Blueprints: a glowing paper item that carries a saved build. The build itself is kept in the
 * world folder (ai_builder/blueprints/<id>.json) and the paper only holds its id, so items stay
 * small and the blueprint keeps working after a restart.
 */
public final class BlueprintStore {

    public static final String KEY = "aib_blueprint";
    private static final Pattern ID = Pattern.compile("^[0-9a-z_]{1,40}$");

    private BlueprintStore() {
    }

    private static Path dir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("ai_builder").resolve("blueprints");
    }

    /** The blueprint id on a paper, or null for any other item. */
    public static String idOf(ItemStack stack) {
        if (stack == null || !stack.is(Items.PAPER)) {
            return null;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag nbt = data.copyTag();
        if (!nbt.contains(KEY)) {
            return null;
        }
        String id = nbt.getString(KEY);
        return ID.matcher(id).matches() ? id : null;
    }

    public static BuildRecord load(MinecraftServer server, String id) {
        if (id == null || !ID.matcher(id).matches()) {
            return null;
        }
        try {
            return BuildRecord.fromJson(Files.readString(dir(server).resolve(id + ".json"), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    /**
     * "손에 받기": save the design with the world and hand it over: an object becomes an object
     * item, a building a blueprint paper.
     */
    public static void give(ServerPlayer player, BuildRecord record) {
        String id = record.id.replaceAll("[^0-9a-z_]", "");
        if (!ID.matcher(id).matches()) {
            AiNetworking.sendLog(player, "[History] 설계도를 만들 수 없어요 (잘못된 id).");
            return;
        }
        record.id = id;
        try {
            Path dir = dir(player.getServer());
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(id + ".json"), record.toJson(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            AiNetworking.sendLog(player, "[History] 설계도를 저장하지 못했어요: " + exception.getMessage());
            return;
        }
        if ("gear".equals(record.kind) && record.plan.isObject()) {
            player.getInventory().placeItemBackInInventory(GearItems.stackFor(record));
            AiNetworking.sendLog(player, "[History] " + GearItems.kindOf(record).korean
                + " 장비를 받았어요. 철 " + GearItems.kindOf(record).korean + "과 같은 성능이에요.");
            return;
        }
        if (record.plan.isObject() && record.plan.ops.isEmpty()) {
            player.getInventory().placeItemBackInInventory(ObjectItems.stackFor(record));
            AiNetworking.sendLog(player, "[History] 소품 아이템을 받았어요. 블록 윗면을 우클릭하면 그 위에 놓이고, 돌처럼 캐면 다시 아이템이 돼요.");
            return;
        }
        player.getInventory().placeItemBackInInventory(paperFor(record));
        AiNetworking.sendLog(player, "[History] 설계도를 받았어요. 블록 윗면을 우클릭하면 그 위에 건물 앞면 가운데가 오도록 지어집니다.");
    }

    public static ItemStack paperFor(BuildRecord record) {
        ItemStack paper = new ItemStack(Items.PAPER);
        CompoundTag nbt = new CompoundTag();
        nbt.putString(KEY, record.id);
        paper.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
        boolean object = "object".equals(record.kind);
        String title = RebuildService.title(record);
        paper.set(DataComponents.CUSTOM_NAME, Component.literal((object ? "소품 설계도: " : "건물 설계도: ") + title)
            .setStyle(Style.EMPTY.withItalic(false).withColor(ChatFormatting.GOLD)));
        int[] b = record.plan.bounds();
        Style lore = Style.EMPTY.withItalic(false).withColor(ChatFormatting.GRAY);
        paper.set(DataComponents.LORE, new ItemLore(List.of(
            Component.literal("크기 " + (b[3] - b[0] + 1) + " × " + (b[4] - b[1] + 1) + " × " + (b[5] - b[2] + 1)).setStyle(lore),
            Component.literal("블록 윗면을 우클릭하면 그 위에").setStyle(lore),
            Component.literal("앞면 가운데를 맞춰 지어집니다").setStyle(lore))));
        paper.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return paper;
    }

    /**
     * Right-click with a blueprint on the top of {@code clicked}: the building's front edge,
     * centred, lands on the block above it. True if handled.
     */
    public static boolean use(ServerPlayer player, ItemStack stack, net.minecraft.core.BlockPos clicked,
                              net.minecraft.core.Direction side) {
        String id = idOf(stack);
        if (id == null) {
            return false;
        }
        if (side != net.minecraft.core.Direction.UP) {
            player.displayClientMessage(Component.literal("블록 윗면을 클릭해 주세요").withStyle(ChatFormatting.GRAY), true);
            return true;
        }
        if (!MinecraftAI.canExecuteBuildCommands(player)) {
            AiNetworking.notify(player, "§c설계도를 쓰려면 치트(OP 권한 2)가 필요해요.§r");
            return true;
        }
        BuildRecord record = load(player.getServer(), id);
        if (record == null) {
            AiNetworking.notify(player, "§c이 설계도의 내용을 찾을 수 없어요 (다른 월드에서 받은 설계도일 수 있어요).§r");
            return true;
        }
        if (record.plan.isObject() && record.plan.ops.isEmpty()) {
            RebuildService.placeObjectOn(player, record, clicked); // blueprints made before object items
        } else {
            RebuildService.placeAt(player, record, clicked.above());
        }
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        return true;
    }
}
