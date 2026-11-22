package com.visualvein.client.xaero;

import net.minecraft.client.Minecraft;
import xaero.hud.minimap.element.render.MinimapElementReader;

public final class XaeroFluidMinimapReader
        extends MinimapElementReader<XaeroFluidMarker, XaeroRenderContext> {

    @Override
    public boolean isHidden(XaeroFluidMarker marker, XaeroRenderContext context) {
        return false;
    }

    @Override
    public double getRenderX(XaeroFluidMarker marker, XaeroRenderContext context, float partialTicks) {
        return marker.x();
    }

    @Override
    public double getRenderY(XaeroFluidMarker marker, XaeroRenderContext context, float partialTicks) {
        return 64;
    }

    @Override
    public double getRenderZ(XaeroFluidMarker marker, XaeroRenderContext context, float partialTicks) {
        return marker.z();
    }

    @Override
    public int getInteractionBoxLeft(XaeroFluidMarker marker, XaeroRenderContext context, float partialTicks) {
        return -8;
    }

    @Override
    public int getInteractionBoxRight(XaeroFluidMarker marker, XaeroRenderContext context, float partialTicks) {
        return 8;
    }

    @Override
    public int getInteractionBoxTop(XaeroFluidMarker marker, XaeroRenderContext context, float partialTicks) {
        return -8;
    }

    @Override
    public int getInteractionBoxBottom(XaeroFluidMarker marker, XaeroRenderContext context, float partialTicks) {
        return 8;
    }

    @Override
    public int getRenderBoxLeft(XaeroFluidMarker marker, XaeroRenderContext context, float partialTicks) {
        return -8;
    }

    @Override
    public int getRenderBoxRight(XaeroFluidMarker marker, XaeroRenderContext context, float partialTicks) {
        return 8;
    }

    @Override
    public int getRenderBoxTop(XaeroFluidMarker marker, XaeroRenderContext context, float partialTicks) {
        return -8;
    }

    @Override
    public int getRenderBoxBottom(XaeroFluidMarker marker, XaeroRenderContext context, float partialTicks) {
        return 8;
    }

    @Override
    public int getLeftSideLength(XaeroFluidMarker marker, Minecraft minecraft) {
        return 0;
    }

    @Override
    public String getMenuName(XaeroFluidMarker marker) {
        return marker.name();
    }

    @Override
    public String getFilterName(XaeroFluidMarker marker) {
        return marker.name();
    }

    @Override
    public int getMenuTextFillLeftPadding(XaeroFluidMarker marker) {
        return 0;
    }

    @Override
    public int getRightClickTitleBackgroundColor(XaeroFluidMarker marker) {
        return marker.color();
    }

    @Override
    public boolean shouldScaleBoxWithOptionalScale() {
        return true;
    }
}
