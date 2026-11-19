package com.visualprospecting.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * A GTCEu surface rock found above a vein. Used for an explicit surface marker on the map and to
 * explain why a player standing on a salt rock may see a different deepslate vein at the centre.
 */
public record SurfacePoint(int x, int y, int z, OreEntry ore) {

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        tag.put("ore", ore.save());
        return tag;
    }

    public static SurfacePoint load(CompoundTag tag) {
        return new SurfacePoint(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"),
                OreEntry.load(tag.getCompound("ore")));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(x);
        buf.writeVarInt(y);
        buf.writeVarInt(z);
        ore.write(buf);
    }

    public static SurfacePoint read(FriendlyByteBuf buf) {
        return new SurfacePoint(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), OreEntry.read(buf));
    }
}
