package com.visualprospecting.client.jm;

import com.visualprospecting.VisualProspecting;
import com.visualprospecting.client.ClientHooks;
import com.visualprospecting.client.ClientVeinCache;
import com.visualprospecting.config.VPConfig;
import com.visualprospecting.data.VeinGroup;
import com.visualprospecting.data.VeinNames;
import com.visualprospecting.data.VeinRecord;
import com.visualprospecting.network.SetVeinStatusPacket;
import com.visualprospecting.network.VPNetwork;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.JmApiVersion;
import journeymap.api.v2.client.display.DisplayType;
import journeymap.api.v2.client.display.IOverlayListener;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.event.DisplayUpdateEvent;
import journeymap.api.v2.client.event.MappingEvent;
import journeymap.api.v2.client.model.MapPolygon;
import journeymap.api.v2.client.model.ShapeProperties;
import journeymap.api.v2.client.model.TextProperties;
import journeymap.api.v2.client.util.UIState;
import journeymap.api.v2.common.Context;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.event.ClientEventRegistry;
import journeymap.api.v2.common.event.FullscreenEventRegistry;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

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
 * JourneyMap 6.x (API v2) entry point. JourneyMap 5.x uses the separate v1 plugin class.
 *
 * <p>Only one of the two plugin classes is ever loaded: JourneyMap 5 scans for
 * {@code journeymap.client.api.ClientPlugin}, while JourneyMap 6 scans for
 * {@code journeymap.api.v2.common.JourneyMapPlugin}.</p>
 */
@JourneyMapPlugin(apiVersion = JmApiVersion.VERSION)
public final class VPJourneyMapPluginV2 implements IClientPlugin {

    @Override
    public String getModId() {
        return VisualProspecting.MODID;
    }

    @Override
    public void initialize(IClientAPI api) {
        VeinOverlayManagerV2.init(api);
        VPFluidOverlayV2.init(api);
        ClientHooks.toggleOverlay = VeinOverlayManagerV2::toggle;
        ClientHooks.createWaypoint = VeinOverlayManagerV2::createWaypointAtNearest;
        ClientHooks.dataChanged = () -> {
            VeinOverlayManagerV2.onDataChanged();
            VPFluidOverlayV2.markDirty();
            VPFluidOverlayV2.refreshCurrent();
        };
        ClientHooks.overlayEnabled = VeinOverlayManagerV2::isEnabled;
        ClientHooks.labelsEnabled = VeinOverlayManagerV2::isLabelsEnabled;
        ClientHooks.minimapEnabled = VeinOverlayManagerV2::isMinimapEnabled;
        ClientHooks.toggleLabels = () -> VeinOverlayManagerV2.setLabelsEnabled(!VeinOverlayManagerV2.isLabelsEnabled());
        ClientHooks.toggleMinimap = () -> VeinOverlayManagerV2.setMinimapEnabled(!VeinOverlayManagerV2.isMinimapEnabled());
        ClientHooks.fluidEnabled = VPFluidOverlayV2::isEnabled;
        ClientHooks.toggleFluid = VPFluidOverlayV2::toggle;

        ClientEventRegistry.DISPLAY_UPDATE_EVENT.subscribe(VisualProspecting.MODID, event -> {
            VeinOverlayManagerV2.refresh(event.uiState);
            VPFluidOverlayV2.refresh(event.uiState);
        });
        ClientEventRegistry.MAPPING_EVENT.subscribe(VisualProspecting.MODID, event -> {
            if (event.getStage() == MappingEvent.Stage.MAPPING_STOPPED) {
                VeinOverlayManagerV2.clear();
                VPFluidOverlayV2.clear();
            } else {
                VeinOverlayManagerV2.markDirty();
                VPFluidOverlayV2.markDirty();
            }
        });
        FullscreenEventRegistry.ADDON_BUTTON_DISPLAY_EVENT.subscribe(VisualProspecting.MODID, event ->
                event.getThemeButtonDisplay().addThemeButton(
                        "VP",
                        new ResourceLocation(VisualProspecting.MODID, "textures/gui/vp_icon.png"),
                        button -> ClientHooks.openMenu.run()
                ));
        VisualProspecting.LOGGER.info("JourneyMap 6.x (API v2) integration enabled");
    }

