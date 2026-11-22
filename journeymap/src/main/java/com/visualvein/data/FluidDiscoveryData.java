package com.visualvein.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Server-side record of fluid vein regions the players have actually discovered. GTCEu owns the
 * authoritative fluid data; this only stores which 8x8 region keys are allowed to be synced.
 */
public final class FluidDiscoveryData extends SavedData {

    public static final String DATA_NAME = "visualvein_fluids";

    private final Map<ResourceLocation, Set<String>> discovered = new HashMap<>();

    public static FluidDiscoveryData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(FluidDiscoveryData::load, FluidDiscoveryData::new, DATA_NAME);
    }

    public Set<String> get(ResourceLocation dimension) {
        return Collections.unmodifiableSet(discovered.computeIfAbsent(dimension, ignored -> new HashSet<>()));
    }

    public boolean discover(ResourceLocation dimension, int regionX, int regionZ) {
        boolean added = discovered.computeIfAbsent(dimension, ignored -> new HashSet<>())
                .add(regionX + ":" + regionZ);
        if (added) {
            setDirty();
        }
        return added;
    }

    public static String key(int regionX, int regionZ) {
        return regionX + ":" + regionZ;
    }

    public static FluidDiscoveryData load(CompoundTag tag) {
        FluidDiscoveryData data = new FluidDiscoveryData();
        CompoundTag dimensions = tag.getCompound("dimensions");
        for (String dimension : dimensions.getAllKeys()) {
            ListTag list = dimensions.getList(dimension, Tag.TAG_STRING);
            Set<String> keys = new HashSet<>();
            for (int i = 0; i < list.size(); i++) {
                keys.add(list.getString(i));
            }
            data.discovered.put(ResourceLocation.tryParse(dimension), keys);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag dimensions = new CompoundTag();
        for (Map.Entry<ResourceLocation, Set<String>> entry : discovered.entrySet()) {
            ListTag list = new ListTag();
            for (String key : entry.getValue()) {
                list.add(StringTag.valueOf(key));
            }
            dimensions.put(entry.getKey().toString(), list);
        }
        tag.put("dimensions", dimensions);
        return tag;
    }
}
