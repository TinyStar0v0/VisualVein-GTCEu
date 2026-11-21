package com.visualvein.client;

import com.visualvein.VisualVein;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Search field for the JourneyMap fullscreen map. It is deliberately a Forge screen listener rather
 * than a JourneyMap widget: JourneyMap 6.0.7 has no text-input API for addons.
 */
@Mod.EventBusSubscriber(modid = VisualVein.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VPMapSearch {

    private static final String FULLSCREEN_CLASS = "journeymap.client.ui.fullscreen.Fullscreen";
    private static EditBox box;
    private static boolean wasFullscreen;

    private VPMapSearch() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!isFullscreen(event.getScreen())) {
            return;
        }
        wasFullscreen = true;
        Minecraft minecraft = Minecraft.getInstance();
        box = new EditBox(minecraft.font, 6, event.getScreen().height - 28, 180, 20,
                Component.translatable("vp.search.hint"));
        box.setMaxLength(80);
        box.setHint(Component.translatable("vp.search.hint"));
        box.setValue(MapSearch.query());
        box.setResponder(MapSearch::setQuery);
        box.setVisible(overlaysEnabled());
        event.addListener(box);
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!isFullscreen(event.getScreen()) || box == null) {
            return;
        }
        box.setX(6);
        box.setY(event.getScreen().height - 28);
        box.setVisible(overlaysEnabled());
        if (box.visible) {
            // JourneyMap's Fullscreen render loop only draws Button renderables, so an EditBox
            // added through ScreenEvent.Init.Post must be drawn explicitly.
            box.render(event.getGuiGraphics(), event.getMouseX(), event.getMouseY(), event.getPartialTick());
        }
    }

    /**
     * Called from the client tick: closing the fullscreen map clears the query immediately.
     */
    public static void tick() {
        boolean fullscreen = isFullscreen(Minecraft.getInstance().screen);
        if (wasFullscreen && !fullscreen) {
            MapSearch.clear();
            box = null;
        }
        wasFullscreen = fullscreen;
    }

    private static boolean overlaysEnabled() {
        return ClientHooks.overlayEnabled.getAsBoolean() || ClientHooks.fluidEnabled.getAsBoolean();
    }

    private static boolean isFullscreen(Object screen) {
        return screen != null && FULLSCREEN_CLASS.equals(screen.getClass().getName());
    }
}
