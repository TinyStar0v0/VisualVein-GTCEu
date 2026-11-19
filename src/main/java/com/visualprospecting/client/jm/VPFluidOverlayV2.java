package com.visualprospecting.client.jm;

import com.visualprospecting.VisualProspecting;
import com.visualprospecting.client.ClientFluidCache;
import com.visualprospecting.client.ClientVeinCache;
import com.visualprospecting.config.VPConfig;
import com.visualprospecting.data.FluidVeinRecord;
import com.visualprospecting.data.VeinRecord;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.model.MapPolygon;
import journeymap.api.v2.client.model.ShapeProperties;
import journeymap.api.v2.client.model.TextProperties;
import journeymap.api.v2.client.util.UIState;
import journeymap.api.v2.common.Context;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
    }

    public static void refresh(@Nullable UIState state) {
        if (api == null) {
            return;
        }
        if (!enabled || state == null || !state.active || state.dimension == null) {
            removeAll();
            return;
        }
        ResourceLocation dimension = state.dimension.location();
        long version = ClientFluidCache.version();
        boolean dimensionChanged = !dimension.toString().equals(lastDimension);
        if (dimensionChanged || version != lastVersion) {
            lastDimension = dimension.toString();
            lastVersion = version;
            removeAll();
        }

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
                    } catch (Throwable throwable) {
                        VisualProspecting.LOGGER.warn("Could not show fluid overlay {}", record.key(), throwable);
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

        PolygonOverlay overlay = new PolygonOverlay(VisualProspecting.MODID, state.dimension, shape, polygon);
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
        return overlay;
    }

    private static String title(FluidVeinRecord record, String name) {
        return name + "\n"
                + "流体矿脉区域: " + record.centerX() + ", " + record.centerZ() + "\n"
                + "当前产量: " + record.yield() + " L/t\n"
                + "枯竭产量: " + record.depletedYield() + " L/t\n"
                + "剩余开采次数: " + record.operations() + " / " + record.maxOperations() + "\n"
                + "状态: " + (record.depleted() ? "已枯竭" : "可开采");
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