    public static final class VeinOverlayManagerV2 {

        private static final Map<String, PolygonOverlay> SHOWN = new HashMap<>();
        private static final Map<String, String> TITLES = new HashMap<>();
        private static IClientAPI api;
        private static boolean enabled = true;
        private static boolean labelsEnabled = true;
        private static boolean minimapEnabled = false;
        private static ResourceLocationKey lastDimension;
        private static String lastViewKey = "";
        private static int lastStructureVersion = -1;

        private VeinOverlayManagerV2() {
        }

        static void init(IClientAPI clientApi) {
            api = clientApi;
            enabled = VPConfig.CLIENT.showOverlay().get();
            // Persistent map labels are opt-in: at most zoom levels they overlap the markers and
            // their anchor is the polygon centre, which makes them look shifted downward.
            labelsEnabled = false;
            minimapEnabled = VPConfig.CLIENT.showOnMinimap().get();
        }

        public static boolean isLabelsEnabled() {
            return labelsEnabled;
        }

        public static void setLabelsEnabled(boolean value) {
            labelsEnabled = value;
            markDirty();
            refreshCurrent();
        }

        public static boolean isMinimapEnabled() {
            return minimapEnabled;
        }

        public static void setMinimapEnabled(boolean value) {
            minimapEnabled = value;
            markDirty();
            refreshCurrent();
        }

        public static boolean isEnabled() {
            return enabled;
        }

        public static void setEnabled(boolean value) {
            if (enabled == value) {
                return;
            }
            enabled = value;
            if (!enabled) {
                removeAllOverlays();
                return;
            }
            markDirty();
            refreshCurrent();
        }

