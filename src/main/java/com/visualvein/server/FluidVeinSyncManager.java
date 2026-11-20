package com.visualvein.server;

import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.BedrockFluidDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.BedrockFluidVeinSavedData;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.FluidVeinWorldEntry;
import com.visualvein.data.FluidDiscoveryData;
import com.visualvein.data.FluidVeinRecord;
import com.visualvein.network.FluidSyncPacket;
import com.visualvein.network.VPNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.material.Fluid;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Server-side fluid vein discovery. GTCEu owns the real fluid data; this class only exposes regions
 * the player has actually discovered by using a fluid-mode prospector or right-clicking bedrock
 * with an empty hand. Opening the map or logging in never reveals undiscovered fields.
 */
public final class FluidVeinSyncManager {

    private static final int REGION_CHUNKS = BedrockFluidVeinSavedData.VEIN_CHUNK_SIZE;

    private FluidVeinSyncManager() {
    }

    public static void sendFull(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        VPNetwork.sendToPlayer(player, new FluidSyncPacket(
                level.dimension().location(), true, discovered(level)));
    }

    public static List<FluidVeinRecord> discovered(ServerLevel level) {
        ResourceLocation dimension = level.dimension().location();
        FluidDiscoveryData data = FluidDiscoveryData.get(level.getServer());
        List<FluidVeinRecord> records = new ArrayList<>();
        for (String key : data.get(dimension)) {
            String[] parts = key.split(":");
            if (parts.length != 2) {
                continue;
            }
            try {
                FluidVeinRecord record = recordAt(level, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                if (record != null) {
                    records.add(record);
                }
            } catch (NumberFormatException ignored) {
                // Skip malformed keys from older/hand-edited data.
            }
        }
        return records;
    }

    /**
     * Used by a GTCEu prospector in FLUID mode. Adds every fluid region in range that actually has a
     * field; returns the number of newly discovered regions.
     */
    public static int discoverAround(ServerLevel level, ServerPlayer player, int radiusChunks) {
        ResourceLocation dimension = level.dimension().location();
        FluidDiscoveryData data = FluidDiscoveryData.get(level.getServer());
        int minRegionX = Math.floorDiv(player.chunkPosition().x - radiusChunks, REGION_CHUNKS);
        int maxRegionX = Math.floorDiv(player.chunkPosition().x + radiusChunks, REGION_CHUNKS);
        int minRegionZ = Math.floorDiv(player.chunkPosition().z - radiusChunks, REGION_CHUNKS);
        int maxRegionZ = Math.floorDiv(player.chunkPosition().z + radiusChunks, REGION_CHUNKS);
        int found = 0;
        for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
            for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                if (recordAt(level, regionX, regionZ) != null && data.discover(dimension, regionX, regionZ)) {
                    found++;
                }
            }
        }
        if (found > 0) {
            sendFull(player);
            player.sendSystemMessage(Component.translatable("vp.msg.fluid_discovered", found));
        } else {
            player.sendSystemMessage(Component.translatable("vp.msg.fluid_none"));
        }
        return found;
    }

    /**
     * Empty-hand right-click on bedrock: reveal the fluid region covering that block.
     */
    public static int discoverAt(ServerLevel level, ServerPlayer player, BlockPos pos) {
        int regionX = Math.floorDiv(pos.getX() >> 4, REGION_CHUNKS);
        int regionZ = Math.floorDiv(pos.getZ() >> 4, REGION_CHUNKS);
        if (recordAt(level, regionX, regionZ) == null) {
            player.sendSystemMessage(Component.translatable("vp.msg.fluid_bedrock_none"));
            return 0;
        }
        boolean added = FluidDiscoveryData.get(level.getServer())
                .discover(level.dimension().location(), regionX, regionZ);
        if (added) {
            sendFull(player);
            player.sendSystemMessage(Component.translatable("vp.msg.fluid_discovered", 1));
            return 1;
        }
        player.sendSystemMessage(Component.translatable("vp.msg.fluid_already"));
        return 0;
    }

    private static FluidVeinRecord recordAt(ServerLevel level, int regionX, int regionZ) {
        int chunkX = regionX * REGION_CHUNKS + REGION_CHUNKS / 2;
        int chunkZ = regionZ * REGION_CHUNKS + REGION_CHUNKS / 2;
        BedrockFluidVeinSavedData data = BedrockFluidVeinSavedData.getOrCreate(level);
        FluidVeinWorldEntry entry = data.getFluidVeinWorldEntry(chunkX, chunkZ);
        if (entry == null || entry.getDefinition() == null) {
            return null;
        }
        BedrockFluidDefinition definition = entry.getDefinition();
        Fluid fluid = definition.getStoredFluid().get();
        if (fluid == null) {
            return null;
        }
        ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluid);
        if (fluidId == null) {
            return null;
        }
        return new FluidVeinRecord(regionX, regionZ,
                chunkX * 16 + 8, chunkZ * 16 + 8,
                fluidId, entry.getFluidYield(), definition.getDepletedYield(),
                entry.getOperationsRemaining(), BedrockFluidVeinSavedData.MAXIMUM_VEIN_OPERATIONS);
    }
}
