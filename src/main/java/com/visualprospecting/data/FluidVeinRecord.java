package com.visualprospecting.data;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * A GTCEu bedrock fluid vein region. Fluid veins are defined on an 8x8 chunk grid, so the marker is
 * stored per region rather than per chunk.
 */
public record FluidVeinRecord(int regionX, int regionZ, int centerX, int centerZ,
                              ResourceLocation fluidId, int yield, int depletedYield,
                              int operations, int maxOperations) {

    public String key() {
        return regionX + ":" + regionZ + ":" + fluidId;
    }

    public boolean depleted() {
        return yield <= depletedYield;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(regionX);
        buf.writeVarInt(regionZ);
        buf.writeVarInt(centerX);
        buf.writeVarInt(centerZ);
        buf.writeResourceLocation(fluidId);
        buf.writeVarInt(yield);
        buf.writeVarInt(depletedYield);
        buf.writeVarInt(operations);
        buf.writeVarInt(maxOperations);
    }

    public static FluidVeinRecord read(FriendlyByteBuf buf) {
        return new FluidVeinRecord(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readResourceLocation(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }
}
