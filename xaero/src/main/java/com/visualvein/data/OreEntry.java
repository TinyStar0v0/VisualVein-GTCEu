package com.visualvein.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;

/**
 * A single ore type contained in a vein, together with the relative weight that GTCEu uses for it.
 *
 * <p>The display name is resolved on the client from {@link #translationKey}, so a server running with
 * English never forces English names onto Chinese clients. {@link #materialId} is used to attribute
 * deepslate/netherrack variants of an ore to the same vein when a player mines them.</p>
 */
public final class OreEntry {

    public final String id;
    public final String materialId;
    public final String translationKey;
    public final int color;
    public final int weight;
    public final boolean material;

    public OreEntry(String id, String materialId, String translationKey, int color, int weight, boolean material) {
        this.id = id;
        this.materialId = materialId == null ? "" : materialId;
        this.translationKey = translationKey == null ? "" : translationKey;
        this.color = color & 0xFFFFFF;
        this.weight = weight;
        this.material = material;
    }

    public OreEntry withWeight(int newWeight) {
        return new OreEntry(id, materialId, translationKey, color, newWeight, material);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("mat", materialId);
        tag.putString("key", translationKey);
        tag.putInt("color", color);
        tag.putInt("weight", weight);
        tag.putBoolean("material", material);
        return tag;
    }

    public static OreEntry load(CompoundTag tag) {
        return new OreEntry(
                tag.getString("id"),
                tag.getString("mat"),
                tag.getString("key"),
                tag.getInt("color"),
                tag.getInt("weight"),
                tag.getBoolean("material")
        );
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(id, 256);
        buf.writeUtf(materialId, 256);
        buf.writeUtf(translationKey, 256);
        buf.writeInt(color);
        buf.writeVarInt(weight);
        buf.writeBoolean(material);
    }

    public static OreEntry read(FriendlyByteBuf buf) {
        return new OreEntry(
                buf.readUtf(256),
                buf.readUtf(256),
                buf.readUtf(256),
                buf.readInt(),
                buf.readVarInt(),
                buf.readBoolean()
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OreEntry oreEntry)) return false;
        return color == oreEntry.color && weight == oreEntry.weight
                && material == oreEntry.material && id.equals(oreEntry.id)
                && materialId.equals(oreEntry.materialId)
                && translationKey.equals(oreEntry.translationKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, materialId, translationKey, color, weight, material);
    }
}
