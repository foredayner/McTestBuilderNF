package io.github.foredayner.aibnf.ai.api;

import io.github.foredayner.aibnf.config.ForbiddenBlockRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public final class BlockCatalog {

    private static List<String> allBlockIds;

    private BlockCatalog() {
    }

    public static int countAvailable() {
        return (int) getAllBlockIds().stream().filter(BlockCatalog::isAllowedId).count();
    }

    public static boolean isAllowed(String blockName) {
        String normalized = ForbiddenBlockRegistry.normalizeBlockId(blockName);
        return normalized != null && isAllowedId(normalized);
    }

    public static String listPage(int offset, int limit) {
        List<String> available = getAvailableIds();
        if (available.isEmpty()) {
            return "No blocks available";
        }

        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.clamp(limit, 1, 100);
        int end = Math.min(safeOffset + safeLimit, available.size());

        if (safeOffset >= available.size()) {
            return "No blocks in range (total=" + available.size() + ", offset=" + safeOffset + ")";
        }

        String page = available.subList(safeOffset, end).stream()
            .map(BlockCatalog::toShortName)
            .collect(Collectors.joining(", "));

        return "blocks " + safeOffset + "-" + (end - 1) + " of " + available.size() + ": " + page;
    }

    public static String search(String query, int limit) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        int safeLimit = Math.clamp(limit, 1, 100);

        List<String> matches = getAvailableIds().stream()
            .filter(id -> needle.isEmpty() || id.contains(needle) || toShortName(id).contains(needle))
            .limit(safeLimit)
            .map(BlockCatalog::toShortName)
            .collect(Collectors.toList());

        if (matches.isEmpty()) {
            return "No blocks matched '" + query + "'";
        }

        return String.join(", ", matches);
    }

    private static List<String> getAvailableIds() {
        return getAllBlockIds().stream()
            .filter(BlockCatalog::isAllowedId)
            .collect(Collectors.toList());
    }

    private static List<String> getAllBlockIds() {
        if (allBlockIds == null) {
            allBlockIds = buildAllBlockIds();
        }
        return allBlockIds;
    }

    private static List<String> buildAllBlockIds() {
        List<String> ids = new ArrayList<>();

        for (Block block : BuiltInRegistries.BLOCK) {
            if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR) {
                continue;
            }
            ids.add(BuiltInRegistries.BLOCK.getKey(block).toString());
        }

        ids.sort(String::compareTo);
        return List.copyOf(ids);
    }

    private static boolean isAllowedId(String blockId) {
        return blockId != null
            && !blockId.isBlank()
            && !ForbiddenBlockRegistry.isForbidden(blockId);
    }

    private static String toShortName(String blockId) {
        int colon = blockId.indexOf(':');
        return colon >= 0 ? blockId.substring(colon + 1) : blockId;
    }
}
