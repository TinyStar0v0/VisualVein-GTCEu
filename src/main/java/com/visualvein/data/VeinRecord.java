package com.visualvein.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One ore vein discovered at a GTCEu vein origin. A single origin can hold one vein per world-gen layer
 * (stone, deepslate, netherrack, endstone, or addon layers), so the identity is origin chunk + layer.
 */
public final class VeinRecord {

    public final ResourceLocation veinId;
    public final String layer;
    public final int originX;
    public final int originZ;
    public final int centerX;
    public final int centerY;
    public final int centerZ;
    /** Surface Y at the vein center, recorded server-side for waypoint creation. */
    public int surfaceY;
    public final int clusterSize;
    public final float density;
    public final List<OreEntry> ores;
    public final long discoveredAt;

    /** Number of ore blocks of this vein that players have mined. */
    public int mined;

    /** Player-defined depleted state, shown on the map and in the tooltip. */
    public boolean depleted;
    /** True once the player has manually marked this vein (surface rock, ore block or prospector). */
    public boolean marked;
    /** Free-form source of the manual mark ("surface", "ore", "prospector", "mining"). */
    public String markSource = "";
    /** Surface rock materials found above the vein (GTCEu surface indicators). */
    public final List<OreEntry> surfaceOres = new ArrayList<>();
    /** Concrete surface rock positions, used for small map markers. */
    public final List<SurfacePoint> surfacePoints = new ArrayList<>();
    /** Server-only guard so surface-rock scanning is not repeated on every rescan. */
    public boolean surfaceScanned;

    public VeinRecord(ResourceLocation veinId, String layer, int originX, int originZ,
                      int centerX, int centerY, int centerZ, int clusterSize, float density,
                      List<OreEntry> ores, int mined, long discoveredAt) {
        this.veinId = veinId;
        this.layer = layer;
        this.originX = originX;
        this.originZ = originZ;
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.clusterSize = clusterSize;
        this.density = density;
        this.ores = Collections.unmodifiableList(new ArrayList<>(ores));
        this.mined = mined;
        this.discoveredAt = discoveredAt;
    }

    public String key() {
        return originX + ":" + originZ + ":" + layer;
    }

    public VeinRecord copy() {
        VeinRecord copy = new VeinRecord(veinId, layer, originX, originZ, centerX, centerY, centerZ,
                clusterSize, density, ores, mined, discoveredAt);
        copy.depleted = depleted;
        copy.marked = marked;
        copy.markSource = markSource;
        copy.surfaceY = surfaceY;
        copy.surfaceOres.addAll(surfaceOres);
        copy.surfacePoints.addAll(surfacePoints);
        copy.surfaceScanned = surfaceScanned;
        return copy;
    }

    /**
     * Marks this vein as manually discovered. Returns false when it was already marked, which is how
     * duplicate right-clicks / scans are suppressed.
     */
    public boolean mark(String source) {
        if (marked) {
            return false;
        }
        marked = true;
        markSource = source == null ? "" : source;
        return true;
    }

    public void addSurfaceOre(OreEntry ore) {
        for (OreEntry existing : surfaceOres) {
            if (existing.id.equals(ore.id)) {
                return;
            }
        }
        surfaceOres.add(ore);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("vein", veinId.toString());
        tag.putString("layer", layer);
        tag.putInt("ox", originX);
        tag.putInt("oz", originZ);
        tag.putInt("x", centerX);
        tag.putInt("y", centerY);
        tag.putInt("z", centerZ);
        tag.putInt("surfaceY", surfaceY);
        tag.putInt("size", clusterSize);
        tag.putFloat("density", density);
        tag.putInt("mined", mined);
        tag.putLong("found", discoveredAt);
        tag.putBoolean("depleted", depleted);
        tag.putBoolean("marked", marked);
        tag.putBoolean("surfaceScanned", surfaceScanned);
        tag.putString("markSource", markSource);
        ListTag oreList = new ListTag();
        for (OreEntry ore : ores) {
            oreList.add(ore.save());
        }
        tag.put("ores", oreList);
        ListTag surfaceList = new ListTag();
        for (OreEntry ore : surfaceOres) {
            surfaceList.add(ore.save());
        }
        tag.put("surfaceOres", surfaceList);
        ListTag pointList = new ListTag();
        for (SurfacePoint point : surfacePoints) {
            pointList.add(point.save());
        }
        tag.put("surfacePoints", pointList);
        return tag;
    }

