package com.visualprospecting.server;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialStack;
import com.gregtechceu.gtceu.common.block.SurfaceRockBlock;
import com.visualprospecting.VisualProspecting;
import com.visualprospecting.data.VeinNames;
import com.visualprospecting.data.VeinRecord;
import com.visualprospecting.data.VeinWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manual marking (nothing is drawn on the map until marked):
 * <ul>
 *     <li>break / right-click a GTCEu surface rock (the block itself is harvested by right-click);</li>
 *     <li>break a complete GTCEu ore block;</li>
 *     <li>right-click with a GTCEu prospector to scan the surrounding vein grid in one go.</li>
 * </ul>
 * A vein is only marked once; repeated interactions are silent.
 */
@Mod.EventBusSubscriber(modid = VisualProspecting.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VeinInteractionHandler {

    private static final Map<UUID, Long> LAST_PROSPECTOR_SCAN = new HashMap<>();

    private VeinInteractionHandler() {
    }

    @SubscribeEvent(receiveCanceled = true)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (handleProspector(player, event.getItemStack())) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof SurfaceRockBlock rock) {
            markSurfaceRock(level, player, pos, rock.getMaterial());
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof SurfaceRockBlock rock) {
            markSurfaceRock(level, player, pos, rock.getMaterial());
            return;
        }
        MaterialStack stack = ChemicalHelper.getMaterial(state.getBlock());
        if (stack != null) {
            markOreBlock(level, player, pos, state, stack.material());
        }
    }

    @SubscribeEvent(receiveCanceled = true)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        handleProspector(player, event.getItemStack());
    }

    /**
     * GTCEu registers prospectors as {@code gtceu:prospector.lv} / {@code prospector.hv} / ...
     * Right-clicking a block and right-clicking air use different Forge events, so both call here.
     */
    private static boolean handleProspector(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null || !itemId.getPath().startsWith("prospector")) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        long now = level.getGameTime();
        Long last = LAST_PROSPECTOR_SCAN.get(player.getUUID());
        if (last != null && now - last < 10L) {
            return true;
        }
        LAST_PROSPECTOR_SCAN.put(player.getUUID(), now);

        VeinWorldData data = VeinWorldData.get(level.getServer());
        VeinWorldData.DimensionVeins dimension = data.dimension(level.dimension().location());
        int before = 0;
        for (VeinRecord record : dimension.all()) {
            if (record.marked) {
                before++;
            }
        }
        VeinScanner.scanRegion(level, player.chunkPosition(), 8, "prospector");
        int after = 0;
        for (VeinRecord record : dimension.all()) {
            if (record.marked) {
                after++;
            }
        }
        int marked = Math.max(0, after - before);
        if (marked > 0) {
            player.sendSystemMessage(Component.translatable("vp.msg.prospector_marked", marked));
        } else {
            player.sendSystemMessage(Component.translatable("vp.msg.prospector_none"));
        }
        return true;
    }

    private static void markSurfaceRock(ServerLevel level, ServerPlayer player, BlockPos pos, Material material) {
        VeinWorldData data = VeinWorldData.get(level.getServer());
        VeinWorldData.DimensionVeins dimension = data.dimension(level.dimension().location());
        VeinRecord vein = dimension.nearestWithMaterial(pos, material.getName(), 64.0D);
        if (vein == null) {
            VeinScanner.scanRegion(level, new net.minecraft.world.level.ChunkPos(pos), 2, "auto");
            vein = dimension.nearestWithMaterial(pos, material.getName(), 64.0D);
        }
        if (vein == null) {
            player.displayClientMessage(Component.translatable("vp.msg.mark_failed"), true);
            return;
        }
        VeinRecord marked = data.mark(level.dimension().location(), vein.key(), "surface");
        if (marked != null) {
            VeinSyncManager.markDirty(level.dimension().location(), java.util.List.of(marked));
            player.sendSystemMessage(Component.translatable("vp.msg.marked",
                    VeinNames.veinName(marked.veinId), marked.centerX, marked.centerZ));
        }
    }

    private static void markOreBlock(ServerLevel level, ServerPlayer player, BlockPos pos,
                                     BlockState state, Material material) {
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (blockId == null) {
            return;
        }
        VeinWorldData data = VeinWorldData.get(level.getServer());
        VeinWorldData.DimensionVeins dimension = data.dimension(level.dimension().location());
        VeinRecord vein = dimension.findVeinForOre(pos, blockId.toString(), material.getName());
        if (vein == null) {
            VeinScanner.scanRegion(level, new net.minecraft.world.level.ChunkPos(pos), 2, "auto");
            vein = dimension.findVeinForOre(pos, blockId.toString(), material.getName());
        }
        if (vein == null) {
            return;
        }
        VeinRecord marked = data.mark(level.dimension().location(), vein.key(), "ore");
        if (marked != null) {
            VeinSyncManager.markDirty(level.dimension().location(), java.util.List.of(marked));
            player.sendSystemMessage(Component.translatable("vp.msg.marked",
                    VeinNames.veinName(marked.veinId), marked.centerX, marked.centerZ));
        }
    }
}
