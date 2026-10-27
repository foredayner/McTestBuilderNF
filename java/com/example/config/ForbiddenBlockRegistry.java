package com.example.config;

import com.example.AiBuildAssistantMod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class ForbiddenBlockRegistry {

    private static Set<String> forbiddenBlocks = Set.of();

    private ForbiddenBlockRegistry() {
    }

    public static void reload(String pathString) {
        reloadFromList(loadFromFile(pathString));
    }

    public static void reloadFromList(List<String> blocks) {
        forbiddenBlocks = Collections.unmodifiableSet(normalizeAll(blocks));
        AiBuildAssistantMod.LOGGER.info("Loaded {} forbidden block(s)", forbiddenBlocks.size());
    }

    public static List<String> loadFromFile(String pathString) {
        Path path = Path.of(pathString);
        List<String> loaded = new ArrayList<>();

        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent() == null ? Path.of(".") : path.getParent());
                List<String> defaults = defaultBlocks();
                Files.write(path, formatFileLines(defaults), StandardCharsets.UTF_8);
                return defaults;
            }

            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String normalized = normalizeBlockId(line);
                if (normalized != null) {
                    loaded.add(normalized);
                }
            }
        } catch (IOException exception) {
            AiBuildAssistantMod.LOGGER.error("Failed to load forbidden blocks from {}", pathString, exception);
        }

        if (loaded.isEmpty()) {
            return defaultBlocks();
        }

        return sortUnique(loaded);
    }

    public static void writeToFile(String pathString, List<String> blocks) {
        Path path = Path.of(pathString);

        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.write(path, formatFileLines(sortUnique(blocks)), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            AiBuildAssistantMod.LOGGER.error("Failed to write forbidden blocks to {}", pathString, exception);
        }
    }

    public static List<String> decodeFromNetwork(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return defaultBlocks();
        }

        List<String> blocks = new ArrayList<>();
        for (String line : encoded.split("\n")) {
            String normalized = normalizeBlockId(line);
            if (normalized != null) {
                blocks.add(normalized);
            }
        }

        return normalizeList(blocks);
    }

    public static String encodeForNetwork(List<String> blocks) {
        return String.join("\n", normalizeList(blocks));
    }

    public static List<String> normalizeList(List<String> blocks) {
        return sortUnique(blocks == null ? List.of() : blocks);
    }

    public static List<String> getBlocks() {
        return sortUnique(new ArrayList<>(forbiddenBlocks));
    }

    public static List<String> defaultBlocks() {
        return List.of("minecraft:bedrock", "minecraft:barrier");
    }

    public static boolean isForbidden(String blockName) {
        String normalized = normalizeBlockId(blockName);
        return normalized != null && forbiddenBlocks.contains(normalized);
    }

    public static String normalizeBlockId(String blockName) {
        if (blockName == null) {
            return null;
        }

        String trimmed = blockName.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return null;
        }

        if (!trimmed.contains(":")) {
            trimmed = "minecraft:" + trimmed;
        }

        return trimmed.toLowerCase();
    }

    private static Set<String> normalizeAll(List<String> blocks) {
        Set<String> normalized = new HashSet<>();
        if (blocks == null) {
            return normalized;
        }

        for (String block : blocks) {
            String normalizedBlock = normalizeBlockId(block);
            if (normalizedBlock != null) {
                normalized.add(normalizedBlock);
            }
        }
        return normalized;
    }

    private static List<String> sortUnique(List<String> blocks) {
        TreeSet<String> sorted = new TreeSet<>();
        for (String block : blocks) {
            String normalized = normalizeBlockId(block);
            if (normalized != null) {
                sorted.add(normalized);
            }
        }
        return new ArrayList<>(sorted);
    }

    private static List<String> formatFileLines(List<String> blocks) {
        List<String> lines = new ArrayList<>();
        lines.add("# One block id per line. Example:");
        lines.addAll(sortUnique(blocks));
        return lines;
    }
}