    public static VeinRecord load(CompoundTag tag) {
        List<OreEntry> ores = new ArrayList<>();
        ListTag oreList = tag.getList("ores", Tag.TAG_COMPOUND);
        for (int i = 0; i < oreList.size(); i++) {
            ores.add(OreEntry.load(oreList.getCompound(i)));
        }
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("vein"));
        if (id == null) {
            id = new ResourceLocation("gtceu", "unknown");
        }
        VeinRecord record = new VeinRecord(id, tag.getString("layer"),
                tag.getInt("ox"), tag.getInt("oz"),
                tag.getInt("x"), tag.getInt("y"), tag.getInt("z"),
                tag.getInt("size"), tag.getFloat("density"),
                ores, tag.getInt("mined"), tag.getLong("found"));
        record.surfaceY = tag.getInt("surfaceY");
        record.depleted = tag.getBoolean("depleted");
        record.marked = tag.getBoolean("marked");
        record.surfaceScanned = tag.getBoolean("surfaceScanned");
        record.markSource = tag.getString("markSource");
        ListTag surfaceList = tag.getList("surfaceOres", Tag.TAG_COMPOUND);
        for (int i = 0; i < surfaceList.size(); i++) {
            record.surfaceOres.add(OreEntry.load(surfaceList.getCompound(i)));
        }
        ListTag pointList = tag.getList("surfacePoints", Tag.TAG_COMPOUND);
        for (int i = 0; i < pointList.size(); i++) {
            record.surfacePoints.add(SurfacePoint.load(pointList.getCompound(i)));
        }
        return record;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(veinId);
        buf.writeUtf(layer, 64);
        buf.writeVarInt(originX);
        buf.writeVarInt(originZ);
        buf.writeVarInt(centerX);
        buf.writeVarInt(centerY);
        buf.writeVarInt(centerZ);
        buf.writeVarInt(surfaceY);
        buf.writeVarInt(clusterSize);
        buf.writeFloat(density);
        buf.writeVarInt(mined);
        buf.writeLong(discoveredAt);
        buf.writeBoolean(depleted);
        buf.writeBoolean(marked);
        buf.writeUtf(markSource, 64);
        buf.writeVarInt(ores.size());
        for (OreEntry ore : ores) {
            ore.write(buf);
        }
        buf.writeVarInt(surfaceOres.size());
        for (OreEntry ore : surfaceOres) {
            ore.write(buf);
        }
        buf.writeVarInt(surfacePoints.size());
        for (SurfacePoint point : surfacePoints) {
            point.write(buf);
        }
    }

    public static VeinRecord read(FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        String layer = buf.readUtf(64);
        int ox = buf.readVarInt();
        int oz = buf.readVarInt();
        int x = buf.readVarInt();
        int y = buf.readVarInt();
        int z = buf.readVarInt();
        int surfaceY = buf.readVarInt();
        int size = buf.readVarInt();
        float density = buf.readFloat();
        int mined = buf.readVarInt();
        long found = buf.readLong();
        boolean depleted = buf.readBoolean();
        boolean marked = buf.readBoolean();
        String markSource = buf.readUtf(64);
        int count = buf.readVarInt();
        List<OreEntry> ores = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ores.add(OreEntry.read(buf));
        }
        VeinRecord record = new VeinRecord(id, layer, ox, oz, x, y, z, size, density, ores, mined, found);
        record.surfaceY = surfaceY;
        record.depleted = depleted;
        record.marked = marked;
        record.markSource = markSource;
        int surfaceCount = buf.readVarInt();
        for (int i = 0; i < surfaceCount; i++) {
            record.surfaceOres.add(OreEntry.read(buf));
        }
        int pointCount = buf.readVarInt();
        for (int i = 0; i < pointCount; i++) {
            record.surfacePoints.add(SurfacePoint.read(buf));
        }
        return record;
    }

    @Override
    public String toString() {
        return "VeinRecord[" + veinId + " " + layer + " @ " + centerX + "," + centerY + "," + centerZ + "]";
    }
}
