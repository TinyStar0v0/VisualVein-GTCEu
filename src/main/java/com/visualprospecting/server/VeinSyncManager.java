package com.visualprospecting.server;

import com.visualprospecting.VisualProspecting;
import com.visualprospecting.config.VPConfig;
import com.visualprospecting.data.VeinRecord;
import com.visualprospecting.data.VeinWorldData;
import com.visualprospecting.network.VPNetwork;
import com.visualprospecting.network.VeinClearPacket;
import com.visualprospecting.network.VeinSyncPacket;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Batches changed veins and sends them to the clients in the affected dimension.
 */
@Mod.EventBusSubscriber(modid = VisualProspecting.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VeinSyncManager {

    private static final int BATCH_SIZE = 200;
    private static final Map<ResourceLocation, Set<String>> DIRTY = new HashMap<>();
    private static int tickCounter;

    private VeinSyncManager() {
    }

    public static void markDirty(ResourceLocation dimension, Collection<VeinRecord> records) {
        Set<String> keys = DIRTY.computeIfAbsent(dimension, ignored -> new HashSet<>());
        for (VeinRecord record : records) {
            keys.add(record.key());
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++tickCounter < VPConfig.COMMON.syncIntervalTicks().get()) {
            return;
        }
        tickCounter = 0;
        if (DIRTY.isEmpty()) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            DIRTY.clear();
            return;
        }
        VeinWorldData data = VeinWorldData.get(server);
        Iterator<Map.Entry<ResourceLocation, Set<String>>> iterator = DIRTY.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ResourceLocation, Set<String>> entry = iterator.next();
            ResourceLocation dimensionId = entry.getKey();
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
            VeinWorldData.DimensionVeins dimension = data.dimensionOrNull(dimensionId);
            if (level == null || dimension == null) {
                iterator.remove();
                continue;
            }
            List<VeinRecord> records = new ArrayList<>(entry.getValue().size());
            for (String key : entry.getValue()) {
                VeinRecord record = dimension.get(key);
                if (record != null) {
                    records.add(record);
                }
            }
            if (!records.isEmpty()) {
                VPNetwork.sendToDimension(level.dimension(), new VeinSyncPacket(dimensionId, false, records));
            }
            iterator.remove();
        }
    }

    public static void sendFull(ServerPlayer player) {
        sendFull(player, player.serverLevel().dimension().location());
    }

    /**
     * Sends a complete snapshot for a specific dimension. Used on login and on dimension changes,
     * where the player's level may not have been swapped yet when the event fires.
     */
    public static void sendFull(ServerPlayer player, ResourceLocation dimensionId) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        VeinWorldData data = VeinWorldData.get(server);
        VeinWorldData.DimensionVeins dimension = data.dimensionOrNull(dimensionId);
        List<VeinRecord> records = dimension == null ? List.of() : List.copyOf(dimension.all());

        VPNetwork.sendToPlayer(player, new VeinClearPacket(dimensionId));
        for (int start = 0; start < records.size(); start += BATCH_SIZE) {
            int end = Math.min(records.size(), start + BATCH_SIZE);
            VPNetwork.sendToPlayer(player, new VeinSyncPacket(dimensionId, false, records.subList(start, end)));
        }
    }

    public static void sendClear(ResourceLocation dimensionId) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
        if (level != null) {
            VPNetwork.sendToDimension(level.dimension(), new VeinClearPacket(dimensionId));
        }
    }

    public static void sendClearAll(MinecraftServer server) {
        for (ResourceLocation dimensionId : VeinWorldData.get(server).dimensions().keySet()) {
            sendClear(dimensionId);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        DIRTY.clear();
        tickCounter = 0;
        VeinScanner.clearQueue();
    }
}
