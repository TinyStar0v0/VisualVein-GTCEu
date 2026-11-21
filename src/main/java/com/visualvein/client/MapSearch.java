package com.visualvein.client;

import com.visualvein.data.FluidVeinRecord;
import com.visualvein.data.OreEntry;
import com.visualvein.data.VeinGroup;
import com.visualvein.data.VeinNames;
import com.visualvein.data.VeinRecord;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

import java.util.Locale;

/**
 * Fullscreen-map search state. The query is intentionally client-only and is never sent to the
 * server or applied to the minimap.
 */
public final class MapSearch {

    private static String query = "";

    private MapSearch() {
    }

    public static String query() {
        return query;
    }

    public static boolean active() {
        return !query.isBlank();
    }

    public static void setQuery(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.equals(query)) {
            return;
        }
        query = normalized;
        ClientHooks.searchChanged.run();
    }

    public static void clear() {
        if (query.isEmpty()) {
            return;
        }
        query = "";
        ClientHooks.searchChanged.run();
    }

    public static boolean matchesOre(VeinGroup group) {
        if (!active()) {
            return true;
        }
        String needle = query.toLowerCase(Locale.ROOT);
        for (VeinRecord record : group.layers()) {
            if (matches(needle, VeinNames.veinName(record.veinId).getString())
                    || matches(needle, record.veinId.toString())) {
                return true;
            }
            for (OreEntry ore : record.ores) {
                if (matchesOre(needle, ore)) {
                    return true;
                }
            }
            for (OreEntry ore : record.surfaceOres) {
                if (matchesOre(needle, ore)) {
                    return true;
                }
            }
        }
        return matches(needle, group.title());
    }

    private static boolean matchesOre(String needle, OreEntry ore) {
        return matches(needle, VeinNames.oreName(ore).getString())
                || matches(needle, ore.id)
                || matches(needle, ore.translationKey);
    }

    public static boolean matchesFluid(FluidVeinRecord record) {
        if (!active()) {
            return true;
        }
        String needle = query.toLowerCase(Locale.ROOT);
        Fluid fluid = BuiltInRegistries.FLUID.get(record.fluidId());
        if (fluid != null) {
            Component name = new FluidStack(fluid, 1).getDisplayName();
            if (matches(needle, name.getString())) {
                return true;
            }
        }
        ResourceLocation id = record.fluidId();
        return matches(needle, id.toString()) || matches(needle, id.getPath());
    }

    private static boolean matches(String needle, String value) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }
}
