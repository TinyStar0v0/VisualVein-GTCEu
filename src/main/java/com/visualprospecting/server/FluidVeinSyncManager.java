package com.visualprospecting.server;

import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.BedrockFluidDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.BedrockFluidVeinSavedData;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.FluidVeinWorldEntry;
import com.visualprospecting.VisualProspecting;
import com.visualprospecting.data.FluidVeinRecord;
import com.visualprospecting.network.FluidSyncPacket;
import com.visualprospecting.network.VPNetwork;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;

/**
 * Syncs GTCEu's bedrock fluid veins (oil, natural gas, ...) to clients. GTCEu already persists the
 * authoritative fluid data; we only read it and mirror an 8x8-chunk region snapshot.
 */
@Mod.EventBusSubscriber(modid = VisualProspecting.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FluidVeinSyncManager {

    private static final int REGION_CHUNKS = BedrockFluidVeinSavedData.VEIN_CHUNK_SIZE;
    private static int tickCounter;

    private FluidVeinSyncManager() {
    }

    public static void sendFull(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        List<FluidVeinRecord> records = scan(level, player.chunkPosition(), 8);
        VPNetwork.sendToPlayer(player, new FluidSyncPacket(level.dimension().location(), true, records));
    }

    public static List<FluidVeinRecord> scan(ServerLevel level, ChunkPos center, int radiusChunks) {
        BedrockFluidVeinSavedData data = BedrockFluidVeinSavedData.getOrCreate(level);
        int minRegionX = Math.floorDiv(center.x - radiusChunks, REGION_CHUNKS);
        int maxRegionX = Math.floorDiv(center.x + radiusChunks, REGION_CHUNKS);
        int minRegionZ = Math.floorDiv(center.z - radiusChunks, REGION_CHUNKS);
        int maxRegionZ = Math.floorDiv(center.z + radiusChunks, REGION_CHUNKS);
        List<FluidVeinRecord> result = new ArrayList<>();
        for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
            for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                int chunkX = regionX * REGION_CHUNKS + REGION_CHUNKS / 2;
                int chunkZ = regionZ * REGION_CHUNKS + REGION_CHUNKS / 2;
                FluidVeinWorldEntry entry = data.getFluidVeinWorldEntry(chunkX, chunkZ);
                if (entry == null || entry.getDefinition() == null) {
                    continue;
                }
                BedrockFluidDefinition definition = entry.getDefinition();
                Fluid fluid = definition.getStoredFluid().get();
                if (fluid == null) {
                    continue;
                }
                ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluid);
                if (fluidId == null) {
                    continue;
                }
                result.add(new FluidVeinRecord(regionX, regionZ,
                        chunkX * 16 + 8, chunkZ * 16 + 8,
                        fluidId, entry.getFluidYield(), definition.getDepletedYield(),
                        entry.getOperationsRemaining(), BedrockFluidVeinSavedData.MAXIMUM_VEIN_OPERATIONS));
            }
        }
        return result;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++tickCounter < 600) {
            return;
        }
        tickCounter = 0;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendFull(player);
        }
    }
}
