package com.visualvein.client;

import com.visualvein.VisualVein;
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

@Mod.EventBusSubscriber(modid = VisualVein.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientSetup {

    public static final KeyMapping TOGGLE_OVERLAY = new KeyMapping(
            "key.visualvein.toggle_overlay", GLFW.GLFW_KEY_V, "key.categories.visualvein");
    public static final KeyMapping CREATE_WAYPOINT = new KeyMapping(
            "key.visualvein.create_waypoint", GLFW.GLFW_KEY_B, "key.categories.visualvein");
    public static final KeyMapping TOGGLE_STATUS = new KeyMapping(
            "key.visualvein.toggle_status", GLFW.GLFW_KEY_DELETE, "key.categories.visualvein");

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_OVERLAY);
        event.register(CREATE_WAYPOINT);
        event.register(TOGGLE_STATUS);
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        ClientHooks.openMenu = () -> Minecraft.getInstance().setScreen(new VpMenuScreen());
        ClientHooks.scanNearby = () -> com.visualvein.network.VPNetwork.sendToServer(
                new com.visualvein.network.ScanRequestPacket(8));

        if (!ModList.get().isLoaded("xaeroworldmap") || !ModList.get().isLoaded("xaerominimap")) {
            ClientHooks.toggleOverlay = ClientSetup::noXaero;
            ClientHooks.createWaypoint = ClientSetup::noXaero;
            ClientHooks.toggleHoveredStatus = ClientSetup::noXaero;
        }
    }

    private static void noXaero() {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.translatable("vp.msg.no_xaero"), false);
        }
    }
}
