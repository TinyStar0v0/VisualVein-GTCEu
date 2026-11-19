package com.visualprospecting.client;

import com.visualprospecting.data.FluidVeinRecord;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ClientFluidCache {

    private static final Map<ResourceLocation, List<FluidVeinRecord>> DATA = new HashMap<>();
    private static long version;

    private ClientFluidCache() {
    }

    public static void apply(ResourceLocation dimension, Collection<FluidVeinRecord> records, boolean replace) {
        List<FluidVeinRecord> list = DATA.computeIfAbsent(dimension, ignored -> new ArrayList<>());
        if (replace) {
            list.clear();
        }
        Map<String, FluidVeinRecord> merged = new LinkedHashMap<>();
        for (FluidVeinRecord record : list) {
            merged.put(record.key(), record);
        }
        for (FluidVeinRecord record : records) {
            merged.put(record.key(), record);
        }
        list.clear();
        list.addAll(merged.values());
        version++;
        ClientHooks.dataChanged.run();
    }

    public static void clear(ResourceLocation dimension) {
        if (DATA.remove(dimension) != null) {
            version++;
            ClientHooks.dataChanged.run();
        }
    }

    public static void clearAll() {
        if (!DATA.isEmpty()) {
            DATA.clear();
            version++;
            ClientHooks.dataChanged.run();
        }
    }

    public static Collection<FluidVeinRecord> get(ResourceLocation dimension) {
        List<FluidVeinRecord> list = DATA.get(dimension);
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
    }

    public static long version() {
        return version;
    }
}
