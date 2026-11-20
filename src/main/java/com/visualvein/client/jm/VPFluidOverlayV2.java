package com.visualvein.client.jm;

import com.visualvein.VisualVein;
import com.visualvein.client.ClientFluidCache;
import com.visualvein.data.FluidVeinRecord;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.display.IOverlayListener;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.model.MapPolygon;
import journeymap.api.v2.client.model.ShapeProperties;
import journeymap.api.v2.client.model.TextProperties;
import journeymap.api.v2.client.util.UIState;
import journeymap.api.v2.common.Context;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JourneyMap 6 overlay for GTCEu bedrock fluid veins. Kept separate from the ore overlay so the two
 * layers can be toggled independently.
 */
public final class VPFluidOverlayV2 {

    private static final Map<String, PolygonOverlay> SHOWN = new HashMap<>();
    private static final Map<String, String> TITLES = new HashMap<>();
    private static IClientAPI api;
    private static boolean enabled = true;
    private static long lastVersion = -1;
    private static String lastDimension = "";

    private VPFluidOverlayV2() {
    }

    public static void init(IClientAPI clientApi) {
        api = clientApi;
        // Fluid and ore are alternate map modes now; start with ore, not fluid.
        enabled = false;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!enabled) {
            removeAll();
            return;
        }
        markDirty();
        if (api != null) {
            UIState state = api.getUIState(Context.UI.Fullscreen);
            if (state != null) {
                refresh(state);
            }
        }
    }

    public static void toggle() {
        setEnabled(!enabled);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable(
                    enabled ? "vp.msg.fluid_on" : "vp.msg.fluid_off"), true);
        }
    }

    public static void markDirty() {
        lastVersion = -1;
    }

    public static void refreshCurrent() {
        if (api == null) {
            return;
        }
        if (Minecraft.getInstance().level == null) {
            clear();
            return;
        }
        UIState state = api.getUIState(Context.UI.Fullscreen);
        if (state == null || !state.active) {
            state = api.getUIState(Context.UI.Minimap);
        }
        if (state != null && state.active) {
            refresh(state);
        }
    }

    public static void clear() {
        removeAll();
        lastDimension = "";
        lastVersion = -1;
    }

    private static void removeAll() {
        if (api != null) {
            for (PolygonOverlay overlay : SHOWN.values()) {
                api.remove(overlay);
            }
        }
        SHOWN.clear();
        TITLES.clear();
    }

    public static void refresh(@Nullable UIState state) {
        if (api == null) {
            return;
        }
        if (!enabled) {
            removeAll();
            return;
        }
        // Cached overlays survive map close/reopen; only remove them when the feature is disabled
        // or the data/dimension actually changes.
        if (state == null || !state.active || state.dimension == null) {
            return;
        }
        if (Minecraft.getInstance().level == null) {
            clear();
            return;
        }
        ResourceLocation dimension = state.dimension.location();
        long version = ClientFluidCache.version();
        boolean dimensionChanged = !dimension.toString().equals(lastDimension);
        if (dimensionChanged) {
            removeAll();
        }
        lastDimension = dimension.toString();
        lastVersion = version;

        List<FluidVeinRecord> records = new ArrayList<>();
        for (FluidVeinRecord record : ClientFluidCache.get(dimension)) {
            if (inView(state, record.centerX(), record.centerZ())) {
                records.add(record);
            }
        }
        BlockPos mapCenter = state.mapCenter;
        double cx = mapCenter == null ? 0 : mapCenter.getX();
        double cz = mapCenter == null ? 0 : mapCenter.getZ();
        records.sort(Comparator.comparingDouble(record -> {
            double dx = record.centerX() - cx;
            double dz = record.centerZ() - cz;
            return dx * dx + dz * dz;
        }));
        if (records.size() > 8) {
            records = records.subList(0, 8);
        }

        // At far zoom (and on the minimap) use one region polygon. Only split into 64 chunk
        // tiles at close fullscreen zoom; otherwise the overlay and text count destroys FPS.
        boolean perChunk = state.ui == Context.UI.Fullscreen && state.zoom >= 4096;
        Set<String> keep = new HashSet<>();
        Map<String, String> desiredTitles = new HashMap<>();
        for (FluidVeinRecord record : records) {
            Fluid fluid = BuiltInRegistries.FLUID.get(record.fluidId());
            if (fluid == null) {
                continue;
            }
            String name = new FluidStack(fluid, 1).getDisplayName().getString();
            if (!perChunk) {
                String key = record.key();
                keep.add(key);
                desiredTitles.put(key, regionTitle(record, name));
                if (!SHOWN.containsKey(key)) {
                    PolygonOverlay overlay = createRegionOverlay(state, record, name);
                    if (overlay != null) {
                        try {
                            api.show(overlay);
                            SHOWN.put(key, overlay);
                            TITLES.put(key, overlay.getTitle());
                        } catch (Throwable throwable) {
                            VisualVein.LOGGER.warn("Could not show fluid region {}", key, throwable);
                        }
                    }
                }
                continue;
            }
            int[] yields = record.chunkYields();
            for (int dx = 0; dx < 8; dx++) {
                for (int dz = 0; dz < 8; dz++) {
                    int index = dx * 8 + dz;
                    int chunkYield = index < yields.length ? yields[index] : record.yield();
                    String key = tileKey(record, dx, dz);
                    keep.add(key);
                    desiredTitles.put(key, title(record, name, dx, dz, chunkYield));
                    if (!SHOWN.containsKey(key)) {
                        PolygonOverlay overlay = createOverlay(state, record, dx, dz, name, chunkYield);
                        if (overlay != null) {
                            try {
                                api.show(overlay);
                                SHOWN.put(key, overlay);
                                TITLES.put(key, overlay.getTitle());
                            } catch (Throwable throwable) {
                                VisualVein.LOGGER.warn("Could not show fluid tile {}", key, throwable);
                            }
                        }
                    }
                }
            }
        }

        Iterator<Map.Entry<String, PolygonOverlay>> iterator = SHOWN.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, PolygonOverlay> entry = iterator.next();
            if (!keep.contains(entry.getKey())) {
                api.remove(entry.getValue());
                iterator.remove();
                TITLES.remove(entry.getKey());
            }
        }
        for (Map.Entry<String, PolygonOverlay> entry : SHOWN.entrySet()) {
            String title = desiredTitles.get(entry.getKey());
            if (title != null && !title.equals(TITLES.get(entry.getKey()))) {
                entry.getValue().setTitle(title);
                entry.getValue().flagForRerender();
                TITLES.put(entry.getKey(), title);
            }
        }
    }

    private static String tileKey(FluidVeinRecord record, int dx, int dz) {
        return record.key() + ":" + dx + ":" + dz;
    }

    private static boolean inView(UIState state, int x, int z) {
        if (state.blockBounds != null) {
            return x >= state.blockBounds.minX - 64 && x <= state.blockBounds.maxX + 64
                    && z >= state.blockBounds.minZ - 64 && z <= state.blockBounds.maxZ + 64;
        }
        BlockPos center = state.mapCenter;
        if (center == null) {
            return true;
        }
        return Math.abs(x - center.getX()) <= 512 && Math.abs(z - center.getZ()) <= 512;
    }

    private static PolygonOverlay createRegionOverlay(UIState state, FluidVeinRecord record, String name) {
        int color = fluidColor(record.fluidId());
        ShapeProperties shape = new ShapeProperties()
                .setFillColor(color)
                .setFillOpacity(0.28F)
                .setStrokeColor(brighten(color))
                .setStrokeOpacity(0.7F)
                .setStrokeWidth(1.0F);
        int x = record.centerX();
        int z = record.centerZ();
        MapPolygon polygon = new MapPolygon(
                new BlockPos(x - 64, 64, z - 64),
                new BlockPos(x + 64, 64, z - 64),
                new BlockPos(x + 64, 64, z + 64),
                new BlockPos(x - 64, 64, z + 64));
        TextProperties text = new TextProperties()
                .setColor(0xFFFFFF)
                .setBackgroundColor(0x081018)
                .setBackgroundOpacity(0.5F)
                .setMinZoom(0)
                .setScale(0.8F);
        text.setActiveUIs(Context.UI.Fullscreen);
        text.setActiveMapTypes(Context.MapType.values());
        PolygonOverlay overlay = new PolygonOverlay(VisualVein.MODID, state.dimension, shape, polygon);
        overlay.setTitle(regionTitle(record, name));
        overlay.setLabel(null);
        overlay.setOverlayGroupName("Fluid Veins");
        overlay.setDisplayOrder(90);
        overlay.setActiveMapTypes(Context.MapType.values());
        overlay.setActiveUIs(Context.UI.Fullscreen, Context.UI.Minimap);
        overlay.setTextProperties(text);
        overlay.setOverlayListener(new IOverlayListener() {
            @Override
            public boolean onMouseClick(UIState uiState, java.awt.geom.Point2D.Double mouse, BlockPos pos,
                                        int button, boolean doubleClick) {
                if (button == 0 && doubleClick) {
                    toggleWaypoint(uiState, record, name);
                    return true;
                }
                return false;
            }
        });
        return overlay;
    }

    private static PolygonOverlay createOverlay(UIState state, FluidVeinRecord record,
                                                 int dx, int dz, String name, int chunkYield) {
        int chunkX = record.regionX() * 8 + dx;
        int chunkZ = record.regionZ() * 8 + dz;
        int x = chunkX * 16 + 8;
        int z = chunkZ * 16 + 8;
        int color = fluidColor(record.fluidId());
        ShapeProperties shape = new ShapeProperties()
                .setFillColor(color)
                .setFillOpacity(0.35F)
                .setStrokeColor(brighten(color))
                .setStrokeOpacity(0.65F)
                .setStrokeWidth(0.8F);
        MapPolygon polygon = new MapPolygon(
                new BlockPos(x - 8, 64, z - 8),
                new BlockPos(x + 8, 64, z - 8),
                new BlockPos(x + 8, 64, z + 8),
                new BlockPos(x - 8, 64, z + 8));

        TextProperties text = new TextProperties()
                .setColor(0xFFFFFF)
                .setBackgroundColor(0x081018)
                .setBackgroundOpacity(0.55F)
                .setMinZoom(4096)
                .setOffsetY(-4)
                .setScale(0.12F);
        text.setActiveMapTypes(Context.MapType.values());
        // Keep the minimap clean and readable: yields only appear in the fullscreen map.
        text.setActiveUIs(Context.UI.Fullscreen);

        PolygonOverlay overlay = new PolygonOverlay(VisualVein.MODID, state.dimension, shape, polygon);
        overlay.setTitle(title(record, name, dx, dz, chunkYield));
        // Show the per-chunk yield when zoomed in; at far zoom the 8x8 grid would be unreadable.
        overlay.setLabel(chunkYield + " L/t");
        overlay.setOverlayGroupName("Fluid Veins");
        overlay.setDisplayOrder(90);
        overlay.setActiveMapTypes(Context.MapType.values());
        overlay.setActiveUIs(Context.UI.Fullscreen, Context.UI.Minimap);
        overlay.setTextProperties(text);
        overlay.setOverlayListener(new IOverlayListener() {
            @Override
            public boolean onMouseClick(UIState uiState, java.awt.geom.Point2D.Double mouse, BlockPos pos,
                                        int button, boolean doubleClick) {
                if (button == 0 && doubleClick) {
                    toggleWaypoint(uiState, record, name);
                    return true;
                }
                return false;
            }
        });
        return overlay;
    }

    private static String waypointName(FluidVeinRecord record, String fluidName) {
        return "VP Fluid: " + fluidName + " [" + record.centerX() + "," + record.centerZ() + "]";
    }

    @Nullable
    private static Waypoint findWaypoint(ResourceKey<Level> dimension, String name) {
        if (api == null || dimension == null) {
            return null;
        }
        try {
            for (Waypoint waypoint : api.getAllWaypoints(dimension)) {
                if (name.equals(waypoint.getName())) {
                    return waypoint;
                }
            }
        } catch (Throwable throwable) {
            VisualVein.LOGGER.debug("Could not list fluid waypoints", throwable);
        }
        return null;
    }

    private static void toggleWaypoint(UIState state, FluidVeinRecord record, String fluidName) {
        if (api == null || state.dimension == null) {
            return;
        }
        String name = waypointName(record, fluidName);
        Waypoint existing = findWaypoint(state.dimension, name);
        Minecraft minecraft = Minecraft.getInstance();
        try {
            if (existing != null) {
                api.removeWaypoint(VisualVein.MODID, existing);
                if (minecraft.player != null) {
                    minecraft.player.displayClientMessage(
                            Component.translatable("vp.msg.waypoint_removed", name), false);
                }
                return;
            }
            Waypoint waypoint = WaypointFactory.createWaypoint(
                    VisualVein.MODID,
                    new BlockPos(record.centerX(), 64, record.centerZ()),
                    name,
                    state.dimension,
                    true);
            waypoint.setColor(0x3388CC);
            waypoint.setShowBeacon(true);
            api.addWaypoint(VisualVein.MODID, waypoint);
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.translatable("vp.msg.waypoint_created", name), false);
            }
        } catch (Throwable throwable) {
            VisualVein.LOGGER.warn("Could not toggle fluid waypoint", throwable);
        }
    }

    private static String regionTitle(FluidVeinRecord record, String name) {
        return name + "\n"
                + Component.translatable("vp.fluid.tooltip.region",
                        record.centerX(), record.centerZ()).getString() + "\n"
                + Component.translatable("vp.fluid.tooltip.zoom_for_chunks").getString() + "\n"
                + Component.translatable("vp.fluid.tooltip.depleted_yield",
                        record.depletedYield()).getString() + "\n"
                + Component.translatable("vp.fluid.tooltip.operations",
                        record.operations(), record.maxOperations()).getString() + "\n"
                + Component.translatable("vp.fluid.tooltip.status",
                        Component.translatable(record.depleted()
                                ? "vp.fluid.status.depleted" : "vp.fluid.status.active").getString()).getString();
    }

    private static String title(FluidVeinRecord record, String name, int dx, int dz, int chunkYield) {
        int chunkX = record.regionX() * 8 + dx;
        int chunkZ = record.regionZ() * 8 + dz;
        return name + "\n"
                + Component.translatable("vp.fluid.tooltip.chunk", chunkX, chunkZ).getString() + "\n"
                + Component.translatable("vp.fluid.tooltip.chunk_yield", chunkYield).getString() + "\n"
                + Component.translatable("vp.fluid.tooltip.region",
                        record.centerX(), record.centerZ()).getString() + "\n"
                + Component.translatable("vp.fluid.tooltip.depleted_yield",
                        record.depletedYield()).getString() + "\n"
                + Component.translatable("vp.fluid.tooltip.operations",
                        record.operations(), record.maxOperations()).getString() + "\n"
                + Component.translatable("vp.fluid.tooltip.status",
                        Component.translatable(record.depleted()
                                ? "vp.fluid.status.depleted" : "vp.fluid.status.active").getString()).getString();
    }

    private static int fluidColor(ResourceLocation id) {
        int hash = id.hashCode();
        int red = 64 + Math.abs((hash >> 16) & 0x7F);
        int green = 64 + Math.abs((hash >> 8) & 0x7F);
        int blue = 64 + Math.abs(hash & 0x7F);
        return (red << 16) | (green << 8) | blue;
    }

    private static int brighten(int color) {
        int red = Math.min(255, ((color >> 16) & 0xFF) + 48);
        int green = Math.min(255, ((color >> 8) & 0xFF) + 48);
        int blue = Math.min(255, (color & 0xFF) + 48);
        return (red << 16) | (green << 8) | blue;
    }
}
