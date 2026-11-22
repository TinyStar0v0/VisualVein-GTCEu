package com.visualvein.client.xaero;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import xaero.lib.client.gui.widget.Tooltip;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Builds Xaero tooltips with one forced line per supplied item. */
final class XaeroTooltips {

    private XaeroTooltips() {
    }

    static Tooltip lines(String text) {
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\\R")) {
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        Tooltip tooltip = new Tooltip(1);
        try {
            Field stringsField = Tooltip.class.getDeclaredField("strings");
            stringsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            ArrayList<Component> strings = (ArrayList<Component>) stringsField.get(tooltip);
            strings.clear();
            int maxWidth = 0;
            for (String line : lines) {
                Component component = Component.literal(line);
                strings.add(component);
                maxWidth = Math.max(maxWidth, Minecraft.getInstance().font.width(component));
            }
            tooltip.withWidth(Math.min(420, maxWidth + 28));
            Field customField = Tooltip.class.getDeclaredField("customLines");
            customField.setAccessible(true);
            customField.setBoolean(tooltip, true);
        } catch (Throwable ignored) {
            // If Xaero changes its internal fields, fall back to a normal tooltip.
            return new Tooltip(Component.literal(text));
        }
        return tooltip;
    }
}
