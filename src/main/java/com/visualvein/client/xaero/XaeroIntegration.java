package com.visualvein.client.xaero;

import com.visualvein.VisualVein;
import com.visualvein.client.ClientFluidCache;
import com.visualvein.client.ClientHooks;
import com.visualvein.client.ClientVeinCache;
import com.visualvein.data.FluidVeinRecord;
import com.visualvein.data.VeinGroup;
import com.visualvein.data.VeinNames;
import com.visualvein.data.VeinRecord;
import com.visualvein.network.SetVeinStatusPacket;
import com.visualvein.network.VPNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.ModList;
import xaero.common.HudMod;
import xaero.map.WorldMap;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers the VisualVein element layer with Xaero's World Map. The minimap renderer is added once
 * its handler is available; registration is retried from the client tick.
 */
public final class XaeroIntegration {

    private static final List<XaeroVeinMarker> MARKERS = new ArrayList<>();
    private static final List<XaeroFluidMarker> FLUID_MARKERS = new ArrayList<>();
    private static final List<XaeroFluidMarker> FLUID_MINIMAP = new ArrayList<>();
    private static boolean registered;
    private static boolean minimapRegistered;
    private static boolean hooksInitialized;
    private static XaeroVeinMarker hovered;
    private static XaeroFluidMarker hoveredFluid;
    private static int lastMarkerCount = -1;
    private static int lastFluidCount = -1;
    private static long lastVeinVersion = -1;
    private static long lastFluidVersion = -1;
    private static ResourceLocation lastDimension;

    private XaeroIntegration() {
    }

    public static List<XaeroVeinMarker> markers() {
        return MARKERS;
    }

    public static List<XaeroFluidMarker> fluidMarkers() {
        return FLUID_MARKERS;
    }

    public static List<XaeroFluidMarker> fluidMinimapMarkers() {
        return FLUID_MINIMAP;
    }

