package com.visualvein.data;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * A GTCEu bedrock fluid vein region. Fluid veins are defined on an 8x8 chunk grid, so the marker is
 * stored per region rather than per chunk.
 */
public record FluidVeinRecord(int regionX, int regionZ, int centerX, int centerZ,
                              ResourceLocation fluidId, int yield, int depletedYield,
                              int operations, int maxOperations, int[] chunkYields) {

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
        buf.writeVarInt(chunkYields.length);
        for (int chunkYield : chunkYields) {
            buf.writeVarInt(chunkYield);
        }
    }

    public static FluidVeinRecord read(FriendlyByteBuf buf) {
        int regionX = buf.readVarInt();
        int regionZ = buf.readVarInt();
        int centerX = buf.readVarInt();
        int centerZ = buf.readVarInt();
        ResourceLocation fluidId = buf.readResourceLocation();
        int yield = buf.readVarInt();
        int depletedYield = buf.readVarInt();
        int operations = buf.readVarInt();
        int maxOperations = buf.readVarInt();
        int[] yields = new int[buf.readVarInt()];
        for (int i = 0; i < yields.length; i++) {
            yields[i] = buf.readVarInt();
        }
        return new FluidVeinRecord(regionX, regionZ, centerX, centerZ, fluidId,
                yield, depletedYield, operations, maxOperations, yields);
    }
}
