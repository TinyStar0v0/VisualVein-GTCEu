package com.visualprospecting.network;

import com.visualprospecting.client.ClientFluidCache;
import com.visualprospecting.data.FluidVeinRecord;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record FluidSyncPacket(ResourceLocation dimension, boolean replace, List<FluidVeinRecord> veins) {

    public static void encode(FluidSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.dimension);
        buf.writeBoolean(msg.replace);
        buf.writeVarInt(msg.veins.size());
        for (FluidVeinRecord vein : msg.veins) {
            vein.write(buf);
        }
    }

    public static FluidSyncPacket decode(FriendlyByteBuf buf) {
        ResourceLocation dimension = buf.readResourceLocation();
        boolean replace = buf.readBoolean();
        int size = buf.readVarInt();
        List<FluidVeinRecord> veins = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            veins.add(FluidVeinRecord.read(buf));
        }
        return new FluidSyncPacket(dimension, replace, veins);
    }

    public static void handle(FluidSyncPacket msg, Supplier<NetworkEvent.Context> context) {
        VPNetwork.enqueue(context, () -> ClientFluidCache.apply(msg.dimension, msg.veins, msg.replace));
    }
}
