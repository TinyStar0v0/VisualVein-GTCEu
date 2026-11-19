package com.visualprospecting.client;

import com.visualprospecting.VisualProspecting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = VisualProspecting.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientSetup {

    public static final KeyMapping TOGGLE_OVERLAY = new KeyMapping(
            "key.visualprospecting.toggle_overlay", GLFW.GLFW_KEY_V, "key.categories.visualprospecting");
    public static final KeyMapping CREATE_WAYPOINT = new KeyMapping(
            "key.visualprospecting.create_waypoint", GLFW.GLFW_KEY_B, "key.categories.visualprospecting");

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_OVERLAY);
        event.register(CREATE_WAYPOINT);
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        ClientHooks.openMenu = () -> Minecraft.getInstance().setScreen(new VpMenuScreen());
        ClientHooks.scanNearby = () -> com.visualprospecting.network.VPNetwork.sendToServer(
                new com.visualprospecting.network.ScanRequestPacket(8));

        // JourneyMap discovers @ClientPlugin (5.x) or @JourneyMapPlugin (6.x) itself. We must not
        // classload either plugin here because the other API version may not be present.
        if (!ModList.get().isLoaded("journeymap")) {
            ClientHooks.toggleOverlay = ClientSetup::noJourneyMap;
            ClientHooks.createWaypoint = ClientSetup::noJourneyMap;
        }
    }

    private static void noJourneyMap() {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.translatable("vp.msg.no_journeymap"), false);
        }
    }
}
