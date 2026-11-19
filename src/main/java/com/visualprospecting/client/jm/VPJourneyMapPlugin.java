package com.visualprospecting.client.jm;

import com.visualprospecting.VisualProspecting;
import com.visualprospecting.client.ClientHooks;
import com.visualprospecting.client.ClientVeinCache;
import com.visualprospecting.config.VPConfig;
import com.visualprospecting.data.OreEntry;
import com.visualprospecting.data.VeinNames;
import com.visualprospecting.data.VeinRecord;
import journeymap.client.api.ClientPlugin;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.IClientPlugin;
import journeymap.client.api.display.Context;
import journeymap.client.api.display.DisplayType;
import journeymap.client.api.display.PolygonOverlay;
import journeymap.client.api.display.Waypoint;
import journeymap.client.api.event.ClientEvent;
import journeymap.client.api.event.DisplayUpdateEvent;
import journeymap.client.api.event.forge.FullscreenDisplayEvent;
import journeymap.client.api.model.MapPolygon;
import journeymap.client.api.model.ShapeProperties;
import journeymap.client.api.model.TextProperties;
import journeymap.client.api.util.UIState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ClientPlugin
public final class VPJourneyMapPlugin implements IClientPlugin {

    private IClientAPI api;

    @Override
    public void initialize(IClientAPI api) {
        this.api = api;
        VeinOverlayManager.init(api);
        ClientHooks.toggleOverlay = VeinOverlayManager::toggle;
        ClientHooks.createWaypoint = VeinOverlayManager::createWaypointAtNearest;
        ClientHooks.dataChanged = VeinOverlayManager::onDataChanged;
        ClientHooks.overlayEnabled = VeinOverlayManager::isEnabled;
        api.subscribe(VisualProspecting.MODID, EnumSet.of(
                ClientEvent.Type.DISPLAY_UPDATE,
                ClientEvent.Type.MAPPING_STARTED,
                ClientEvent.Type.MAPPING_STOPPED));
        MinecraftForge.EVENT_BUS.register(this);
        VisualProspecting.LOGGER.info("JourneyMap integration enabled");
    }

    @Override
    public String getModId() {
        return VisualProspecting.MODID;
    }

    @Override
    public void onEvent(ClientEvent event) {
        if (event.type == ClientEvent.Type.DISPLAY_UPDATE && event instanceof DisplayUpdateEvent update) {
            VeinOverlayManager.refresh(update.uiState);
        } else if (event.type == ClientEvent.Type.MAPPING_STOPPED) {
            VeinOverlayManager.clear();
        } else if (event.type == ClientEvent.Type.MAPPING_STARTED) {
            VeinOverlayManager.markDirty();
        }
    }

    @SubscribeEvent
    public void onAddonButtons(FullscreenDisplayEvent.AddonButtonDisplayEvent event) {
        event.getThemeButtonDisplay().addThemeButton(
                "VP",
                "Visual Prospecting",
                button -> ClientHooks.openMenu.run()
        );
    }

    /**
     * Draws GTCEu vein squares and handles the optional JourneyMap waypoint shortcut.
     */
    public static final class VeinOverlayManager {

        private static final Map<String, PolygonOverlay> SHOWN = new HashMap<>();
        private static final Map<String, String> TITLES = new HashMap<>();
        private static IClientAPI api;
        private static boolean enabled = true;
        private static ResourceLocationKey lastDimension;
        private static int lastStructureVersion = -1;

        private VeinOverlayManager() {
        }

        static void init(IClientAPI clientApi) {
            api = clientApi;
            enabled = VPConfig.CLIENT.showOverlay().get();
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

        /**
         * Called when a network packet changed the client-side vein data. If the map is open we refresh
         * immediately instead of waiting for JourneyMap's next display-update event.
         */
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
            boolean dimensionChanged = !current.equals(lastDimension);
            boolean structureChanged = data.structureVersion() != lastStructureVersion;
            if (dimensionChanged || structureChanged) {
                lastDimension = current;
                lastStructureVersion = data.structureVersion();
                removeAllOverlays();
                syncVisible(state, data);
            } else {
                syncVisible(state, data);
            }
        }

        /**
         * Adds overlays that became visible after panning and removes those that left the map, without
         * rebuilding the whole layer. JourneyMap fires DISPLAY_UPDATE frequently, so this stays cheap.
         */
        private static void syncVisible(UIState state, ClientVeinCache.DimensionData data) {
            List<VeinRecord> visible = visibleVeins(state, data);
            Set<String> keep = new HashSet<>();
            for (VeinRecord record : visible) {
                keep.add(record.key());
            }
            for (Iterator<Map.Entry<String, PolygonOverlay>> iterator = SHOWN.entrySet().iterator(); iterator.hasNext(); ) {
                Map.Entry<String, PolygonOverlay> entry = iterator.next();
                if (!keep.contains(entry.getKey())) {
                    api.remove(entry.getValue());
                    iterator.remove();
                    TITLES.remove(entry.getKey());
                }
            }
            for (VeinRecord record : visible) {
                if (!SHOWN.containsKey(record.key())) {
                    showOverlay(state, record);
                }
            }
            updateTitles(data);
        }

