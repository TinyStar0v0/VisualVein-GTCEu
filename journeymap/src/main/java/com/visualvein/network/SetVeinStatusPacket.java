package com.visualvein.network;

import com.visualvein.data.VeinRecord;
import com.visualvein.data.VeinWorldData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Right-click the vein overlay to mark it as depleted (or restore it). The state is persisted on the
 * server and sent back to every client in the dimension.
 */
public record SetVeinStatusPacket(ResourceLocation dimension, String veinKey, boolean depleted) {

    public static void encode(SetVeinStatusPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.dimension);
        buf.writeUtf(msg.veinKey, 128);
        buf.writeBoolean(msg.depleted);
    }

    public static SetVeinStatusPacket decode(FriendlyByteBuf buf) {
        return new SetVeinStatusPacket(buf.readResourceLocation(), buf.readUtf(128), buf.readBoolean());
    }

    public static void handle(SetVeinStatusPacket msg, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ServerPlayer player = ctx.getSender();
        ctx.enqueueWork(() -> {
            if (player == null) {
                return;
            }
            if (player.getServer() == null) {
                return;
            }
            VeinWorldData data = VeinWorldData.get(player.getServer());
            VeinRecord record = data.setDepleted(msg.dimension, msg.veinKey, msg.depleted);
            if (record == null) {
                return;
            }
            VPNetwork.sendToDimension(player.serverLevel().dimension(),
                    new VeinSyncPacket(msg.dimension, false, List.of(record)));
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    msg.depleted ? "vp.msg.depleted" : "vp.msg.restored"), true);
        });
        ctx.setPacketHandled(true);
    }
}
