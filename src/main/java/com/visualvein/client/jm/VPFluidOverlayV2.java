package com.visualvein.client.jm;

import com.visualvein.VisualVein;
import com.visualvein.client.ClientFluidCache;
import com.visualvein.client.ClientVeinCache;
import com.visualvein.config.VPConfig;
import com.visualvein.data.FluidVeinRecord;
import com.visualvein.data.VeinRecord;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
        enabled = VPConfig.CLIENT.showFluidVeins().get();
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
        if (state != null) {
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
            // An 8x8 fluid region often contains a marked ore vein. Ore markers win: hide the
            // big fluid square and its tooltip there instead of stacking two hover boxes.
            if (oreNear(dimension, record)) {
                continue;
            }
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
        if (records.size() > 120) {
            records = records.subList(0, 120);
        }

        for (FluidVeinRecord record : records) {
            if (!SHOWN.containsKey(record.key())) {
                PolygonOverlay overlay = createOverlay(state, record);
                if (overlay != null) {
                    try {
                        api.show(overlay);
                        SHOWN.put(record.key(), overlay);
                        TITLES.put(record.key(), overlay.getTitle());
                    } catch (Throwable throwable) {
                        VisualVein.LOGGER.warn("Could not show fluid overlay {}", record.key(), throwable);
                    }
                }
            }
        }
        Iterator<Map.Entry<String, PolygonOverlay>> iterator = SHOWN.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, PolygonOverlay> entry = iterator.next();
            boolean present = records.stream().anyMatch(record -> record.key().equals(entry.getKey()));
            if (!present) {
                api.remove(entry.getValue());
                iterator.remove();
                TITLES.remove(entry.getKey());
            }
        }
        // Fluid yield/operations are re-synced periodically; update titles without rebuilding.
        for (FluidVeinRecord record : records) {
            PolygonOverlay overlay = SHOWN.get(record.key());
            if (overlay == null) {
                continue;
            }
            Fluid fluid = BuiltInRegistries.FLUID.get(record.fluidId());
            if (fluid == null) {
                continue;
            }
            String name = new FluidStack(fluid, 1).getDisplayName().getString();
            String title = title(record, name);
            if (!title.equals(TITLES.get(record.key()))) {
                overlay.setTitle(title);
                overlay.flagForRerender();
                TITLES.put(record.key(), title);
            }
        }
    }

    private static boolean oreNear(ResourceLocation dimension, FluidVeinRecord fluid) {
        ClientVeinCache.DimensionData data = ClientVeinCache.get(dimension);
        if (data == null) {
            return false;
        }
        for (VeinRecord ore : data.all()) {
            if (!ore.marked) {
                continue;
            }
            if (Math.abs(ore.centerX - fluid.centerX()) <= 64
                    && Math.abs(ore.centerZ - fluid.centerZ()) <= 64) {
                return true;
            }
        }
        return false;
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

    private static PolygonOverlay createOverlay(UIState state, FluidVeinRecord record) {
        Fluid fluid = BuiltInRegistries.FLUID.get(record.fluidId());
        if (fluid == null) {
            return null;
        }
        String name = new FluidStack(fluid, 1).getDisplayName().getString();
        int color = fluidColor(record.fluidId());
        ShapeProperties shape = new ShapeProperties()
                .setFillColor(color)
                .setFillOpacity(0.32F)
                .setStrokeColor(brighten(color))
                .setStrokeOpacity(0.9F)
                .setStrokeWidth(1.5F);
        int x = record.centerX();
        int z = record.centerZ();
        MapPolygon polygon = new MapPolygon(
                new BlockPos(x - 32, 32, z - 32),
                new BlockPos(x + 32, 32, z - 32),
                new BlockPos(x + 32, 32, z + 32),
                new BlockPos(x - 32, 32, z + 32));

        TextProperties text = new TextProperties()
                .setColor(0xFFFFFF)
                .setBackgroundColor(0x081018)
                .setBackgroundOpacity(0.55F)
                .setMinZoom(VPConfig.CLIENT.labelMinZoom().get())
                .setOffsetY(-4)
                .setScale(1.0F);
        text.setActiveMapTypes(Context.MapType.values());

        PolygonOverlay overlay = new PolygonOverlay(VisualVein.MODID, state.dimension, shape, polygon);
        overlay.setTitle(title(record, name));
        // The fluid name is already in the hover tooltip; a permanent label only collides with
        // the adjacent ore marker labels.
        overlay.setLabel(null);
        overlay.setOverlayGroupName("Fluid Veins");
        overlay.setDisplayOrder(90);
        overlay.setActiveMapTypes(Context.MapType.values());
        if (VPConfig.CLIENT.showOnMinimap().get()) {
            overlay.setActiveUIs(Context.UI.Fullscreen, Context.UI.Minimap);
        } else {
            overlay.setActiveUIs(Context.UI.Fullscreen);
        }
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

    private static String title(FluidVeinRecord record, String name) {
        return name + "\n"
                + Component.translatable("vp.fluid.tooltip.region",
                        record.centerX(), record.centerZ()).getString() + "\n"
                + Component.translatable("vp.fluid.tooltip.yield", record.yield()).getString() + "\n"
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
