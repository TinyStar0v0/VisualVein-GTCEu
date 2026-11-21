package com.visualvein.client;

import com.visualvein.data.SurfacePoint;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

/**
 * JourneyMap waypoints should be reachable. A vein's centerY is often underground, so use the
 * client-side surface height when the map is showing the current dimension.
 */
public final class WaypointSurface {

    private WaypointSurface() {
    }

    public static int y(ResourceKey<Level> dimension, int x, int z, int fallback) {
        return y(dimension, x, z, fallback, 0, List.of());
    }

    public static int y(ResourceKey<Level> dimension, int x, int z, int fallback,
                        int recordedSurfaceY, List<SurfacePoint> surfacePoints) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.level.dimension().equals(dimension)) {
            return recordedSurfaceY != 0 && recordedSurfaceY > -1000 ? recordedSurfaceY : fallback;
        }
        int minY = minecraft.level.getMinBuildHeight();
        if (recordedSurfaceY != 0 && recordedSurfaceY > minY) {
            return recordedSurfaceY;
        }
        if (minecraft.level.hasChunkAt(new BlockPos(x, minY, z))) {
            int surface = minecraft.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (surface > minY) {
                return surface;
            }
        }
        // The fullscreen map can show explored chunks that are no longer loaded. The server
        // records an actual surface rock position when it finds one; use the nearest one.
        int nearestY = Integer.MIN_VALUE;
        double best = Double.MAX_VALUE;
        for (SurfacePoint point : surfacePoints) {
            double dx = point.x() - x;
            double dz = point.z() - z;
            double distance = dx * dx + dz * dz;
            if (point.y() > minY && distance < best) {
                best = distance;
                nearestY = point.y();
            }
        }
        return nearestY > minY ? nearestY : fallback;
    }
}
