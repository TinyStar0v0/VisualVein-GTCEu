package com.visualvein.client;

import com.visualvein.VisualVein;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = VisualVein.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientEvents {

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        while (ClientSetup.TOGGLE_OVERLAY.consumeClick()) {
            ClientHooks.openMenu.run();
        }
        while (ClientSetup.CREATE_WAYPOINT.consumeClick()) {
            ClientHooks.createWaypoint.run();
        }
        while (ClientSetup.TOGGLE_STATUS.consumeClick()) {
            ClientHooks.toggleHoveredStatus.run();
        }
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("vpwaypoint").executes(context -> {
            ClientHooks.createWaypoint.run();
            return 1;
        }));
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientVeinCache.clearAll();
        ClientFluidCache.clearAll();
    }
}
