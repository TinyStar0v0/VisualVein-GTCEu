package com.visualvein.network;

import com.visualvein.server.VeinSyncManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record RequestSyncPacket() {

    public static void encode(RequestSyncPacket msg, FriendlyByteBuf buf) {
    }

    public static RequestSyncPacket decode(FriendlyByteBuf buf) {
        return new RequestSyncPacket();
    }

    public static void handle(RequestSyncPacket msg, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ServerPlayer player = ctx.getSender();
        ctx.enqueueWork(() -> {
            if (player != null) {
                VeinSyncManager.sendFull(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
