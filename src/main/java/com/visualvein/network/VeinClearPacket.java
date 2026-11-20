package com.visualvein.network;

import com.visualvein.client.ClientVeinCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record VeinClearPacket(ResourceLocation dimension) {

    public static void encode(VeinClearPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.dimension);
    }

    public static VeinClearPacket decode(FriendlyByteBuf buf) {
        return new VeinClearPacket(buf.readResourceLocation());
    }

    public static void handle(VeinClearPacket msg, Supplier<NetworkEvent.Context> context) {
        VPNetwork.enqueue(context, () -> ClientVeinCache.clear(msg.dimension));
    }
}
