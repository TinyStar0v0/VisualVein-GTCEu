package com.visualprospecting.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * World-level persistent store for all discovered ore veins, grouped by dimension.
 */
public final class VeinWorldData extends SavedData {

    public static final String DATA_NAME = "visualprospecting_veins";
    private static final int DATA_VERSION = 2;
    /** A GTCEu vein is at most three chunks wide in every direction from its centre. */
    private static final int VEIN_CHUNK_RADIUS = 3;

    private final Map<ResourceLocation, DimensionVeins> dimensions = new HashMap<>();

    public static VeinWorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(VeinWorldData::load, VeinWorldData::new, DATA_NAME);
    }

    public DimensionVeins dimension(ResourceLocation dimension) {
        return dimensions.computeIfAbsent(dimension, ignored -> new DimensionVeins());
    }

    @Nullable
    public DimensionVeins dimensionOrNull(ResourceLocation dimension) {
        return dimensions.get(dimension);
    }

    /**
     * Inserts a vein if it is new. If it already exists, only the mined counter is merged; the world-gen
     * definition is deterministic, so the stored metadata does not need to be replaced.
     *
     * @return true when a new vein was added
     */
    public boolean addOrUpdate(ResourceLocation dimension, VeinRecord record) {
        DimensionVeins veins = dimension(dimension);
        VeinRecord existing = veins.byKey.get(record.key());
        if (existing != null) {
            if (record.mined > existing.mined) {
                existing.mined = record.mined;
                setDirty();
            }
            return false;
        }
        veins.add(record);
        setDirty();
        return true;
    }

    /**
     * Marks a vein manually. Returns the record when it changed, or null when it was already marked.
     */
    @Nullable
    public VeinRecord mark(ResourceLocation dimension, String key, String source) {
        DimensionVeins veins = dimensions.get(dimension);
        if (veins == null) {
            return null;
        }
        VeinRecord record = veins.get(key);
        if (record == null || !record.mark(source)) {
            return null;
        }
        setDirty();
        return record;
    }

    @Nullable
    public VeinRecord setDepleted(ResourceLocation dimension, String key, boolean depleted) {
        DimensionVeins veins = dimensions.get(dimension);
        if (veins == null) {
            return null;
        }
        VeinRecord record = veins.get(key);
        if (record == null) {
            return null;
        }
        record.depleted = depleted;
        setDirty();
        return record;
    }

    public int clear(ResourceLocation dimension) {
        DimensionVeins removed = dimensions.remove(dimension);
        if (removed == null) {
            return 0;
        }
        setDirty();
        return removed.byKey.size();
    }

    public void clearAll() {
        dimensions.clear();
        setDirty();
    }

    public Map<ResourceLocation, DimensionVeins> dimensions() {
        return Collections.unmodifiableMap(dimensions);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("version", DATA_VERSION);
        CompoundTag dims = new CompoundTag();
        for (Map.Entry<ResourceLocation, DimensionVeins> entry : dimensions.entrySet()) {
            ListTag veins = new ListTag();
            for (VeinRecord record : entry.getValue().byKey.values()) {
                veins.add(record.save());
            }
            CompoundTag dimTag = new CompoundTag();
            dimTag.put("veins", veins);
            dims.put(entry.getKey().toString(), dimTag);
        }
        tag.put("dimensions", dims);
        return tag;
    }

    public static VeinWorldData load(CompoundTag tag) {
        VeinWorldData data = new VeinWorldData();
        CompoundTag dims = tag.getCompound("dimensions");
        for (String key : dims.getAllKeys()) {
            ResourceLocation dimension = ResourceLocation.tryParse(key);
            if (dimension == null) {
                continue;
            }
            DimensionVeins veins = data.dimension(dimension);
            ListTag list = dims.getCompound(key).getList("veins", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                veins.add(VeinRecord.load(list.getCompound(i)));
            }
        }
        return data;
    }

    public static final class DimensionVeins {

        private final Map<String, VeinRecord> byKey = new LinkedHashMap<>();
        /** Chunk -> veins whose centre can reach that chunk. Used for cheap mining attribution. */
        private final Map<Long, List<VeinRecord>> byChunk = new HashMap<>();

        private void add(VeinRecord record) {
            byKey.put(record.key(), record);
            int centerChunkX = record.centerX >> 4;
            int centerChunkZ = record.centerZ >> 4;
            for (int dx = -VEIN_CHUNK_RADIUS; dx <= VEIN_CHUNK_RADIUS; dx++) {
                for (int dz = -VEIN_CHUNK_RADIUS; dz <= VEIN_CHUNK_RADIUS; dz++) {
                    long key = ChunkPos.asLong(centerChunkX + dx, centerChunkZ + dz);
                    byChunk.computeIfAbsent(key, ignored -> new ArrayList<>()).add(record);
                }
            }
        }

        public Collection<VeinRecord> all() {
            return Collections.unmodifiableCollection(byKey.values());
        }

        public int size() {
            return byKey.size();
        }

        @Nullable
        public VeinRecord get(String key) {
            return byKey.get(key);
        }

        /**
         * Finds the nearest recorded vein that contains the given ore block at the given position.
         */
        @Nullable
        public VeinRecord findVeinForOre(BlockPos pos, String oreId, String materialId) {
            List<VeinRecord> candidates = byChunk.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
            if (candidates == null) {
                return null;
            }
            VeinRecord best = null;
            double bestDistance = Double.MAX_VALUE;
            for (VeinRecord candidate : candidates) {
                boolean contains = false;
                for (OreEntry ore : candidate.ores) {
                    if (ore.id.equals(oreId)
                            || (!materialId.isEmpty() && materialId.equals(ore.materialId))) {
                        contains = true;
                        break;
                    }
                }
                if (!contains) {
                    continue;
                }
                double dx = pos.getX() - candidate.centerX;
                double dy = pos.getY() - candidate.centerY;
                double dz = pos.getZ() - candidate.centerZ;
                double distance = dx * dx + dy * dy + dz * dz;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = candidate;
                }
            }
            return best;
        }

        /**
         * Finds the nearest vein that contains a material, used by surface-rock and prospector marking.
         */
        @Nullable
        public VeinRecord nearestWithMaterial(BlockPos pos, String materialId, double maxDistance) {
            VeinRecord best = null;
            double bestDistance = maxDistance * maxDistance;
            for (VeinRecord record : byKey.values()) {
                boolean contains = false;
                for (OreEntry ore : record.ores) {
                    if (!materialId.isEmpty() && (materialId.equals(ore.materialId)
                            || ore.id.equals("material:" + materialId))) {
                        contains = true;
                        break;
                    }
                }
                if (!contains) {
                    continue;
                }
                double dx = pos.getX() - record.centerX;
                double dz = pos.getZ() - record.centerZ;
                double distance = dx * dx + dz * dz;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = record;
                }
            }
            return best;
        }

        @Nullable
        public VeinRecord nearest(double x, double z) {
            VeinRecord best = null;
            double bestDistance = Double.MAX_VALUE;
            for (VeinRecord record : byKey.values()) {
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
