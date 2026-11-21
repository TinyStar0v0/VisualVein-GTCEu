package com.visualvein.client;

import com.visualvein.VisualVein;
import journeymap.api.v2.client.event.FullscreenRenderEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Search field for the JourneyMap fullscreen map. JourneyMap 6.0.7 has no text-input API for addons,
 * and its Fullscreen screen neither calls Screen.init() nor Screen.render(); therefore the field is
 * created lazily, kept out of the Button-only renderables list, and drawn from RenderGuiEvent.Post.
 */
@Mod.EventBusSubscriber(modid = VisualVein.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VPMapSearch {

    private static final String FULLSCREEN_CLASS = "journeymap.client.ui.fullscreen.Fullscreen";
    @Nullable
    private static EditBox box;
    private static boolean wasFullscreen;

    private VPMapSearch() {
    }

    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            tick();
        }
    }

    public static void render(FullscreenRenderEvent event) {
        Screen screen = event.getFullscreen().getScreen();
        if (!isFullscreen(screen)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (box == null) {
            createBox(minecraft, screen);
        }
        updateBox(screen);
        if (box != null && box.visible) {
            box.render(event.getGraphics(), event.getMouseX(), event.getMouseY(), event.getPartialTicks());
        }
    }

    private static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.screen;
        boolean fullscreen = isFullscreen(screen);
        if (wasFullscreen && !fullscreen) {
            MapSearch.clear();
            box = null;
        }
        wasFullscreen = fullscreen;
        if (!fullscreen) {
            return;
        }
        if (box == null) {
            createBox(minecraft, screen);
        }
        updateBox(screen);
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!isFullscreen(event.getScreen()) || box == null || !box.visible) {
            return;
        }
        if (box.isMouseOver(event.getMouseX(), event.getMouseY())) {
            box.setFocused(true);
            box.mouseClicked(event.getMouseX(), event.getMouseY(), event.getButton());
            event.setCanceled(true);
            return;
        }
        if (box.isFocused()) {
            box.setFocused(false);
        }
    }

    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (!isFullscreen(event.getScreen()) || box == null || !box.isFocused()) {
            return;
        }
        int key = event.getKeyCode();
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            box.setFocused(false);
            event.setCanceled(true);
            return;
        }
        // JourneyMap sends unhandled typing to its coordinate search/chat; while our field is
        // focused, it must receive the key instead.
        box.keyPressed(key, event.getScanCode(), event.getModifiers());
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onCharTyped(ScreenEvent.CharacterTyped.Pre event) {
        if (!isFullscreen(event.getScreen()) || box == null || !box.isFocused()) {
            return;
        }
        box.charTyped(event.getCodePoint(), event.getModifiers());
        event.setCanceled(true);
    }

    private static void createBox(Minecraft minecraft, Screen screen) {
        if (!isFullscreen(screen)) {
            return;
        }
        box = new EditBox(minecraft.font, 6, screen.height - 28, 180, 20,
                Component.translatable("vp.search.hint"));
        box.setMaxLength(80);
        box.setHint(Component.translatable("vp.search.hint"));
        box.setValue(MapSearch.query());
        box.setResponder(MapSearch::setQuery);
        box.setVisible(overlaysEnabled());
        try {
            @SuppressWarnings("unchecked")
            List<GuiEventListener> children = (List<GuiEventListener>) (List<?>) screen.children();
            children.add(box);
        } catch (Throwable throwable) {
            VisualVein.LOGGER.warn("Could not attach VisualVein map search box", throwable);
        }
    }

    private static void updateBox(Screen screen) {
        if (box == null) {
            return;
        }
        box.setX(6);
        box.setY(screen.height - 28);
        boolean visible = overlaysEnabled();
        if (!visible && box.isFocused()) {
            box.setFocused(false);
        }
        box.setVisible(visible);
    }

    private static boolean overlaysEnabled() {
        return ClientHooks.overlayEnabled.getAsBoolean() || ClientHooks.fluidEnabled.getAsBoolean();
    }

    private static boolean isFullscreen(@Nullable Screen screen) {
        return screen != null && FULLSCREEN_CLASS.equals(screen.getClass().getName());
    }
}
