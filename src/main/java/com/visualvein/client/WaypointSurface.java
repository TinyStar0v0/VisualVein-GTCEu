package com.visualvein.client;

import com.visualvein.data.SurfacePoint;
import com.visualvein.data.VeinRecord;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

/**
 * Finds a waypoint position that is actually safe to stand in. The vein center is often underground,
 * and an unloaded map region has no usable client heightmap, so repeat this as a small local search:
 * near the vein center first, then slightly farther out, then above the recorded surface, and finally
 * above the build height as a last resort.
 */
public final class WaypointSurface {

    private static final int SEARCH_RADIUS = 10;

    private WaypointSurface() {
    }

    public static int y(ResourceKey<Level> dimension, VeinRecord record) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.level.dimension().equals(dimension)) {
            return recordedOrHigh(record.surfaceY, 0);
        }
        Level level = minecraft.level;
        int minY = level.getMinBuildHeight();
        int high = level.getMaxBuildHeight() - 4;

        // 1. A safe two-block air column near the vein center.
        for (int radius = 0; radius <= SEARCH_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    int x = record.centerX + dx;
                    int z = record.centerZ + dz;
                    if (!level.hasChunkAt(new BlockPos(x, minY, z))) {
                        continue;
                    }
                    int safe = safeColumn(level, x, z);
                    if (safe != Integer.MIN_VALUE) {
                        return safe;
                    }
                }
            }
        }

        // 2. The server-recorded surface height is always above the terrain.
        if (record.surfaceY != 0 && record.surfaceY > minY) {
            return Math.min(high, record.surfaceY + 1);
        }

        // 3. Older records may have a surface rock position but no surfaceY.
        int pointY = nearestSurfacePointY(record.surfacePoints, record.centerX, record.centerZ, minY);
        if (pointY != Integer.MIN_VALUE) {
            return Math.min(high, pointY + 1);
        }

        // 4. Last resort: high above the origin, never inside the ground.
        return high;
    }

    /**
     * Fluid fields have no recorded surfaceY; find a safe column in a small local search.
     */
    public static int y(ResourceKey<Level> dimension, int x, int z, int fallback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.level.dimension().equals(dimension)) {
            return fallback > 0 ? fallback : 64;
        }
        Level level = minecraft.level;
        int minY = level.getMinBuildHeight();
        for (int radius = 0; radius <= 8; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    if (!level.hasChunkAt(new BlockPos(x + dx, minY, z + dz))) {
                        continue;
                    }
                    int safe = safeColumn(level, x + dx, z + dz);
                    if (safe != Integer.MIN_VALUE) {
                        return safe;
                    }
                }
            }
        }
        // The old fallback (often y=64) can be inside a mountain. Use above build height instead.
        return level.getMaxBuildHeight() - 4;
    }

    private static int safeColumn(Level level, int x, int z) {
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (surface <= level.getMinBuildHeight()) {
            return Integer.MIN_VALUE;
        }
        for (int y = surface; y <= surface + 4 && y < level.getMaxBuildHeight() - 1; y++) {
            BlockPos feet = new BlockPos(x, y, z);
            BlockPos head = feet.above();
            BlockState feetState = level.getBlockState(feet);
            BlockState headState = level.getBlockState(head);
            if (!feetState.getFluidState().isEmpty() || !headState.getFluidState().isEmpty()) {
                continue;
            }
            if (!feetState.getCollisionShape(level, feet).isEmpty()
                    || !headState.getCollisionShape(level, head).isEmpty()) {
                continue;
            }
            BlockPos floor = feet.below();
            if (level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()) {
                continue;
            }
            return y;
        }
        return Integer.MIN_VALUE;
    }

    private static int recordedOrHigh(int recordedSurfaceY, int fallback) {
        if (recordedSurfaceY != 0 && recordedSurfaceY > -1000) {
            return recordedSurfaceY + 1;
        }
        return fallback > 0 ? fallback : 64;
    }

    private static int nearestSurfacePointY(List<SurfacePoint> points, int x, int z, int minY) {
        int nearestY = Integer.MIN_VALUE;
        double best = Double.MAX_VALUE;
        for (SurfacePoint point : points) {
            if (point.y() <= minY) {
                continue;
            }
            double dx = point.x() - x;
            double dz = point.z() - z;
            double distance = dx * dx + dz * dz;
            if (distance < best) {
                best = distance;
                nearestY = point.y();
            }
        }
        return nearestY;
    }
}
