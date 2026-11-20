package com.visualvein.client.jm;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialStack;
import com.visualvein.VisualVein;
import com.visualvein.client.ClientHooks;
import com.visualvein.client.ClientVeinCache;
import com.visualvein.config.VPConfig;
import com.visualvein.data.VeinGroup;
import com.visualvein.data.VeinNames;
import com.visualvein.data.VeinRecord;
import com.visualvein.network.SetVeinStatusPacket;
import com.visualvein.network.VPNetwork;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.JmApiVersion;
import journeymap.api.v2.client.display.IOverlayListener;
import journeymap.api.v2.client.display.MarkerOverlay;
import journeymap.api.v2.client.display.Overlay;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.fullscreen.IThemeButton;
import journeymap.api.v2.client.event.DisplayUpdateEvent;
import journeymap.api.v2.client.event.MappingEvent;
import journeymap.api.v2.client.model.MapImage;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

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

    private static IThemeButton oreButton;
    private static IThemeButton fluidButton;
    private static IThemeButton noneButton;

    @Override
    public String getModId() {
        return VisualVein.MODID;
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
        ClientHooks.toggleHoveredStatus = VeinOverlayManagerV2::toggleHoveredStatus;
        ClientHooks.toggleMarkerStyle = () -> VeinOverlayManagerV2.setTextureMode(!VeinOverlayManagerV2.isTextureMode());
        ClientHooks.textureMarkers = VeinOverlayManagerV2::isTextureMode;

        ClientEventRegistry.DISPLAY_UPDATE_EVENT.subscribe(VisualVein.MODID, event -> {
            VeinOverlayManagerV2.refresh(event.uiState);
            VPFluidOverlayV2.refresh(event.uiState);
        });
        ClientEventRegistry.MAPPING_EVENT.subscribe(VisualVein.MODID, event -> {
            // Keep the created overlays cached. MAPPING_STOPPED is also fired around normal map
            // view changes; discarding everything there forced a full rebuild on every reopen.
            if (event.getStage() != MappingEvent.Stage.MAPPING_STOPPED) {
                VeinOverlayManagerV2.markDirty();
                VPFluidOverlayV2.markDirty();
            }
        });
        FullscreenEventRegistry.ADDON_BUTTON_DISPLAY_EVENT.subscribe(VisualVein.MODID, event -> {
            var display = event.getThemeButtonDisplay();
            // These are radio-style mode selectors: the action always selects its mode, even if
            // JourneyMap's automatic toggle state is out of sync. The third button clears both.
            oreButton = display.addThemeToggleButton(
                    Component.translatable("vp.button.ore").getString(),
                    new ResourceLocation(VisualVein.MODID, "textures/gui/ore_icon.png"),
                    VeinOverlayManagerV2.isEnabled(),
                    button -> {
                        VeinOverlayManagerV2.setEnabled(true);
                        VPFluidOverlayV2.setEnabled(false);
                        syncMapButtons();
                    });
            fluidButton = display.addThemeToggleButton(
                    Component.translatable("vp.button.fluid").getString(),
                    new ResourceLocation(VisualVein.MODID, "textures/gui/fluid_icon.png"),
                    VPFluidOverlayV2.isEnabled(),
                    button -> {
                        VPFluidOverlayV2.setEnabled(true);
                        VeinOverlayManagerV2.setEnabled(false);
                        syncMapButtons();
                    });
            noneButton = display.addThemeToggleButton(
                    Component.translatable("vp.button.none").getString(),
                    new ResourceLocation(VisualVein.MODID, "textures/gui/none_icon.png"),
                    !VeinOverlayManagerV2.isEnabled() && !VPFluidOverlayV2.isEnabled(),
                    button -> {
                        VeinOverlayManagerV2.setEnabled(false);
                        VPFluidOverlayV2.setEnabled(false);
                        syncMapButtons();
                    });
            display.addThemeButton(
                    "VP",
                    new ResourceLocation(VisualVein.MODID, "textures/gui/vp_icon.png"),
                    button -> ClientHooks.openMenu.run());
        });
        VisualVein.LOGGER.info("JourneyMap 6.x (API v2) integration enabled");
    }

    private static void syncMapButtons() {
        if (oreButton != null) {
            oreButton.setToggled(VeinOverlayManagerV2.isEnabled());
        }
        if (fluidButton != null) {
            fluidButton.setToggled(VPFluidOverlayV2.isEnabled());
        }
        if (noneButton != null) {
            noneButton.setToggled(!VeinOverlayManagerV2.isEnabled() && !VPFluidOverlayV2.isEnabled());
        }
    }

    public static final class VeinOverlayManagerV2 {

        private static final Map<String, Overlay> SHOWN = new HashMap<>();
        private static final Map<String, String> TITLES = new HashMap<>();
        private static final Map<String, String> STYLES = new HashMap<>();
        private static IClientAPI api;
        private static boolean enabled = true;
        private static boolean labelsEnabled = true;
        private static boolean minimapEnabled = false;
        private static boolean textureMode = true;
        private static ResourceLocationKey lastDimension;
        private static String lastViewKey = "";
        private static int lastStructureVersion = -1;
        private static int lastDataVersion = -1;
        private static boolean lastMinimapEnabled;
        private static VeinGroup hoveredGroup;
        private static ResourceLocation hoveredDimension;

        private VeinOverlayManagerV2() {
        }

        static void init(IClientAPI clientApi) {
            api = clientApi;
            enabled = VPConfig.CLIENT.showOverlay().get();
            // Persistent map labels are opt-in: at most zoom levels they overlap the markers and
            // their anchor is the polygon centre, which makes them look shifted downward.
            labelsEnabled = false;
            // Discovered veins should appear on the minimap immediately, not only after opening
            // the fullscreen map. The menu can still turn minimap rendering off.
            minimapEnabled = true;
            lastMinimapEnabled = true;
            textureMode = VPConfig.CLIENT.textureMarkers().get();
        }

        public static boolean isTextureMode() {
            return textureMode;
        }

        public static void setTextureMode(boolean value) {
            if (textureMode == value) {
                return;
            }
            textureMode = value;
            removeAllOverlays();
            markDirty();
            refreshCurrent();
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
            lastDataVersion = -1;
            hoveredGroup = null;
            hoveredDimension = null;
        }

        private static void removeAllOverlays() {
            if (api != null) {
                for (Overlay overlay : SHOWN.values()) {
                    api.remove(overlay);
                }
            }
            SHOWN.clear();
            TITLES.clear();
            STYLES.clear();
        }

        private static void refreshCurrent() {
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

        public static void refresh(@Nullable UIState state) {
            if (api == null) {
                return;
            }
            if (!enabled) {
                removeAllOverlays();
                return;
            }
            // Keep overlays cached while the map is closed. Rebuilding every overlay on open was
            // the main cause of the visible delay before the layer appeared.
            if (state == null || !state.active || state.dimension == null) {
                return;
            }
            if (Minecraft.getInstance().level == null) {
                clear();
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
            boolean dataChanged = data.version() != lastDataVersion;
            boolean minimapChanged = minimapEnabled != lastMinimapEnabled;
            if (dimensionChanged) {
                removeAllOverlays();
            }
            lastDimension = current;
            lastViewKey = viewKey;
            lastStructureVersion = data.structureVersion();
            lastDataVersion = data.version();
            lastMinimapEnabled = minimapEnabled;
            syncVisible(state, data, dimensionChanged || viewChanged || structureChanged
                    || dataChanged || minimapChanged);
        }

        private static void syncVisible(UIState state, ClientVeinCache.DimensionData data,
                                        boolean refreshText) {
            List<VeinGroup> visible = visibleGroups(state, data);
            Set<String> keep = new HashSet<>();
            for (VeinGroup group : visible) {
                keep.add(group.key());
            }
            for (Iterator<Map.Entry<String, Overlay>> iterator = SHOWN.entrySet().iterator(); iterator.hasNext(); ) {
                Map.Entry<String, Overlay> entry = iterator.next();
                if (!keep.contains(entry.getKey())) {
                    api.remove(entry.getValue());
                    iterator.remove();
                    TITLES.remove(entry.getKey());
                    STYLES.remove(entry.getKey());
                }
            }
            for (VeinGroup group : visible) {
                if (!SHOWN.containsKey(group.key())) {
                    showOverlay(state, group);
                }
            }
            if (refreshText) {
                updateTitles(data, state);
            }
        }

        private static void showOverlay(UIState state, VeinGroup group) {
            Overlay overlay = createOverlay(state, group);
            if (overlay == null) {
                return;
            }
            try {
                api.show(overlay);
                SHOWN.put(group.key(), overlay);
                TITLES.put(group.key(), group.title());
                STYLES.put(group.key(), styleKey(group.primary()));
            } catch (Throwable throwable) {
                VisualVein.LOGGER.warn("Could not show JourneyMap overlay {}", group.key(), throwable);
            }
        }

        private static void updateTitles(ClientVeinCache.DimensionData data, UIState state) {
            if (SHOWN.isEmpty()) {
                return;
            }
            Map<String, VeinGroup> groups = groupMap(data, state);
            for (Map.Entry<String, Overlay> entry : SHOWN.entrySet()) {
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
                    TITLES.put(entry.getKey(), title);
                }
                String style = styleKey(group.primary());
                if (!style.equals(STYLES.get(entry.getKey()))) {
                    applyStyle(entry.getValue(), group.primary());
                    STYLES.put(entry.getKey(), style);
                }
                entry.getValue().flagForRerender();
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

        private static final Map<String, ResourceLocation> TEXTURE_CACHE = new HashMap<>();

        private static ResourceLocation textureFor(VeinRecord record) {
            String key = record.ores.isEmpty() ? "" : record.ores.get(0).id;
            return TEXTURE_CACHE.computeIfAbsent(key, ignored -> resolveTexture(record));
        }

        private static final Set<String> AVAILABLE_ORE_ICONS = Set.of(
                "diamond", "dull", "fine", "flint", "lapis", "lignite", "paper", "powder", "quartz", "rough");

        private static ResourceLocation resolveTexture(VeinRecord record) {
            for (com.visualvein.data.OreEntry ore : record.ores) {
                ResourceLocation blockId = ResourceLocation.tryParse(ore.id);
                if (blockId == null || !BuiltInRegistries.BLOCK.containsKey(blockId)) {
                    continue;
                }
                try {
                    Block block = BuiltInRegistries.BLOCK.get(blockId);
                    MaterialStack stack = ChemicalHelper.getMaterial(block);
                    String iconSet = stack == null || stack.material().getMaterialIconSet() == null
                            ? "dull" : stack.material().getMaterialIconSet().name;
                    if (!AVAILABLE_ORE_ICONS.contains(iconSet)) {
                        iconSet = "dull";
                    }
                    return new ResourceLocation(VisualVein.MODID, "textures/oresets/" + iconSet + ".png");
                } catch (Throwable ignored) {
                    // Fall through to the generic ore icon.
                }
            }
            return new ResourceLocation(VisualVein.MODID, "textures/gui/ore_icon.png");
        }

        private static int tintFor(VeinRecord record) {
            if (record.depleted) {
                return 0xFF6666;
            }
            for (com.visualvein.data.OreEntry ore : record.ores) {
                ResourceLocation blockId = ResourceLocation.tryParse(ore.id);
                if (blockId == null || !BuiltInRegistries.BLOCK.containsKey(blockId)) {
                    continue;
                }
                try {
                    Block block = BuiltInRegistries.BLOCK.get(blockId);
                    MaterialStack stack = ChemicalHelper.getMaterial(block);
                    Material material = stack == null ? null : stack.material();
                    if (material != null) {
                        return material.getMaterialRGB();
                    }
                } catch (Throwable ignored) {
                    // Use the stored fallback color.
                }
            }
            return record.ores.isEmpty() ? 0xFFFFFF : record.ores.get(0).color;
        }

        private static ShapeProperties shapeFor(VeinRecord record) {
            int color = record.depleted ? 0x8A2B2B
                    : (record.ores.isEmpty() ? 0x888888 : record.ores.get(0).color);
            if (color == 0) {
                color = 0x888888;
            }
            return new ShapeProperties()
                    .setFillColor(color)
                    .setFillOpacity(VPConfig.CLIENT.fillOpacity().get().floatValue())
                    .setStrokeColor(record.depleted ? 0xFF5555 : 0xFFFFFF)
                    .setStrokeOpacity(0.9F)
                    .setStrokeWidth(record.depleted ? 2.5F : 1.5F);
        }

        private static String styleKey(VeinRecord record) {
            return (record.depleted ? "D" : "A") + ":" + textureMode + ":" + textureFor(record)
                    + ":" + tintFor(record) + ":" + (minimapEnabled ? "M" : "F");
        }

        private static void applyStyle(Overlay overlay, VeinRecord record) {
            if (overlay instanceof MarkerOverlay marker) {
                MapImage icon = marker.getIcon();
                icon.setColor(tintFor(record));
                marker.setIcon(icon);
            } else if (overlay instanceof PolygonOverlay polygon) {
                polygon.setShapeProperties(shapeFor(record));
            }
            overlay.setDisplayOrder(record.depleted ? 110 : 100);
            if (minimapEnabled) {
                overlay.setActiveUIs(Context.UI.Fullscreen, Context.UI.Minimap);
            } else {
                overlay.setActiveUIs(Context.UI.Fullscreen);
            }
        }

        @Nullable
        private static Overlay createOverlay(UIState state, VeinGroup group) {
            VeinRecord record = group.primary();
            Overlay overlay;
            if (textureMode) {
                MapImage icon = new MapImage(textureFor(record), 16, 16)
                        .setDisplayWidth(24)
                        .setDisplayHeight(24)
                        .setColor(tintFor(record));
                icon.centerAnchors();
                overlay = new MarkerOverlay(VisualVein.MODID,
                        new BlockPos(record.centerX, record.centerY, record.centerZ), icon);
            } else {
                int x = record.centerX;
                int y = record.centerY;
                int z = record.centerZ;
                MapPolygon polygon = new MapPolygon(
                        new BlockPos(x - 8, y, z - 8),
                        new BlockPos(x + 8, y, z - 8),
                        new BlockPos(x + 8, y, z + 8),
                        new BlockPos(x - 8, y, z + 8));
                overlay = new PolygonOverlay(VisualVein.MODID, state.dimension, shapeFor(record), polygon);
            }

            TextProperties text = new TextProperties()
                    .setColor(0xFFFFFF)
                    .setBackgroundColor(0x101010)
                    .setBackgroundOpacity(0.55F)
                    .setMinZoom(VPConfig.CLIENT.labelMinZoom().get())
                    .setOffsetY(-40)
                    .setScale(1.0F);
            text.setActiveMapTypes(Context.MapType.values());

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
                public void onMouseMove(UIState uiState, java.awt.geom.Point2D.Double mouse, BlockPos pos) {
                    hoveredGroup = group;
                    hoveredDimension = uiState.dimension.location();
                }

                @Override
                public void onMouseOut(UIState uiState, java.awt.geom.Point2D.Double mouse, BlockPos pos) {
                    if (hoveredGroup == group) {
                        hoveredGroup = null;
                    }
                }

                @Override
                public boolean onMouseClick(UIState uiState, java.awt.geom.Point2D.Double mouse, BlockPos pos,
                                            int button, boolean doubleClick) {
                    if (button == 0 && doubleClick) {
                        toggleWaypoint(uiState, group);
                        return true;
                    }
                    return false;
                }

                @Override
                public void onOverlayMenuPopup(UIState uiState, java.awt.geom.Point2D.Double mouse, BlockPos pos,
                                               journeymap.api.v2.client.fullscreen.ModPopupMenu menu) {
                    menu.addMenuItem(Component.translatable(record.depleted
                            ? "vp.menu.mark_active" : "vp.menu.mark_depleted").getString(), clicked -> {
                        VPNetwork.sendToServer(new SetVeinStatusPacket(
                                uiState.dimension.location(), record.key(), !record.depleted));
                    });
                    boolean hasWaypoint = findWaypoint(uiState.dimension, waypointName(record)) != null;
                    menu.addMenuItem(Component.translatable(hasWaypoint
                                    ? "vp.menu.remove_waypoint" : "vp.menu.create_waypoint").getString(),
                            clicked -> toggleWaypoint(uiState, group));
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
                message(Component.translatable("vp.msg.no_data"));
                return;
            }
            VeinRecord record = null;
            double best = Double.MAX_VALUE;
            for (VeinRecord candidate : data.all()) {
                if (!candidate.marked) {
                    continue;
                }
                double dx = candidate.centerX - minecraft.player.getX();
                double dz = candidate.centerZ - minecraft.player.getZ();
                double distance = dx * dx + dz * dz;
                if (distance < best) {
                    best = distance;
                    record = candidate;
                }
            }
            if (record == null) {
                message(Component.translatable("vp.msg.no_data"));
                return;
            }
            String name = waypointName(record);
            if (findWaypoint(minecraft.level.dimension(), name) != null) {
                message(Component.translatable("vp.msg.waypoint_exists", name));
                return;
            }
            addWaypoint(minecraft.level.dimension(), record);
        }

        public static void toggleHoveredStatus() {
            if (hoveredGroup == null || hoveredDimension == null) {
                return;
            }
            VeinRecord record = hoveredGroup.primary();
            VPNetwork.sendToServer(new SetVeinStatusPacket(
                    hoveredDimension, record.key(), !record.depleted));
            message(Component.translatable(record.depleted ? "vp.msg.restored" : "vp.msg.depleted"));
        }

        private static String waypointName(VeinRecord record) {
            return "VP: " + VeinNames.veinName(record.veinId).getString()
                    + " [" + record.centerX + "," + record.centerZ + "]";
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
                VisualVein.LOGGER.debug("Could not list JourneyMap waypoints", throwable);
            }
            return null;
        }

        private static boolean addWaypoint(ResourceKey<Level> dimension, VeinRecord record) {
            if (api == null || dimension == null) {
                return false;
            }
            String name = waypointName(record);
            Waypoint waypoint = WaypointFactory.createWaypoint(
                    VisualVein.MODID,
                    new BlockPos(record.centerX, record.centerY, record.centerZ),
                    name,
                    dimension,
                    true);
            waypoint.setColor(record.depleted ? 0xAA3333 : 0xFFAA00);
            waypoint.setShowBeacon(true);
            try {
                api.addWaypoint(VisualVein.MODID, waypoint);
                message(Component.translatable("vp.msg.waypoint_created", name));
                return true;
            } catch (Throwable throwable) {
                VisualVein.LOGGER.warn("Could not create JourneyMap waypoint", throwable);
                return false;
            }
        }

        private static void toggleWaypoint(UIState state, VeinGroup group) {
            if (api == null || state.dimension == null) {
                return;
            }
            VeinRecord record = group.primary();
            String name = waypointName(record);
            Waypoint existing = findWaypoint(state.dimension, name);
            if (existing != null) {
                try {
                    api.removeWaypoint(VisualVein.MODID, existing);
                    message(Component.translatable("vp.msg.waypoint_removed", name));
                } catch (Throwable throwable) {
                    VisualVein.LOGGER.warn("Could not remove JourneyMap waypoint", throwable);
                }
            } else {
                addWaypoint(state.dimension, record);
            }
        }

        private static void message(Component component) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(component, false);
            }
        }

        private record ResourceLocationKey(ResourceLocation location) {
        }
    }
}
