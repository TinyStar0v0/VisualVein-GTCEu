package com.visualprospecting.server;

import com.gregtechceu.gtceu.api.data.worldgen.ores.GeneratedVeinMetadata;
import com.gregtechceu.gtceu.api.data.worldgen.ores.OreGenerator;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.visualprospecting.VisualProspecting;
import com.visualprospecting.data.OreEntry;
import com.visualprospecting.data.VeinGroup;
import com.visualprospecting.data.VeinNames;
import com.visualprospecting.data.VeinRecord;
import com.visualprospecting.data.VeinWorldData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Mod.EventBusSubscriber(modid = VisualProspecting.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VPCommands {

    private static final int PAGE_SIZE = 10;

    private VPCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("vp")
                .executes(context -> help(context.getSource()))
                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource(), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> list(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "page")))))
                .then(Commands.literal("nearest")
                        .executes(context -> nearest(context.getSource())))
                .then(Commands.literal("scan")
                        .executes(context -> scan(context.getSource(), 8))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 16))
                                .executes(context -> scan(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "radius")))))
                .then(Commands.literal("stats")
                        .executes(context -> stats(context.getSource())))
                .then(Commands.literal("clear")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> clear(context.getSource()))
                        .then(Commands.literal("all")
                                .executes(context -> clearAll(context.getSource()))))
                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> reload(context.getSource())))
                .then(Commands.literal("debug")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> debug(context.getSource()))));
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("vp.command.help.header")
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.translatable("vp.command.help.list"), false);
        source.sendSuccess(() -> Component.translatable("vp.command.help.nearest"), false);
        source.sendSuccess(() -> Component.translatable("vp.command.help.stats"), false);
        source.sendSuccess(() -> Component.translatable("vp.command.help.clear"), false);
        source.sendSuccess(() -> Component.translatable("vp.command.help.reload"), false);
        return 1;
    }

    private static int list(CommandSourceStack source, int page) {
        VeinWorldData.DimensionVeins dimension = dimension(source);
        if (dimension == null || dimension.size() == 0) {
            source.sendFailure(Component.translatable("vp.command.no_data"));
            return 0;
        }
        Vec3 position = source.getPosition();
        List<VeinGroup> groups = VeinGroup.group(dimension.all(), false, null);
        groups.sort(Comparator.comparingDouble(group -> distanceSq(group.primary(), position)));
        int pages = Math.max(1, (groups.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.min(page, pages);
        int start = (current - 1) * PAGE_SIZE;
        int end = Math.min(groups.size(), start + PAGE_SIZE);
        ResourceLocation dimensionId = source.getLevel().dimension().location();

        source.sendSuccess(() -> Component.translatable("vp.command.list.header",
                VeinNames.dimensionName(dimensionId), current, pages, groups.size())
                .withStyle(ChatFormatting.GOLD), false);
        for (int i = start; i < end; i++) {
            VeinGroup group = groups.get(i);
            VeinRecord vein = group.primary();
            int number = i + 1;
            Component line = Component.literal("#" + number + " ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(VeinNames.veinName(vein.veinId).copy().withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" [").withStyle(ChatFormatting.GRAY))
                    .append(VeinNames.layerName(vein.layer).copy().withStyle(VeinNames.layerColor(vein.layer)))
                    .append(Component.literal("]  " + vein.centerX + ", " + vein.centerY + ", " + vein.centerZ + "  ")
                            .withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(Math.round(Math.sqrt(distanceSq(vein, position))) + "m")
                            .withStyle(ChatFormatting.DARK_AQUA))
                    .append(Component.literal(group.layers().size() > 1
                            ? "  +" + (group.layers().size() - 1) + "层" : "")
                            .withStyle(ChatFormatting.DARK_GRAY));
            source.sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int nearest(CommandSourceStack source) {
        VeinWorldData.DimensionVeins dimension = dimension(source);
        if (dimension == null || dimension.size() == 0) {
            source.sendFailure(Component.translatable("vp.command.no_data"));
            return 0;
        }
        Vec3 position = source.getPosition();
        List<VeinGroup> groups = VeinGroup.group(dimension.all(), false, null);
        groups.sort(Comparator.comparingDouble(group -> distanceSq(group.primary(), position)));
        if (groups.isEmpty()) {
            source.sendFailure(Component.translatable("vp.command.no_data"));
            return 0;
        }
        VeinGroup group = groups.get(0);
        VeinRecord vein = group.primary();
        source.sendSuccess(() -> Component.translatable("vp.command.nearest.header",
                VeinNames.veinName(vein.veinId)).withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.translatable("vp.command.nearest.location",
                VeinNames.layerName(vein.layer),
                vein.centerX, vein.centerY, vein.centerZ,
                Math.round(Math.sqrt(distanceSq(vein, position)))), false);
        source.sendSuccess(() -> Component.translatable("vp.command.nearest.properties",
                vein.clusterSize, Math.round(vein.density * 100.0F)), false);
        source.sendSuccess(() -> Component.translatable("vp.command.nearest.ores"), false);
        int total = 0;
        for (OreEntry ore : vein.ores) {
            total += ore.weight;
        }
        int finalTotal = total;
        for (OreEntry ore : vein.ores) {
            source.sendSuccess(() -> Component.literal("  • ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(VeinNames.oreName(ore).copy().withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(finalTotal > 0
                            ? "  " + Math.round(ore.weight * 100.0F / finalTotal) + "%"
                            : "  -").withStyle(ChatFormatting.GRAY)), false);
        }
        if (group.layers().size() > 1) {
            for (VeinRecord other : group.layers()) {
                if (other == vein) {
                    continue;
                }
                source.sendSuccess(() -> Component.literal("  + ")
                        .withStyle(ChatFormatting.DARK_GRAY)
                        .append(VeinNames.veinName(other.veinId).copy().withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(" / " + VeinNames.layerName(other.layer).getString()
                                + " (Y " + other.centerY + ")").withStyle(ChatFormatting.DARK_GRAY)), false);
            }
        }
        return 1;
    }

    private static int scan(CommandSourceStack source, int radius) {
        Vec3 pos = source.getPosition();
        net.minecraft.world.level.ChunkPos center = new net.minecraft.world.level.ChunkPos(
                (int) pos.x >> 4, (int) pos.z >> 4);
        int found = VeinScanner.scanRegion(source.getLevel(), center, radius, "command");
        source.sendSuccess(() -> Component.translatable("vp.command.scan", found, radius), true);
        return 1;
    }

    private static int stats(CommandSourceStack source) {
        VeinWorldData data = VeinWorldData.get(source.getServer());
        Map<String, Integer> layers = new LinkedHashMap<>();
        int total = 0;
        for (VeinWorldData.DimensionVeins dimension : data.dimensions().values()) {
            total += dimension.size();
            for (VeinRecord vein : dimension.all()) {
                layers.merge(vein.layer, 1, Integer::sum);
            }
        }
        int finalTotal = total;
        source.sendSuccess(() -> Component.translatable("vp.command.stats.header", finalTotal)
                .withStyle(ChatFormatting.GOLD), false);
        for (Map.Entry<String, Integer> entry : layers.entrySet()) {
            source.sendSuccess(() -> Component.literal("  " + entry.getKey() + ": " + entry.getValue())
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static int clear(CommandSourceStack source) {
        ResourceLocation dimensionId = source.getLevel().dimension().location();
        VeinWorldData data = VeinWorldData.get(source.getServer());
        int removed = data.clear(dimensionId);
        VeinSyncManager.sendClear(dimensionId);
        source.sendSuccess(() -> Component.translatable("vp.command.clear", removed), true);
        return 1;
    }

    private static int clearAll(CommandSourceStack source) {
        VeinWorldData data = VeinWorldData.get(source.getServer());
        List<ResourceLocation> dimensions = new ArrayList<>(data.dimensions().keySet());
        int total = 0;
        for (VeinWorldData.DimensionVeins dimension : data.dimensions().values()) {
            total += dimension.size();
        }
        data.clearAll();
        for (ResourceLocation dimension : dimensions) {
            VeinSyncManager.sendClear(dimension);
        }
        int finalTotal = total;
        source.sendSuccess(() -> Component.translatable("vp.command.clear_all", finalTotal), true);
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        int queued = 0;
        for (ServerLevel level : source.getServer().getAllLevels()) {
            queued += VeinScanner.queueAroundPlayers(level, 16);
        }
        int finalQueued = queued;
        source.sendSuccess(() -> Component.translatable("vp.command.reload", finalQueued), true);
        return 1;
    }

    private static int debug(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        source.sendSuccess(() -> Component.literal("GTCEu ore vein definitions: "
                + GTRegistries.ORE_VEINS.keys().size()), false);
        source.sendSuccess(() -> Component.literal("Scanner: " + VeinScanner.debugStats()), false);
        try {
            net.minecraft.world.level.ChunkPos center = new net.minecraft.world.level.ChunkPos(
                    (int) source.getPosition().x >> 4, (int) source.getPosition().z >> 4);
            var fluids = FluidVeinSyncManager.scan(level, center, 8);
            source.sendSuccess(() -> Component.literal("Fluid vein regions nearby: " + fluids.size()), false);
            for (int i = 0; i < Math.min(5, fluids.size()); i++) {
                var fluid = fluids.get(i);
                source.sendSuccess(() -> Component.literal("  " + fluid.fluidId()
                        + " @ " + fluid.centerX() + "," + fluid.centerZ()
                        + " yield=" + fluid.yield() + " ops=" + fluid.operations()), false);
            }
        } catch (Throwable throwable) {
            source.sendFailure(Component.literal("Fluid scan error: " + throwable));
        }
        try {
            List<GeneratedVeinMetadata> metadata = new OreGenerator().generateMetadata(
                    level, level.getChunkSource().getGenerator(), new net.minecraft.world.level.ChunkPos(0, 0));
            source.sendSuccess(() -> Component.literal("Chunk 0,0 metadata: " + metadata.size()), false);
            for (GeneratedVeinMetadata meta : metadata) {
                source.sendSuccess(() -> Component.literal("  " + meta.id() + " @ "
                        + meta.center().getX() + "," + meta.center().getY() + "," + meta.center().getZ()
                        + " layer=" + meta.definition().layer().getSerializedName()), false);
            }
        } catch (Throwable throwable) {
            source.sendFailure(Component.literal("Metadata error: " + throwable));
            VisualProspecting.LOGGER.warn("Debug metadata scan failed", throwable);
        }
        return 1;
    }

    private static VeinWorldData.DimensionVeins dimension(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        return VeinWorldData.get(source.getServer()).dimensionOrNull(level.dimension().location());
    }

    private static double distanceSq(VeinRecord vein, Vec3 position) {
        double dx = position.x - vein.centerX;
        double dz = position.z - vein.centerZ;
        return dx * dx + dz * dz;
    }
}
