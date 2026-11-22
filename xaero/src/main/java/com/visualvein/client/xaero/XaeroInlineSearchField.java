package com.visualvein.client.xaero;

import com.visualvein.client.MapSearch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Inline search field that sits immediately left of the search icon in Xaero's sidebar. */
final class XaeroInlineSearchField extends Button {

    XaeroInlineSearchField(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty(), button -> XaeroSearchBox.focusField(),
                button -> Component.empty());
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        boolean focused = XaeroSearchBox.isFieldFocused();
        int border = focused ? 0xFFFFD27F : 0xFF5A6470;
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, border);
        graphics.fill(x, y, x + width, y + height, 0xE0101418);
        String query = MapSearch.query();
        String text = query.isEmpty()
                ? Component.translatable("vp.search.hint").getString() : query;
        int color = query.isEmpty() ? 0xFF8A939C : 0xFFFFFFFF;
        graphics.drawString(Minecraft.getInstance().font, text, x + 5, y + 6, color, false);
        if (focused) {
            int cursorX = x + 5 + Minecraft.getInstance().font.width(query);
            graphics.fill(cursorX, y + 5, cursorX + 1, y + height - 5, 0xFFFFD27F);
        }
    }
}
