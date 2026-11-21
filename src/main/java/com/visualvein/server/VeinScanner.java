package com.visualvein.server;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialStack;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.data.worldgen.GTOreDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.ores.GeneratedVeinMetadata;
import com.gregtechceu.gtceu.api.data.worldgen.ores.OreGenerator;
import com.gregtechceu.gtceu.common.block.SurfaceRockBlock;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.mojang.datafixers.util.Either;
import com.visualvein.VisualVein;
import com.visualvein.config.VPConfig;
import com.visualvein.data.OreEntry;
import com.visualvein.data.SurfacePoint;
import com.visualvein.data.VeinRecord;
import com.visualvein.data.VeinWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Turns GTCEu's deterministic vein metadata into persistent {@link VeinRecord}s.
 *
 * <p>GTCEu decides veins in origin chunks (every {@code oreVeinGridSize} chunks). We simply ask its
 * {@link OreGenerator} for the metadata of a newly loaded chunk; for non-origin chunks this is a cheap
 * modulo check and returns an empty list. For origin chunks it returns the same data the world generator
 * used, including the exact centre Y and vein definition.</p>
 */
@Mod.EventBusSubscriber(modid = VisualVein.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VeinScanner {

    private static final Logger LOGGER = LoggerFactory.getLogger("VisualVein/Scanner");
    private static final OreGenerator GENERATOR = new OreGenerator();
    private static final Queue<ScanTask> QUEUE = new ConcurrentLinkedQueue<>();
    private static final Set<String> QUEUED = ConcurrentHashMap.newKeySet();
    private static int scannedChunks;
    private static int metadataHits;
    private static int discoveredVeins;

    private VeinScanner() {
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!VPConfig.COMMON.enableTracking().get()) {
            return;
        }
        if (event.getLevel() instanceof ServerLevel level) {
            queue(level, event.getChunk().getPos());
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        int budget = VPConfig.COMMON.scanChunksPerTick().get();
        for (int i = 0; i < budget; i++) {
            ScanTask task = QUEUE.poll();
            if (task == null) {
                return;
            }
            QUEUED.remove(task.key);
            try {
                scan(task.level, task.pos, task.source);
            } catch (Throwable throwable) {
                LOGGER.debug("Failed to scan GTCEu veins in {} at {}", task.level.dimension().location(), task.pos, throwable);
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!VPConfig.COMMON.trackMining().get()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer player = event.getPlayer() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        if (player == null) {
            return;
        }
        BlockState state = event.getState();
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (blockId == null) {
            return;
        }
        VeinWorldData data = VeinWorldData.get(level.getServer());
        VeinWorldData.DimensionVeins dimension = data.dimensionOrNull(level.dimension().location());
        if (dimension == null) {
            return;
        }
        MaterialStack stack = ChemicalHelper.getMaterial(state.getBlock());
        String materialId = stack == null ? "" : stack.material().getName();
        VeinRecord vein = dimension.findVeinForOre(event.getPos(), blockId.toString(), materialId);
        if (vein == null) {
            scanRegion(level, new ChunkPos(event.getPos().getX() >> 4, event.getPos().getZ() >> 4), 2, "auto");
            vein = dimension.findVeinForOre(event.getPos(), blockId.toString(), materialId);
        }
        if (vein == null) {
            return;
        }
        vein.mined++;
        data.setDirty();
        VeinSyncManager.markDirty(level.dimension().location(), List.of(vein));
    }

    public static void clearQueue() {
        QUEUE.clear();
        QUEUED.clear();
    }

    public static String debugStats() {
        return "queued=" + QUEUE.size() + " scanned=" + scannedChunks
                + " metadataHits=" + metadataHits + " discovered=" + discoveredVeins;
    }

    private static void scan(ServerLevel level, ChunkPos pos, String source) {
        if (!VPConfig.COMMON.enableTracking().get()) {
            return;
        }
        scannedChunks++;
        List<GeneratedVeinMetadata> metadata = GENERATOR.generateMetadata(
                level, level.getChunkSource().getGenerator(), pos);
        if (metadata.isEmpty()) {
            return;
        }
        metadataHits++;
        VeinWorldData data = VeinWorldData.get(level.getServer());
        ResourceLocation dimension = level.dimension().location();
        List<VeinRecord> changed = new ArrayList<>();
        for (GeneratedVeinMetadata meta : metadata) {
            VeinRecord record = createRecord(level, meta);
            if (record == null) {
                continue;
            }
            VeinRecord existing = data.dimension(dimension).get(record.key());
            boolean added = data.addOrUpdate(dimension, record);
            VeinRecord stored = data.dimension(dimension).get(record.key());
            if (stored == null) {
                continue;
            }
            attachSurfaceOres(level, stored);
            if (added) {
                discoveredVeins++;
            }
            if (!"auto".equals(source) && stored.mark(source)) {
                changed.add(stored);
            } else if (added) {
                changed.add(stored);
            }
            if (existing != null && !stored.surfaceOres.isEmpty() && existing.surfaceOres.isEmpty()) {
                if (!changed.contains(stored)) {
                    changed.add(stored);
                }
            }
        }
        if (!changed.isEmpty()) {
            VeinSyncManager.markDirty(dimension, changed);
            LOGGER.debug("Discovered/marked {} GTCEu vein(s) in {}", changed.size(), dimension);
        }
    }

    /**
     * Scans every chunk in a square synchronously. Used by the prospector, surface-rock interaction
     * and {@code /vp scan}; the radius is intentionally small (chunks), and non-origin chunks are cheap.
     */
    public static int scanRegion(ServerLevel level, ChunkPos center, int radiusChunks, String source) {
        int before = 0;
        VeinWorldData data = VeinWorldData.get(level.getServer());
        VeinWorldData.DimensionVeins dimension = data.dimensionOrNull(level.dimension().location());
        if (dimension != null) {
            before = dimension.size();
        }
        int grid = gridSize();
        int minX = Math.floorDiv(center.x - radiusChunks, grid) * grid;
        int maxX = Math.floorDiv(center.x + radiusChunks, grid) * grid;
        int minZ = Math.floorDiv(center.z - radiusChunks, grid) * grid;
        int maxZ = Math.floorDiv(center.z + radiusChunks, grid) * grid;
        for (int x = minX; x <= maxX; x += grid) {
            for (int z = minZ; z <= maxZ; z += grid) {
                scan(level, new ChunkPos(x, z), source);
            }
        }
        VeinWorldData.DimensionVeins after = data.dimensionOrNull(level.dimension().location());
        return after == null ? 0 : Math.max(0, after.size() - before);
    }

    /**
     * Reads GTCEu surface rocks around a vein centre and stores their materials. Surface rocks are
     * the player-visible hint above both stone and deepslate veins.
     */
    private static void attachSurfaceOres(ServerLevel level, VeinRecord record) {
        if (!record.surfaceOres.isEmpty() || record.surfaceScanned) {
            return;
        }
        int radius = 8;
        BlockPos center = new BlockPos(record.centerX, record.centerY, record.centerZ);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                for (int dy = 1; dy >= -2; dy--) {
                    BlockState state = level.getBlockState(new BlockPos(x, surface + dy, z));
                    if (state.getBlock() instanceof SurfaceRockBlock rock) {
                        Material material = rock.getMaterial();
                        if (!containsMaterial(record, material.getName())) {
                            break;
                        }
                        OreEntry ore = new OreEntry("material:" + material.getName(),
                                material.getName(), material.getUnlocalizedName(),
                                material.getMaterialRGB(), 1, true);
                        record.addSurfaceOre(ore);
                        if (record.surfacePoints.size() < 12) {
                            record.surfacePoints.add(new SurfacePoint(x, surface + dy, z, ore));
                        }
                        break;
                    }
                }
            }
        }
        record.surfaceScanned = true;
    }

    private static boolean containsMaterial(VeinRecord record, String materialId) {
        for (OreEntry ore : record.ores) {
            if (materialId.equals(ore.materialId) || ore.id.equals("material:" + materialId)) {
                return true;
            }
        }
        return false;
    }

    public static int gridSize() {
        try {
            return Math.max(1, ConfigHolder.INSTANCE.worldgen.oreVeins.oreVeinGridSize);
        } catch (Throwable ignored) {
            return 3;
        }
    }

    public static void queue(ServerLevel level, ChunkPos pos) {
        queue(level, pos, "auto");
    }

    public static void queue(ServerLevel level, ChunkPos pos, String source) {
        String key = level.dimension().location() + "@" + pos.toLong();
        if (QUEUED.add(key)) {
            QUEUE.add(new ScanTask(level, pos, key, source));
        }
    }

    /**
     * Queues all currently loaded chunks in a square around each player, used by {@code /vp reload}.
     */
    public static int queueAroundPlayers(ServerLevel level, int radiusChunks) {
        int queued = 0;
        for (ServerPlayer player : level.players()) {
            ChunkPos center = player.chunkPosition();
            for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
                for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                    if (level.hasChunk(center.x + dx, center.z + dz)) {
                        queue(level, new ChunkPos(center.x + dx, center.z + dz));
                        queued++;
                    }
                }
            }
        }
        return queued;
    }

    @Nullable
    private static VeinRecord createRecord(ServerLevel level, GeneratedVeinMetadata meta) {
        GTOreDefinition definition = meta.definition();
        if (definition == null || definition.veinGenerator() == null) {
            return null;
        }
        BlockPos center = meta.center();
        List<OreEntry> ores;
        try {
            ores = collectOres(definition);
        } catch (Throwable throwable) {
            LOGGER.debug("Could not read ore composition for vein {}", meta.id(), throwable);
            ores = List.of();
        }
        int clusterSize;
        try {
            clusterSize = definition.clusterSize().getMaxValue();
        } catch (Throwable throwable) {
            clusterSize = 0;
        }
        String layer = "unknown";
        try {
            layer = definition.layer().getSerializedName();
        } catch (Throwable ignored) {
        }
        VeinRecord record = new VeinRecord(
                meta.id(),
                layer,
                meta.originChunk().x,
                meta.originChunk().z,
                center.getX(),
                center.getY(),
                center.getZ(),
                clusterSize,
                definition.density(),
                ores,
                0,
                System.currentTimeMillis()
        );
        try {
            record.surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    center.getX(), center.getZ());
        } catch (Throwable ignored) {
            record.surfaceY = center.getY();
        }
        return record;
    }

    private static List<OreEntry> collectOres(GTOreDefinition definition) {
        Map<String, OreEntry> merged = new LinkedHashMap<>();
        for (Map.Entry<Either<BlockState, Material>, Integer> entry : definition.veinGenerator().getAllEntries()) {
            OreEntry ore = toOreEntry(entry.getKey(), Math.max(0, entry.getValue()));
            if (ore == null) {
                continue;
            }
            OreEntry previous = merged.get(ore.id);
            if (previous == null) {
                merged.put(ore.id, ore);
            } else {
                merged.put(ore.id, previous.withWeight(previous.weight + ore.weight));
            }
        }
        List<OreEntry> result = new ArrayList<>(merged.values());
        result.sort(Comparator.comparingInt((OreEntry ore) -> ore.weight).reversed());
        return result;
    }

    @Nullable
    private static OreEntry toOreEntry(Either<BlockState, Material> either, int weight) {
        if (either.left().isPresent()) {
            BlockState state = either.left().get();
            Block block = state.getBlock();
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (id == null) {
                return null;
            }
            MaterialStack stack = ChemicalHelper.getMaterial(block);
            Material material = stack == null ? null : stack.material();
            int color = material == null ? block.defaultMapColor().col : material.getMaterialRGB();
            String materialId = material == null ? "" : material.getName();
            return new OreEntry(id.toString(), materialId, block.getDescriptionId(), color, weight, material != null);
        }
        if (either.right().isPresent()) {
            Material material = either.right().get();
            Block block = ChemicalHelper.getBlock(TagPrefix.ore, material);
            String id;
            if (block != null) {
                ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
                id = blockId == null ? "material:" + material.getName() : blockId.toString();
            } else {
                id = "material:" + material.getName();
            }
            return new OreEntry(id, material.getName(), material.getUnlocalizedName(),
                    material.getMaterialRGB(), weight, true);
        }
        return null;
    }

    private record ScanTask(ServerLevel level, ChunkPos pos, String key, String source) {
    }
}
