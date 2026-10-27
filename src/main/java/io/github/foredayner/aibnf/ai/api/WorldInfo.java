package io.github.foredayner.aibnf.ai.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;


public class WorldInfo {
    public String biome;
    public int groundLevel;
    public String[] nearbyMaterials;
    
    public WorldInfo(ServerLevel world, BlockPos origin, int radius) {
    }
}