        private static void showOverlay(UIState state, VeinRecord record) {
            String id = record.key();
            PolygonOverlay overlay = createOverlay(state, record, id);
            if (overlay == null) {
                return;
            }
            try {
                api.show(overlay);
                SHOWN.put(id, overlay);
                TITLES.put(id, title(record));
            } catch (Throwable throwable) {
                VisualProspecting.LOGGER.warn("Could not show JourneyMap overlay {}", id, throwable);
            }
        }

        private static void updateTitles(ClientVeinCache.DimensionData data) {
            for (Map.Entry<String, PolygonOverlay> entry : SHOWN.entrySet()) {
                VeinRecord record = data.get(entry.getKey());
                if (record == null) {
                    continue;
                }
                String title = title(record);
                if (!title.equals(TITLES.get(entry.getKey()))) {
                    entry.getValue().setTitle(title);
                    entry.getValue().flagForRerender();
                    TITLES.put(entry.getKey(), title);
                }
            }
        }

        private static List<VeinRecord> visibleVeins(UIState state, ClientVeinCache.DimensionData data) {
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
            List<VeinRecord> result = new ArrayList<>();
            boolean onlyMarked = com.visualprospecting.config.VPConfig.CLIENT.showOnlyMarked().get();
            for (VeinRecord record : data.all()) {
                if (onlyMarked && !record.marked) {
                    continue;
                }
                if (record.centerX >= minX && record.centerX <= maxX
                        && record.centerZ >= minZ && record.centerZ <= maxZ) {
                    result.add(record);
                }
            }
            BlockPos center = state.mapCenter;
            double cx = center == null ? 0 : center.getX();
            double cz = center == null ? 0 : center.getZ();
            result.sort(Comparator.comparingDouble(record -> {
                double dx = record.centerX - cx;
                double dz = record.centerZ - cz;
                return dx * dx + dz * dz;
            }));
            int limit = VPConfig.CLIENT.maxOverlays().get();
            if (result.size() > limit) {
                return result.subList(0, limit);
            }
            return result;
        }

        @Nullable
        private static PolygonOverlay createOverlay(UIState state, VeinRecord record, String id) {
            int color = record.ores.isEmpty() ? 0x888888 : record.ores.get(0).color;
            if (color == 0) {
                color = 0x888888;
            }
            int stroke = brighten(color);
            ShapeProperties shape = new ShapeProperties()
                    .setFillColor(color)
                    .setFillOpacity(VPConfig.CLIENT.fillOpacity().get().floatValue())
                    .setStrokeColor(stroke)
                    .setStrokeOpacity(0.9F)
                    .setStrokeWidth(1.5F);

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
                    .setScale(1.0F);

            PolygonOverlay overlay = new PolygonOverlay(VisualProspecting.MODID, id, state.dimension, shape, polygon);
            overlay.setTitle(title(record));
            overlay.setLabel(label(record));
            overlay.setOverlayGroupName("Ore Veins");
            overlay.setDisplayOrder(100);
            overlay.setActiveMapTypes(EnumSet.of(Context.MapType.Any));
            if (VPConfig.CLIENT.showOnMinimap().get()) {
                overlay.setActiveUIs(EnumSet.of(Context.UI.Fullscreen, Context.UI.Minimap));
            } else {
                overlay.setActiveUIs(EnumSet.of(Context.UI.Fullscreen));
            }
            overlay.setTextProperties(text);
            return overlay;
        }

        private static String title(VeinRecord record) {
            StringBuilder builder = new StringBuilder();
            builder.append(VeinNames.veinName(record.veinId).getString()).append('\n');
            builder.append("Layer: ").append(VeinNames.layerName(record.layer).getString())
                    .append("   Y: ").append(record.centerY).append('\n');
            builder.append("Center: ").append(record.centerX).append(", ").append(record.centerZ).append('\n');
            builder.append("Size: ").append(record.clusterSize)
                    .append("   Density: ").append(Math.round(record.density * 100.0F)).append("%\n");
            builder.append("Ores:\n");
            for (OreEntry ore : record.ores) {
                builder.append("  ").append(VeinNames.oreName(ore).getString())
                        .append("  x").append(ore.weight).append('\n');
            }
            if (VPConfig.CLIENT.showMinedCount().get()) {
                builder.append("Mined: ").append(record.mined);
            }
            return builder.toString().trim();
        }

        @Nullable
        private static String label(VeinRecord record) {
            if (!VPConfig.CLIENT.showLabels().get()) {
                return null;
            }
            return VeinNames.veinName(record.veinId).getString() + "\n"
                    + VeinNames.layerName(record.layer).getString();
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
            String id = "vp_" + record.key().replace(':', '_');
            Waypoint waypoint = new Waypoint(VisualProspecting.MODID, id, name,
                    minecraft.level.dimension(), new BlockPos(record.centerX, record.centerY, record.centerZ));
            waypoint.setColor(0xFFAA00);
            waypoint.setBackgroundColor(0x202020);
            try {
                api.show(waypoint);
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

        /**
         * Small wrapper so the inner class does not accidentally retain a mutable ResourceLocation.
         */
        private record ResourceLocationKey(net.minecraft.resources.ResourceLocation location) {
        }
    }
}
