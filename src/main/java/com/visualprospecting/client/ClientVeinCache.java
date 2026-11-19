package com.visualprospecting.client;

import com.visualprospecting.data.VeinRecord;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-side copy of the server's vein data. Updated by {@code VeinSyncPacket}.
 */
public final class ClientVeinCache {

    private static final Map<ResourceLocation, DimensionData> DATA = new HashMap<>();
    private static long globalVersion;

    private ClientVeinCache() {
    }

    public static void apply(ResourceLocation dimension, Collection<VeinRecord> records, boolean replace) {
        DimensionData data = DATA.computeIfAbsent(dimension, ignored -> new DimensionData());
        boolean changed = replace;
        if (replace) {
            data.veins.clear();
            data.structureVersion++;
        }
        for (VeinRecord record : records) {
            VeinRecord old = data.veins.put(record.key(), record.copy());
            if (old == null) {
                data.structureVersion++;
                changed = true;
            } else if (old.mined != record.mined
                    || old.depleted != record.depleted
                    || old.marked != record.marked
                    || !old.markSource.equals(record.markSource)
                    || !old.ores.equals(record.ores)
                    || !old.surfaceOres.equals(record.surfaceOres)
                    || !old.surfacePoints.equals(record.surfacePoints)) {
                changed = true;
            }
        }
        if (changed) {
            data.version++;
            globalVersion++;
            ClientHooks.dataChanged.run();
        }
    }

    public static void clear(ResourceLocation dimension) {
        DimensionData removed = DATA.remove(dimension);
        if (removed != null) {
            globalVersion++;
            ClientHooks.dataChanged.run();
        }
    }

    public static void clearAll() {
        if (!DATA.isEmpty()) {
            DATA.clear();
            globalVersion++;
            ClientHooks.dataChanged.run();
        }
    }

    @Nullable
    public static DimensionData get(ResourceLocation dimension) {
        return DATA.get(dimension);
    }

    public static long version() {
        return globalVersion;
    }

    public static final class DimensionData {

        private final Map<String, VeinRecord> veins = new LinkedHashMap<>();
        private int structureVersion;
        private int version;

        public Collection<VeinRecord> all() {
            return Collections.unmodifiableCollection(veins.values());
        }

        @Nullable
        public VeinRecord get(String key) {
            return veins.get(key);
        }

        public int size() {
            return veins.size();
        }

        public int structureVersion() {
            return structureVersion;
        }

        public int version() {
            return version;
        }

        @Nullable
        public VeinRecord nearest(double x, double z) {
            VeinRecord best = null;
            double bestDistance = Double.MAX_VALUE;
            for (VeinRecord record : veins.values()) {
                double dx = x - record.centerX;
                double dz = z - record.centerZ;
                double distance = dx * dx + dz * dz;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = record;
                }
            }
            return best;
        }
    }
}
