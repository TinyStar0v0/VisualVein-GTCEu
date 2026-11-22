package com.visualvein.network;

import com.visualvein.data.FluidVeinRecord;
import com.visualvein.data.VeinRecord;
import com.visualvein.data.VeinWorldData;
import com.visualvein.server.FluidVeinSyncManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server-side safe teleport to a manually marked ore vein. */
public record TeleportToVeinPacket(ResourceLocation dimension, String veinKey) {

    public static void encode(TeleportToVeinPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.dimension);
        buf.writeUtf(msg.veinKey, 128);
    }

    public static TeleportToVeinPacket decode(FriendlyByteBuf buf) {
        return new TeleportToVeinPacket(buf.readResourceLocation(), buf.readUtf(128));
    }

    public static void handle(TeleportToVeinPacket msg, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ServerPlayer player = ctx.getSender();
        ctx.enqueueWork(() -> {
            if (player == null || player.getServer() == null) {
                return;
            }
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, msg.dimension);
            ServerLevel level = player.getServer().getLevel(key);
            if (level == null) {
                player.displayClientMessage(Component.translatable("vp.msg.teleport_unsafe"), true);
                return;
            }
            VeinWorldData.DimensionVeins dimension = VeinWorldData.get(player.getServer())
                    .dimensionOrNull(msg.dimension);
            VeinRecord record = dimension == null ? null : dimension.get(msg.veinKey);
            int centerX;
            int centerZ;
            if (record != null) {
                centerX = record.centerX;
                centerZ = record.centerZ;
            } else {
                FluidVeinRecord fluid = null;
                for (FluidVeinRecord candidate : FluidVeinSyncManager.discovered(level)) {
                    if (candidate.key().equals(msg.veinKey)) {
                        fluid = candidate;
                        break;
                    }
                }
                if (fluid == null) {
                    player.displayClientMessage(Component.translatable("vp.msg.teleport_unsafe"), true);
                    return;
                }
                centerX = fluid.centerX();
                centerZ = fluid.centerZ();
            }
            BlockPos safe = findSafe(level, centerX, centerZ);
            if (safe == null) {
                player.displayClientMessage(Component.translatable("vp.msg.teleport_unsafe"), true);
                return;
            }
            player.teleportTo(level, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5,
                    player.getYRot(), player.getXRot());
            player.displayClientMessage(Component.translatable("vp.msg.teleported",
                    safe.getX(), safe.getY(), safe.getZ()), true);
        });
        ctx.setPacketHandled(true);
    }

    private static BlockPos findSafe(ServerLevel level, int centerX, int centerZ) {
        for (int radius = 0; radius <= 12; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    int x = centerX + dx;
                    int z = centerZ + dz;
                    if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
                        continue;
                    }
                    int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    for (int y = top; y <= top + 4; y++) {
                        BlockPos feet = new BlockPos(x, y, z);
                        if (isSafe(level, feet)) {
                            return feet;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean isSafe(ServerLevel level, BlockPos feet) {
        BlockState below = level.getBlockState(feet.below());
        BlockState at = level.getBlockState(feet);
        BlockState head = level.getBlockState(feet.above());
        return below.isSolid()
                && below.getFluidState().isEmpty()
                && at.getCollisionShape(level, feet).isEmpty()
                && head.getCollisionShape(level, feet.above()).isEmpty()
                && at.getFluidState().isEmpty()
                && head.getFluidState().isEmpty()
                && !at.getFluidState().is(FluidTags.LAVA)
                && !head.getFluidState().is(FluidTags.LAVA);
    }
}
