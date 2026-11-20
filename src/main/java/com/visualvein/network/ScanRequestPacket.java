package com.visualvein.network;

import com.visualvein.server.VeinScanner;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client request to scan the GTCEu vein grid around the player. Used by the VP menu and the
 * prospector integration.
 */
public record ScanRequestPacket(int radiusChunks) {

    public static void encode(ScanRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.radiusChunks);
    }

    public static ScanRequestPacket decode(FriendlyByteBuf buf) {
        return new ScanRequestPacket(buf.readVarInt());
    }

    public static void handle(ScanRequestPacket msg, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ServerPlayer player = ctx.getSender();
        ctx.enqueueWork(() -> {
            if (player == null) {
                return;
            }
            int radius = Math.max(1, Math.min(16, msg.radiusChunks));
            int found = VeinScanner.scanRegion(player.serverLevel(), player.chunkPosition(), radius, "prospector");
            com.visualvein.server.FluidVeinSyncManager.sendFull(player);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "vp.msg.prospector_scan", found), true);
        });
        ctx.setPacketHandled(true);
    }
}
