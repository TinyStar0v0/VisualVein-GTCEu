package com.visualvein.client.xaero;

import com.visualvein.VisualVein;
import com.visualvein.client.MapSearch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import xaero.lib.client.gui.widget.Tooltip;
import xaero.map.gui.GuiMap;
import xaero.map.gui.GuiTexturedButton;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Native Xaero sidebar buttons that are re-attached if Xaero rebuilds its widget list. */
@Mod.EventBusSubscriber(modid = VisualVein.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class XaeroSearchBox {

    private static final ResourceLocation ICONS =
            new ResourceLocation(VisualVein.MODID, "textures/gui/xaero_buttons.png");
    private static final int ICON = 16;
    private static final int SIZE = 20;
    private static final int FIELD_WIDTH = 150;
    private static final List<AbstractWidget> WIDGETS = new ArrayList<>();
    @Nullable
    private static GuiMap attachedTo;
    @Nullable
    private static XaeroInlineSearchField field;
    private static boolean fieldFocused;

    private XaeroSearchBox() {
    }

    public static void tick() {
        if (Minecraft.getInstance().screen instanceof GuiMap map) {
            ensureAttached(map);
        }
    }

    static void focusField() {
        fieldFocused = true;
    }

    static boolean isFieldFocused() {
        return fieldFocused && field != null && field.visible;
    }

    static boolean fieldVisible() {
        return field != null && field.visible;
    }

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof GuiMap map) {
            ensureAttached(map);
        }
    }

    private static void ensureAttached(GuiMap map) {
        if (attachedTo != map || WIDGETS.isEmpty()) {
            attachedTo = map;
            WIDGETS.clear();
            field = null;
            fieldFocused = false;
            createWidgets(map);
        }
        int x = map.width - SIZE;
        int y = map.height - 200;
        for (AbstractWidget widget : WIDGETS) {
            if (widget == field) {
                widget.setX(x - 4 - FIELD_WIDTH);
                widget.setY(y - 80);
            } else {
                widget.setX(x);
                int index = WIDGETS.indexOf(widget);
                widget.setY(y - index * 20);
            }
            if (!map.children().contains(widget)) {
                map.addButton(widget);
            } else if (!map.renderables.contains(widget)) {
                map.renderables.add((Renderable) widget);
            }
        }
    }

    private static void createWidgets(GuiMap map) {
        int x = map.width - SIZE;
        int y = map.height - 200;
        WIDGETS.add(textured(x, y, 0, "vp.button.ore", () -> XaeroDisplay.mode(XaeroDisplay.Mode.ORE)));
        WIDGETS.add(textured(x, y - 20, 1, "vp.button.fluid",
                () -> XaeroDisplay.mode(XaeroDisplay.Mode.FLUID)));
        WIDGETS.add(textured(x, y - 40, 2, "vp.button.hide",
                () -> XaeroDisplay.mode(XaeroDisplay.Mode.HIDDEN)));
        WIDGETS.add(textured(x, y - 60, 3, "vp.button.style", XaeroDisplay::toggleTexture));
        WIDGETS.add(textured(x, y - 80, 4, "vp.button.search", XaeroSearchBox::toggleField));

        field = new XaeroInlineSearchField(x - 4 - FIELD_WIDTH, y - 80, FIELD_WIDTH, SIZE);
        field.visible = false;
        WIDGETS.add(field);
    }

    private static GuiTexturedButton textured(int x, int y, int icon, String tooltipKey, Runnable action) {
        return new GuiTexturedButton(x, y, SIZE, SIZE, icon * ICON, 0, ICON, ICON, ICONS,
                button -> action.run(),
                () -> new Tooltip(Component.translatable(tooltipKey)));
    }

    private static void toggleField() {
        if (field == null) {
            return;
        }
        field.visible = !field.visible;
        fieldFocused = field.visible;
    }

    @SubscribeEvent
    public static void onKey(ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof GuiMap) || !isFieldFocused()) {
            return;
        }
        int key = event.getKeyCode();
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            String query = MapSearch.query();
            if (!query.isEmpty()) {
                MapSearch.setQuery(query.substring(0, query.length() - 1));
            }
            event.setCanceled(true);
        } else if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (field != null) {
                field.visible = false;
            }
            fieldFocused = false;
            event.setCanceled(true);
        } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            fieldFocused = false;
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onChar(ScreenEvent.CharacterTyped.Pre event) {
        if (!(event.getScreen() instanceof GuiMap) || !isFieldFocused()) {
            return;
        }
        char character = event.getCodePoint();
        if (character >= 32 && character != 127 && MapSearch.query().length() < 80) {
            MapSearch.setQuery(MapSearch.query() + character);
            event.setCanceled(true);
        }
    }
}
