package com.visualvein.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class VPConfig {

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        ForgeConfigSpec.Builder common = new ForgeConfigSpec.Builder();
        common.comment("Server-side ore vein tracking.").push("tracking");
        ForgeConfigSpec.BooleanValue enableTracking = common
                .comment("Record GTCEu ore veins when chunks are generated or loaded.")
                .define("enableTracking", true);
        ForgeConfigSpec.BooleanValue trackMining = common
                .comment("Count ore blocks mined by players and attribute them to the nearest recorded vein.")
                .define("trackMining", true);
        ForgeConfigSpec.IntValue scanChunksPerTick = common
                .comment("Maximum number of newly loaded chunks scanned per server tick.")
                .defineInRange("scanChunksPerTick", 4, 1, 64);
        ForgeConfigSpec.IntValue syncIntervalTicks = common
                .comment("How often (in ticks) incremental vein updates are sent to clients.")
                .defineInRange("syncIntervalTicks", 20, 5, 200);
        common.pop();
        COMMON_SPEC = common.build();
        COMMON = new Common(enableTracking, trackMining, scanChunksPerTick, syncIntervalTicks);

        ForgeConfigSpec.Builder client = new ForgeConfigSpec.Builder();
        client.comment("JourneyMap overlay options.").push("overlay");
        ForgeConfigSpec.BooleanValue showOverlay = client
                .comment("Show discovered ore veins on the JourneyMap fullscreen map.")
                .define("showOverlay", true);
        ForgeConfigSpec.BooleanValue showOnMinimap = client
                .comment("Also draw vein polygons on the JourneyMap minimap.")
                .define("showOnMinimap", true);
        ForgeConfigSpec.BooleanValue showLabels = client
                .comment("Draw the vein name at its centre on the fullscreen map.")
                .define("showLabels", true);
        ForgeConfigSpec.IntValue labelMinZoom = client
                .comment("Minimum JourneyMap zoom level at which vein labels are drawn.")
                .defineInRange("labelMinZoom", 1, 0, 8);
        ForgeConfigSpec.IntValue maxOverlays = client
                .comment("Maximum number of vein polygons shown at once. Lower this if the map feels slow.")
                .defineInRange("maxOverlays", 300, 20, 2000);
        ForgeConfigSpec.DoubleValue fillOpacity = client
                .comment("Opacity of the coloured vein squares.")
                .defineInRange("fillOpacity", 0.35D, 0.05D, 1.0D);
        ForgeConfigSpec.BooleanValue showMinedCount = client
                .comment("Include the mined-ore counter in the hover tooltip.")
                .define("showMinedCount", true);
        ForgeConfigSpec.BooleanValue showFluidVeins = client
                .comment("Show GTCEu bedrock fluid veins on the JourneyMap map.")
                .define("showFluidVeins", true);
        ForgeConfigSpec.BooleanValue showOnlyMarked = client
                .comment("Only draw ore veins the player has marked by breaking a surface rock/ore block or using a prospector.")
                .define("showOnlyMarked", true);
        ForgeConfigSpec.BooleanValue textureMarkers = client
                .comment("Use ore block textures for map markers instead of flat color squares.")
                .define("textureMarkers", true);
        client.pop();
        CLIENT_SPEC = client.build();
        CLIENT = new Client(showOverlay, showOnMinimap, showLabels, labelMinZoom, maxOverlays, fillOpacity,
                showMinedCount, showFluidVeins, showOnlyMarked, textureMarkers);
    }

    private VPConfig() {
    }

    public record Common(ForgeConfigSpec.BooleanValue enableTracking,
                         ForgeConfigSpec.BooleanValue trackMining,
                         ForgeConfigSpec.IntValue scanChunksPerTick,
                         ForgeConfigSpec.IntValue syncIntervalTicks) {
    }

    public record Client(ForgeConfigSpec.BooleanValue showOverlay,
                         ForgeConfigSpec.BooleanValue showOnMinimap,
                         ForgeConfigSpec.BooleanValue showLabels,
                         ForgeConfigSpec.IntValue labelMinZoom,
                         ForgeConfigSpec.IntValue maxOverlays,
                         ForgeConfigSpec.DoubleValue fillOpacity,
                         ForgeConfigSpec.BooleanValue showMinedCount,
                         ForgeConfigSpec.BooleanValue showFluidVeins,
                         ForgeConfigSpec.BooleanValue showOnlyMarked,
                         ForgeConfigSpec.BooleanValue textureMarkers) {
    }
}