    public static void tick() {
        if (!ModList.get().isLoaded("xaeroworldmap") || !ModList.get().isLoaded("xaerominimap")) {
            return;
        }
        if (!hooksInitialized) {
            initHooks();
            hooksInitialized = true;
        }
        XaeroSearchBox.tick();
        if (!registered) {
            try {
                WorldMap.mapElementRenderHandler.add(new XaeroFluidRenderer());
                WorldMap.mapElementRenderHandler.add(new XaeroVeinRenderer());
                registered = true;
                VisualVein.LOGGER.info("VisualVein Xaero World Map layer registered");
            } catch (Throwable throwable) {
                VisualVein.LOGGER.warn("Could not register Xaero World Map layer", throwable);
            }
        }
        if (!minimapRegistered) {
            try {
                if (HudMod.INSTANCE != null && HudMod.INSTANCE.getMinimap() != null) {
                    HudMod.INSTANCE.getMinimap().getOverMapRendererHandler()
                            .add(new XaeroFluidMinimapRenderer());
                    HudMod.INSTANCE.getMinimap().getOverMapRendererHandler()
                            .add(new XaeroMinimapRenderer());
                    minimapRegistered = true;
                    VisualVein.LOGGER.info("VisualVein Xaero Minimap layer registered");
                }
            } catch (Throwable throwable) {
                VisualVein.LOGGER.warn("Could not register Xaero Minimap layer", throwable);
            }
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        ResourceLocation dimension = minecraft.level.dimension().location();
        long veinVersion = ClientVeinCache.version();
        long fluidVersion = ClientFluidCache.version();
        if (!dimension.equals(lastDimension) || veinVersion != lastVeinVersion
                || fluidVersion != lastFluidVersion) {
            lastDimension = dimension;
            lastVeinVersion = veinVersion;
            lastFluidVersion = fluidVersion;
            rebuild();
        }
    }

    public static void rebuild() {
        MARKERS.clear();
        FLUID_MARKERS.clear();
        FLUID_MINIMAP.clear();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        ResourceLocation dimension = minecraft.level.dimension().location();
        lastDimension = dimension;
        lastVeinVersion = ClientVeinCache.version();
        lastFluidVersion = ClientFluidCache.version();
        ClientVeinCache.DimensionData data = ClientVeinCache.get(dimension);
        if (data != null) {
            List<VeinRecord> visible = new ArrayList<>();
            for (VeinRecord record : data.all()) {
                if (record.marked) {
                    visible.add(record);
                }
            }
            for (VeinGroup group : VeinGroup.group(visible, false, null)) {
                VeinRecord primary = group.primary();
                String name = VeinNames.veinName(primary.veinId).getString();
                String title = group.title();
                MARKERS.add(XaeroVeinMarker.of(group, name, title));
            }
        }

        int regions = 0;
        for (FluidVeinRecord record : ClientFluidCache.get(dimension)) {
            if (regions++ >= 8) {
                break;
            }
            // A fully exhausted fluid vein must not leave a depleted tint behind.
            if (record.operations() <= 0) {
                continue;
            }
            Fluid fluid = BuiltInRegistries.FLUID.get(record.fluidId());
            String name = fluid == null ? record.fluidId().toString()
                    : new FluidStack(fluid, 1).getDisplayName().getString();
            int color = fluidColor(record.fluidId());
            String regionTitle = name + "\n"
                    + Component.translatable("vp.fluid.tooltip.region",
                            record.centerX(), record.centerZ()).getString()
                    + "\n" + Component.translatable("vp.fluid.tooltip.yield", record.yield()).getString()
                    + "\n" + Component.translatable("vp.fluid.tooltip.operations",
                            record.operations(), record.maxOperations()).getString();
            FLUID_MINIMAP.add(new XaeroFluidMarker(record.key(), record, record.centerX(),
                    record.centerZ(), color, name, regionTitle));
            int[] yields = record.chunkYields();
            for (int dx = 0; dx < 8; dx++) {
                for (int dz = 0; dz < 8; dz++) {
                    int chunkX = record.regionX() * 8 + dx;
                    int chunkZ = record.regionZ() * 8 + dz;
                    int index = dx * 8 + dz;
                    int yield = index < yields.length ? yields[index] : record.yield();
                    String title = name + "\n"
                            + Component.translatable("vp.fluid.tooltip.chunk", chunkX, chunkZ).getString()
                            + "\n" + Component.translatable("vp.fluid.tooltip.chunk_yield", yield).getString()
                            + "\n" + Component.translatable("vp.fluid.tooltip.yield", record.yield()).getString()
                            + "\n" + Component.translatable("vp.fluid.tooltip.operations",
                                    record.operations(), record.maxOperations()).getString();
                    FLUID_MARKERS.add(new XaeroFluidMarker(
                            record.key() + ":" + dx + ":" + dz, record,
                            chunkX * 16 + 8, chunkZ * 16 + 8, color, name, title));
                }
            }
        }
    }

    private static int fluidColor(ResourceLocation id) {
        int hash = id.hashCode();
        int red = 64 + Math.abs((hash >> 16) & 0x7F);
        int green = 64 + Math.abs((hash >> 8) & 0x7F);
        int blue = 64 + Math.abs(hash & 0x7F);
        return (red << 16) | (green << 8) | blue;
    }

    public static void setHovered(XaeroVeinMarker marker) {
        hovered = marker;
    }

    public static XaeroVeinMarker hovered() {
        return hovered;
    }

    public static void setHoveredFluid(XaeroFluidMarker marker) {
        hoveredFluid = marker;
    }

    public static XaeroFluidMarker hoveredFluid() {
        return hoveredFluid;
    }

    public static void initHooks() {
        ClientHooks.dataChanged = XaeroIntegration::rebuild;
        // Search is read live by the renderers; rebuilding on every keystroke causes needless
        // allocation and frame-time spikes on the minimap.
        ClientHooks.searchChanged = () -> {
        };
        ClientHooks.createWaypoint = XaeroWaypointBridge::createNearest;
        ClientHooks.overlayEnabled = () -> true;
        ClientHooks.fluidEnabled = () -> false;
        ClientHooks.labelsEnabled = () -> false;
        ClientHooks.minimapEnabled = () -> true;
        ClientHooks.textureMarkers = () -> false;
        ClientHooks.toggleHoveredStatus = () -> {
            if (hovered == null || Minecraft.getInstance().level == null) {
                return;
            }
            VeinRecord record = hovered.group().primary();
            VPNetwork.sendToServer(new SetVeinStatusPacket(
                    Minecraft.getInstance().level.dimension().location(), record.key(), !record.depleted));
        };
    }
}
