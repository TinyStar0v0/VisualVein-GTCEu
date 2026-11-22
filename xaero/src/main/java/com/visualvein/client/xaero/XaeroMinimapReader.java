package com.visualvein.client.xaero;

import net.minecraft.client.Minecraft;
import xaero.hud.minimap.element.render.MinimapElementReader;
import xaero.hud.minimap.element.render.MinimapElementRenderLocation;

public final class XaeroMinimapReader extends MinimapElementReader<XaeroVeinMarker, XaeroRenderContext> {

    @Override
    public boolean isHidden(XaeroVeinMarker marker, XaeroRenderContext context) {
        return false;
    }

    @Override
    public double getRenderX(XaeroVeinMarker marker, XaeroRenderContext context, float partialTicks) {
        return marker.x();
    }

    @Override
    public double getRenderY(XaeroVeinMarker marker, XaeroRenderContext context, float partialTicks) {
        return marker.y();
    }

    @Override
    public double getRenderZ(XaeroVeinMarker marker, XaeroRenderContext context, float partialTicks) {
        return marker.z();
    }

    @Override
    public int getInteractionBoxLeft(XaeroVeinMarker marker, XaeroRenderContext context, float partialTicks) {
        return -8;
    }

    @Override
    public int getInteractionBoxRight(XaeroVeinMarker marker, XaeroRenderContext context, float partialTicks) {
        return 8;
    }

    @Override
    public int getInteractionBoxTop(XaeroVeinMarker marker, XaeroRenderContext context, float partialTicks) {
        return -8;
    }

    @Override
    public int getInteractionBoxBottom(XaeroVeinMarker marker, XaeroRenderContext context, float partialTicks) {
        return 8;
    }

    @Override
    public int getRenderBoxLeft(XaeroVeinMarker marker, XaeroRenderContext context, float partialTicks) {
        return -8;
    }

    @Override
    public int getRenderBoxRight(XaeroVeinMarker marker, XaeroRenderContext context, float partialTicks) {
        return 8;
    }

    @Override
    public int getRenderBoxTop(XaeroVeinMarker marker, XaeroRenderContext context, float partialTicks) {
        return -8;
    }

    @Override
    public int getRenderBoxBottom(XaeroVeinMarker marker, XaeroRenderContext context, float partialTicks) {
        return 8;
    }

    @Override
    public int getLeftSideLength(XaeroVeinMarker marker, Minecraft minecraft) {
        return 0;
    }

    @Override
    public String getMenuName(XaeroVeinMarker marker) {
        return marker.name();
    }

    @Override
    public String getFilterName(XaeroVeinMarker marker) {
        return marker.name();
    }

    @Override
    public int getMenuTextFillLeftPadding(XaeroVeinMarker marker) {
        return 0;
    }

    @Override
    public int getRightClickTitleBackgroundColor(XaeroVeinMarker marker) {
        return marker.color();
    }

    @Override
    public boolean shouldScaleBoxWithOptionalScale() {
        return true;
    }

    @Override
    public boolean isInteractable(MinimapElementRenderLocation location, XaeroVeinMarker marker) {
        return true;
    }
}
