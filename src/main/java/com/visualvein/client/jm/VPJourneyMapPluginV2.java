package com.visualvein.client.jm;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialStack;
import com.visualvein.VisualVein;
import com.visualvein.client.ClientHooks;
import com.visualvein.client.MapSearch;
import com.visualvein.client.VPMapSearch;
import com.visualvein.client.WaypointSurface;
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
import journeymap.api.v2.client.display.ImageOverlay;
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
import net.minecraftforge.common.MinecraftForge;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import com.mojang.blaze3d.platform.NativeImage;
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
        ClientHooks.searchChanged = () -> {
            VeinOverlayManagerV2.applySearch();
            VPFluidOverlayV2.applySearch();
        };
        MinecraftForge.EVENT_BUS.addListener(VPMapSearch::clientTick);
        FullscreenEventRegistry.FULLSCREEN_RENDER_EVENT.subscribe(VisualVein.MODID, VPMapSearch::render);

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
                applySearchStyle(overlay, group, state);
            } catch (Throwable throwable) {
                VisualVein.LOGGER.warn("Could not show JourneyMap overlay {}", group.key(), throwable);
            }
        }

        private static void updateTitles(ClientVeinCache.DimensionData data, UIState state) {
            if (SHOWN.isEmpty()) {
                return;
            }
            Map<String, VeinGroup> groups = groupMap(data, state);
            // Iterate a snapshot: a texture style change recreates the ImageOverlay, which mutates
            // SHOWN and would otherwise throw ConcurrentModificationException.
            for (Map.Entry<String, Overlay> entry : new ArrayList<>(SHOWN.entrySet())) {
                VeinGroup group = groups.get(entry.getKey());
                if (group == null) {
                    continue;
                }
                String style = styleKey(group.primary());
                if (!style.equals(STYLES.get(entry.getKey()))) {
                    if (textureMode && entry.getValue() instanceof ImageOverlay) {
                        // ImageOverlay caches its image in the JourneyMap draw step. Replacing the
                        // MapImage alone is not enough on 6.0.7, so recreate the overlay to make
                        // the depleted cross appear/disappear immediately.
                        api.remove(entry.getValue());
                        SHOWN.remove(entry.getKey());
                        TITLES.remove(entry.getKey());
                        STYLES.remove(entry.getKey());
                        showOverlay(state, group);
                        continue;
                    }
                    applyStyle(entry.getValue(), group.primary(), state.dimension.location());
                    STYLES.put(entry.getKey(), style);
                }
                String title = group.title();
                String label = labelsEnabled ? group.label() : null;
                if (!title.equals(TITLES.get(entry.getKey()))
                        || !java.util.Objects.equals(label, entry.getValue().getLabel())) {
                    entry.getValue().setTitle(title);
                    entry.getValue().setLabel(label);
                    TITLES.put(entry.getKey(), title);
                }
                applySearchStyle(entry.getValue(), group, state);
                entry.getValue().flagForRerender();
            }
        }

        public static void applySearch() {
            if (api == null) {
                return;
            }
            UIState state = api.getUIState(Context.UI.Fullscreen);
            if (state == null || !state.active) {
                state = api.getUIState(Context.UI.Minimap);
            }
            if (state == null || !state.active || state.dimension == null) {
                return;
            }
            ClientVeinCache.DimensionData data = ClientVeinCache.get(state.dimension.location());
            if (data == null) {
                return;
            }
            Map<String, VeinGroup> groups = groupMap(data, state);
            for (Map.Entry<String, Overlay> entry : new ArrayList<>(SHOWN.entrySet())) {
                VeinGroup group = groups.get(entry.getKey());
                if (group != null) {
                    applySearchStyle(entry.getValue(), group, state);
                    entry.getValue().flagForRerender();
                }
            }
        }

        private static void applySearchStyle(Overlay overlay, VeinGroup group, UIState state) {
            // Search is shared by the fullscreen map and the minimap.
            boolean search = MapSearch.active();
            boolean match = !search || MapSearch.matchesOre(group);
            if (overlay instanceof ImageOverlay image) {
                MapImage icon = image.getImage();
                icon.setOpacity(match ? 1.0F : 0.15F);
                icon.setColor(match ? 0xFFFFFF : 0x777777);
            } else if (overlay instanceof PolygonOverlay polygon) {
                polygon.setShapeProperties(shapeFor(group.primary(), search ? match : null));
            }
            overlay.setDisplayOrder(search && match ? 130 : (group.primary().depleted ? 110 : 100));
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
                    // Loaded from the installed GTCEu jar at runtime; no third-party texture
                    // is redistributed inside VisualVein.
                    return new ResourceLocation("gtceu", "textures/block/material_sets/" + iconSet + "/ore.png");
                } catch (Throwable ignored) {
                    // Fall through to the generic ore icon.
                }
            }
            return new ResourceLocation(VisualVein.MODID, "textures/gui/ore_icon.png");
        }

        private static MapImage mapImageFor(VeinRecord record, ResourceLocation dimension) {
            ResourceLocation texture = textureFor(record);
            ResourceLocation baseTexture = baseTextureFor(record, dimension);
            int tint = tintFor(record);
            NativeImage base = loadNative(baseTexture);
            NativeImage ore = loadNative(texture);
            if (base != null || ore != null) {
                NativeImage composite = composeMarker(base, ore, tint, record.depleted);
                if (base != null) {
                    base.close();
                }
                if (ore != null) {
                    ore.close();
                }
                if (composite != null) {
                    return new MapImage(composite).setColor(0xFFFFFF);
                }
            }
            return new MapImage(texture, 16, 16).setColor(tint);
        }

        @Nullable
        private static NativeImage loadNative(ResourceLocation texture) {
            try {
                var resource = Minecraft.getInstance().getResourceManager().getResource(texture).orElse(null);
                if (resource != null) {
                    try (var input = resource.open()) {
                        return NativeImage.read(input);
                    }
                }
            } catch (Throwable throwable) {
                VisualVein.LOGGER.debug("Could not load marker texture {}", texture, throwable);
            }
            return null;
        }

        @Nullable
        private static NativeImage composeMarker(@Nullable NativeImage base, @Nullable NativeImage ore,
                                                 int tint, boolean depleted) {
            try {
                NativeImage output = new NativeImage(16, 16, false);
                int tintR = (tint >> 16) & 0xFF;
                int tintG = (tint >> 8) & 0xFF;
                int tintB = tint & 0xFF;
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        int basePixel = base == null ? 0 : base.getPixelRGBA(x, y);
                        int baseA = (basePixel >>> 24) & 0xFF;
                        // NativeImage stores RGBA bytes; the int read by getPixelRGBA is ABGR.
                        int baseB = (basePixel >> 16) & 0xFF;
                        int baseG = (basePixel >> 8) & 0xFF;
                        int baseR = basePixel & 0xFF;
                        if (ore == null) {
                            output.setPixelRGBA(x, y, boostPixel(basePixel));
                            continue;
                        }
                        int orePixel = ore.getPixelRGBA(x, y);
                        int oreA = (orePixel >>> 24) & 0xFF;
                        int oreR = (orePixel & 0xFF) * tintR / 255;
                        int oreG = ((orePixel >> 8) & 0xFF) * tintG / 255;
                        int oreB = ((orePixel >> 16) & 0xFF) * tintB / 255;
                        int alpha = oreA;
                        int outR = (oreR * alpha + baseR * (255 - alpha)) / 255;
                        int outG = (oreG * alpha + baseG * (255 - alpha)) / 255;
                        int outB = (oreB * alpha + baseB * (255 - alpha)) / 255;
                        int outA = Math.max(baseA, alpha);
                        output.setPixelRGBA(x, y, (outA << 24)
                                | (boost(outB) << 16) | (boost(outG) << 8) | boost(outR));
                    }
                }
                drawMarkerBorder(output);
                if (depleted) {
                    drawDepletedCross(output);
                }
                return output;
            } catch (Throwable throwable) {
                VisualVein.LOGGER.debug("Could not compose ore marker", throwable);
                return null;
            }
        }

        private static int boost(int channel) {
            return Math.min(255, (channel * 5) / 4 + 24);
        }

        private static int boostPixel(int pixel) {
            int alpha = (pixel >>> 24) & 0xFF;
            int blue = (pixel >> 16) & 0xFF;
            int green = (pixel >> 8) & 0xFF;
            int red = pixel & 0xFF;
            return (alpha << 24) | (boost(blue) << 16) | (boost(green) << 8) | boost(red);
        }

        private static void drawMarkerBorder(NativeImage image) {
            // A light frame keeps the marker readable over dark forest/stone map tiles.
            int border = 0xFFD8E0E0;
            for (int i = 0; i < 16; i++) {
                image.setPixelRGBA(i, 0, border);
                image.setPixelRGBA(i, 15, border);
                image.setPixelRGBA(0, i, border);
                image.setPixelRGBA(15, i, border);
            }
        }

        private static void drawDepletedCross(NativeImage image) {
            // ABGR int layout, matching getPixelRGBA/setPixelRGBA.
            int red = 0xFF3030FF;
            for (int i = 0; i < 16; i++) {
                image.setPixelRGBA(i, i, red);
                image.setPixelRGBA(i, 15 - i, red);
                if (i < 15) {
                    image.setPixelRGBA(i + 1, i, red);
                    image.setPixelRGBA(i + 1, 15 - i, red);
                }
            }
        }

        private static ResourceLocation baseTextureFor(VeinRecord record, ResourceLocation dimension) {
            for (com.visualvein.data.OreEntry ore : record.ores) {
                String id = ore.id.toLowerCase();
                if (id.contains("deepslate")) {
                    return new ResourceLocation("minecraft", "textures/block/deepslate.png");
                }
                if (id.contains("netherrack")) {
                    return new ResourceLocation("minecraft", "textures/block/netherrack.png");
                }
                if (id.contains("end_stone") || id.contains("endstone")) {
                    return new ResourceLocation("minecraft", "textures/block/end_stone.png");
                }
            }
            String path = dimension.getPath();
            if (path.contains("nether")) {
                return new ResourceLocation("minecraft", "textures/block/netherrack.png");
            }
            if (path.contains("end")) {
                return new ResourceLocation("minecraft", "textures/block/end_stone.png");
            }
            // Overworld stone, and the default for modded planets/dimensions.
            return new ResourceLocation("minecraft", "textures/block/stone.png");
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
            return shapeFor(record, null);
        }

        private static ShapeProperties shapeFor(VeinRecord record, @Nullable Boolean searchMatch) {
            int color = record.depleted ? 0x8A2B2B
                    : (record.ores.isEmpty() ? 0x888888 : record.ores.get(0).color);
            if (color == 0) {
                color = 0x888888;
            }
            float fill = VPConfig.CLIENT.fillOpacity().get().floatValue();
            int strokeColor = record.depleted ? 0xFF5555 : 0xFFFFFF;
            float strokeOpacity = 0.9F;
            float strokeWidth = record.depleted ? 2.5F : 1.5F;
            if (searchMatch != null) {
                if (searchMatch) {
                    fill = Math.max(fill, 0.55F);
                    strokeColor = 0xFFD700;
                    strokeOpacity = 1.0F;
                    strokeWidth = 2.5F;
                } else {
                    fill = 0.05F;
                    strokeColor = 0x555555;
                    strokeOpacity = 0.2F;
                    strokeWidth = 0.5F;
                }
            }
            return new ShapeProperties()
                    .setFillColor(color)
                    .setFillOpacity(fill)
                    .setStrokeColor(strokeColor)
                    .setStrokeOpacity(strokeOpacity)
                    .setStrokeWidth(strokeWidth);
        }

        private static String styleKey(VeinRecord record) {
            return (record.depleted ? "D" : "A") + ":" + textureMode + ":" + textureFor(record)
                    + ":" + tintFor(record) + ":" + (minimapEnabled ? "M" : "F");
        }

        private static void applyStyle(Overlay overlay, VeinRecord record, ResourceLocation dimension) {
            if (overlay instanceof ImageOverlay image) {
                // Rebuild the composite so a depleted vein gets its red cross, and a restored
                // vein loses it, without recreating the JourneyMap overlay itself.
                image.setImage(mapImageFor(record, dimension));
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
                MapImage icon = mapImageFor(record, state.dimension.location());
                icon.centerAnchors();
                ImageOverlay image = new ImageOverlay(VisualVein.MODID,
                        new BlockPos(record.centerX - 4, record.centerY, record.centerZ - 4),
                        new BlockPos(record.centerX + 4, record.centerY, record.centerZ + 4),
                        icon);
                image.setDimension(state.dimension);
                overlay = image;
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
                    VeinRecord current = currentRecord(uiState.dimension.location(), record);
                    menu.addMenuItem(Component.translatable(current.depleted
                            ? "vp.menu.mark_active" : "vp.menu.mark_depleted").getString(), clicked -> {
                        VeinRecord latest = currentRecord(uiState.dimension.location(), record);
                        VPNetwork.sendToServer(new SetVeinStatusPacket(
                                uiState.dimension.location(), latest.key(), !latest.depleted));
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
            VeinRecord record = currentRecord(hoveredDimension, hoveredGroup.primary());
            VPNetwork.sendToServer(new SetVeinStatusPacket(
                    hoveredDimension, record.key(), !record.depleted));
            message(Component.translatable(record.depleted ? "vp.msg.restored" : "vp.msg.depleted"));
        }

        private static VeinRecord currentRecord(ResourceLocation dimension, VeinRecord fallback) {
            ClientVeinCache.DimensionData data = ClientVeinCache.get(dimension);
            if (data != null) {
                VeinRecord current = data.get(fallback.key());
                if (current != null) {
                    return current;
                }
            }
            return fallback;
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
            int y = WaypointSurface.y(dimension, record);
            Waypoint waypoint = WaypointFactory.createWaypoint(
                    VisualVein.MODID,
                    new BlockPos(record.centerX, y, record.centerZ),
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
