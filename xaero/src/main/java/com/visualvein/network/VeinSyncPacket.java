package com.visualvein.network;

import com.visualvein.client.ClientVeinCache;
import com.visualvein.data.VeinRecord;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record VeinSyncPacket(ResourceLocation dimension, boolean full, List<VeinRecord> veins) {

    public static void encode(VeinSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.dimension);
        buf.writeBoolean(msg.full);
        buf.writeVarInt(msg.veins.size());
        for (VeinRecord vein : msg.veins) {
            vein.write(buf);
        }
    }

    public static VeinSyncPacket decode(FriendlyByteBuf buf) {
        ResourceLocation dimension = buf.readResourceLocation();
        boolean full = buf.readBoolean();
        int count = buf.readVarInt();
        List<VeinRecord> veins = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            veins.add(VeinRecord.read(buf));
        }
        return new VeinSyncPacket(dimension, full, veins);
    }

    public static void handle(VeinSyncPacket msg, Supplier<NetworkEvent.Context> context) {
        VPNetwork.enqueue(context, () -> ClientVeinCache.apply(msg.dimension, msg.veins, msg.full));
    }
}