        public static void toggle() {
            setEnabled(!enabled);
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.translatable(
                        enabled ? "vp.msg.overlay_on" : "vp.msg.overlay_off"), true);
            }
        }

        public static void markDirty() {
            lastStructureVersion = -1;
        }

        public static void onDataChanged() {
            markDirty();
            refreshCurrent();
        }

        public static void clear() {
            removeAllOverlays();
            lastDimension = null;
            lastStructureVersion = -1;
        }

        private static void removeAllOverlays() {
            if (api != null) {
                api.removeAll(VisualProspecting.MODID, DisplayType.Polygon);
            }
            SHOWN.clear();
            TITLES.clear();
        }

        private static void refreshCurrent() {
            if (api == null) {
                return;
            }
            UIState state = api.getUIState(Context.UI.Fullscreen);
            if (state == null) {
                state = api.getUIState(Context.UI.Minimap);
            }
            if (state != null) {
                refresh(state);
            }
        }

        public static void refresh(@Nullable UIState state) {
            if (api == null) {
                return;
            }
            if (!enabled || state == null || !state.active || state.dimension == null) {
                removeAllOverlays();
                return;
            }
            ClientVeinCache.DimensionData data = ClientVeinCache.get(state.dimension.location());
            if (data == null || data.size() == 0) {
                removeAllOverlays();
                return;
            }

            ResourceLocationKey current = new ResourceLocationKey(state.dimension.location());
            String viewKey = state.mapType + ":" + state.chunkY;
            boolean dimensionChanged = !current.equals(lastDimension);
            boolean viewChanged = !viewKey.equals(lastViewKey);
            boolean structureChanged = data.structureVersion() != lastStructureVersion;
            if (dimensionChanged || structureChanged || viewChanged) {
                lastDimension = current;
                lastViewKey = viewKey;
                lastStructureVersion = data.structureVersion();
                removeAllOverlays();
                syncVisible(state, data);
            } else {
                syncVisible(state, data);
            }
        }

        private static void syncVisible(UIState state, ClientVeinCache.DimensionData data) {
            List<VeinGroup> visible = visibleGroups(state, data);
            Set<String> keep = new HashSet<>();
            for (VeinGroup group : visible) {
                keep.add(group.key());
            }
            for (Iterator<Map.Entry<String, PolygonOverlay>> iterator = SHOWN.entrySet().iterator(); iterator.hasNext(); ) {
                Map.Entry<String, PolygonOverlay> entry = iterator.next();
                if (!keep.contains(entry.getKey())) {
                    api.remove(entry.getValue());
                    iterator.remove();
                    TITLES.remove(entry.getKey());
                }
            }
            for (VeinGroup group : visible) {
                if (!SHOWN.containsKey(group.key())) {
                    showOverlay(state, group);
                }
            }
            updateTitles(data, state);
        }

        private static void showOverlay(UIState state, VeinGroup group) {
            PolygonOverlay overlay = createOverlay(state, group);
            if (overlay == null) {
                return;
            }
            try {
                api.show(overlay);
                SHOWN.put(group.key(), overlay);
                TITLES.put(group.key(), group.title());
            } catch (Throwable throwable) {
                VisualProspecting.LOGGER.warn("Could not show JourneyMap overlay {}", group.key(), throwable);
            }
        }

        private static void updateTitles(ClientVeinCache.DimensionData data, UIState state) {
            if (SHOWN.isEmpty()) {
                return;
            }
            Map<String, VeinGroup> groups = groupMap(data, state);
            for (Map.Entry<String, PolygonOverlay> entry : SHOWN.entrySet()) {
                VeinGroup group = groups.get(entry.getKey());
                if (group == null) {
                    continue;
                }
                String title = group.title();
                String label = labelsEnabled ? group.label() : null;
                if (!title.equals(TITLES.get(entry.getKey()))
                        || !java.util.Objects.equals(label, entry.getValue().getLabel())) {
                    entry.getValue().setTitle(title);
                    entry.getValue().setLabel(label);
                    entry.getValue().flagForRerender();
                    TITLES.put(entry.getKey(), title);
                }
            }
        }

        private static Map<String, VeinGroup> groupMap(ClientVeinCache.DimensionData data, UIState state) {
            Map<String, VeinGroup> groups = new HashMap<>();
            boolean underground = state.mapType == Context.MapType.Underground;
            boolean onlyMarked = VPConfig.CLIENT.showOnlyMarked().get();
            List<VeinRecord> visible = new ArrayList<>();
            for (VeinRecord record : data.all()) {
                if (!onlyMarked || record.marked) {
                    visible.add(record);
                }
            }
            for (VeinGroup group : VeinGroup.group(visible, underground, state.chunkY)) {
                groups.put(group.key(), group);
            }
            return groups;
        }

        private static List<VeinGroup> visibleGroups(UIState state, ClientVeinCache.DimensionData data) {
            double minX;
            double maxX;
            double minZ;
            double maxZ;
            if (state.blockBounds != null) {
                minX = state.blockBounds.minX - 32;
                maxX = state.blockBounds.maxX + 32;
                minZ = state.blockBounds.minZ - 32;
                maxZ = state.blockBounds.maxZ + 32;
            } else {
                BlockPos center = state.mapCenter;
                double cx = center == null ? 0 : center.getX();
                double cz = center == null ? 0 : center.getZ();
                minX = cx - 256;
                maxX = cx + 256;
                minZ = cz - 256;
                maxZ = cz + 256;
            }
            boolean onlyMarked = VPConfig.CLIENT.showOnlyMarked().get();
            List<VeinRecord> inView = new ArrayList<>();
            for (VeinRecord record : data.all()) {
                if (onlyMarked && !record.marked) {
                    continue;
                }
                if (record.centerX >= minX && record.centerX <= maxX
                        && record.centerZ >= minZ && record.centerZ <= maxZ) {
                    inView.add(record);
                }
            }
            boolean underground = state.mapType == Context.MapType.Underground;
            List<VeinGroup> groups = VeinGroup.group(inView, underground, state.chunkY);
            BlockPos center = state.mapCenter;
            double cx = center == null ? 0 : center.getX();
            double cz = center == null ? 0 : center.getZ();
            groups.sort(Comparator.comparingDouble(group -> {
                double dx = group.primary().centerX - cx;
                double dz = group.primary().centerZ - cz;
                return dx * dx + dz * dz;
            }));
            int limit = VPConfig.CLIENT.maxOverlays().get();
            if (groups.size() > limit) {
                return groups.subList(0, limit);
            }
            return groups;
        }

        @Nullable
        private static PolygonOverlay createOverlay(UIState state, VeinGroup group) {
            VeinRecord record = group.primary();
            int color = record.depleted ? 0x8A2B2B
                    : (record.ores.isEmpty() ? 0x888888 : record.ores.get(0).color);
            if (color == 0) {
                color = 0x888888;
            }
            int stroke = record.depleted ? 0xFF5555 : brighten(color);
            ShapeProperties shape = new ShapeProperties()
                    .setFillColor(color)
                    .setFillOpacity(VPConfig.CLIENT.fillOpacity().get().floatValue())
                    .setStrokeColor(stroke)
                    .setStrokeOpacity(0.9F)
                    .setStrokeWidth(record.depleted ? 2.5F : 1.5F);

            int x = record.centerX;
            int y = record.centerY;
            int z = record.centerZ;
            MapPolygon polygon = new MapPolygon(
                    new BlockPos(x - 8, y, z - 8),
                    new BlockPos(x + 8, y, z - 8),
                    new BlockPos(x + 8, y, z + 8),
                    new BlockPos(x - 8, y, z + 8)
            );

            TextProperties text = new TextProperties()
                    .setColor(0xFFFFFF)
                    .setBackgroundColor(0x101010)
                    .setBackgroundOpacity(0.55F)
                    .setMinZoom(VPConfig.CLIENT.labelMinZoom().get())
                    .setOffsetY(-40)
                    .setScale(1.0F);
            text.setActiveMapTypes(Context.MapType.values());

            PolygonOverlay overlay = new PolygonOverlay(VisualProspecting.MODID, state.dimension, shape, polygon);
            overlay.setTitle(group.title());
            overlay.setLabel(labelsEnabled ? group.label() : null);
            overlay.setOverlayGroupName("Ore Veins");
            overlay.setDisplayOrder(record.depleted ? 110 : 100);
            overlay.setActiveMapTypes(Context.MapType.values());
            if (minimapEnabled) {
                overlay.setActiveUIs(Context.UI.Fullscreen, Context.UI.Minimap);
            } else {
                overlay.setActiveUIs(Context.UI.Fullscreen);
            }
            overlay.setTextProperties(text);
            overlay.setOverlayListener(new IOverlayListener() {
                @Override
                public void onOverlayMenuPopup(UIState uiState, java.awt.geom.Point2D.Double mouse, BlockPos pos,
                                               journeymap.api.v2.client.fullscreen.ModPopupMenu menu) {
                    menu.addMenuItem(record.depleted ? "标记为未枯竭" : "标记为已枯竭", clicked -> {
                        VPNetwork.sendToServer(new SetVeinStatusPacket(
                                uiState.dimension.location(), record.key(), !record.depleted));
                    });
                    menu.addMenuItem("创建矿脉路径点", clicked -> ClientHooks.createWaypoint.run());
                }
            });
            return overlay;
        }

        public static void createWaypointAtNearest() {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.level == null || api == null) {
                return;
            }
            ClientVeinCache.DimensionData data = ClientVeinCache.get(minecraft.level.dimension().location());
            if (data == null || data.size() == 0) {
                minecraft.player.displayClientMessage(Component.translatable("vp.msg.no_data"), true);
                return;
            }
            VeinRecord record = data.nearest(minecraft.player.getX(), minecraft.player.getZ());
            if (record == null) {
                minecraft.player.displayClientMessage(Component.translatable("vp.msg.no_data"), true);
                return;
            }
            String name = VeinNames.veinName(record.veinId).getString()
                    + " (" + VeinNames.layerName(record.layer).getString() + ")";
            Waypoint waypoint = WaypointFactory.createWaypoint(
                    VisualProspecting.MODID,
                    new BlockPos(record.centerX, record.centerY, record.centerZ),
                    name,
                    minecraft.level.dimension(),
                    true);
            waypoint.setColor(0xFFAA00);
            try {
                api.addWaypoint(VisualProspecting.MODID, waypoint);
                minecraft.player.displayClientMessage(
                        Component.translatable("vp.msg.waypoint_created", name), true);
            } catch (Throwable throwable) {
                VisualProspecting.LOGGER.warn("Could not create JourneyMap waypoint", throwable);
            }
        }

        private static int brighten(int color) {
            int red = Math.min(255, ((color >> 16) & 0xFF) + 48);
            int green = Math.min(255, ((color >> 8) & 0xFF) + 48);
            int blue = Math.min(255, (color & 0xFF) + 48);
            return (red << 16) | (green << 8) | blue;
        }

        private record ResourceLocationKey(ResourceLocation location) {
        }
    }
}
