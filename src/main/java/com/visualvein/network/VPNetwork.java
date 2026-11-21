package com.visualvein.network;

import com.visualvein.VisualVein;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public final class VPNetwork {

    private static final String PROTOCOL = "2";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(VisualVein.MODID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private VPNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, VeinSyncPacket.class,
                VeinSyncPacket::encode, VeinSyncPacket::decode, VeinSyncPacket::handle);
        CHANNEL.registerMessage(id++, VeinClearPacket.class,
                VeinClearPacket::encode, VeinClearPacket::decode, VeinClearPacket::handle);
        CHANNEL.registerMessage(id++, RequestSyncPacket.class,
                RequestSyncPacket::encode, RequestSyncPacket::decode, RequestSyncPacket::handle);
        CHANNEL.registerMessage(id++, ScanRequestPacket.class,
                ScanRequestPacket::encode, ScanRequestPacket::decode, ScanRequestPacket::handle);
        CHANNEL.registerMessage(id++, SetVeinStatusPacket.class,
                SetVeinStatusPacket::encode, SetVeinStatusPacket::decode, SetVeinStatusPacket::handle);
        CHANNEL.registerMessage(id++, FluidSyncPacket.class,
                FluidSyncPacket::encode, FluidSyncPacket::decode, FluidSyncPacket::handle);
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToDimension(ResourceKey<Level> dimension, Object packet) {
        CHANNEL.send(PacketDistributor.DIMENSION.with(() -> dimension), packet);
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void requestSync() {
        CHANNEL.sendToServer(new RequestSyncPacket());
    }

    /** Small helper to keep packet handlers readable. */
    static void enqueue(Supplier<NetworkEvent.Context> context, Runnable runnable) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(runnable);
        ctx.setPacketHandled(true);
    }

    static ResourceLocation readDimension(FriendlyByteBuf buf) {
        return buf.readResourceLocation();
    }
}
