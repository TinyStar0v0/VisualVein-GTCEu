package com.visualvein.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * JourneyMap waypoints should be reachable. A vein's centerY is often underground, so use the
 * client-side surface height when the map is showing the current dimension.
 */
public final class WaypointSurface {

    private WaypointSurface() {
    }

    public static int y(ResourceKey<Level> dimension, int x, int z, int fallback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.level.dimension().equals(dimension)) {
            return fallback;
        }
        int surface = minecraft.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return surface > minecraft.level.getMinBuildHeight() ? surface : fallback;
    }
}
