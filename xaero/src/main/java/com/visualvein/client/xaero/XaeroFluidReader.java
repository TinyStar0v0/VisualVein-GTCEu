package com.visualvein.client.xaero;

import com.visualvein.network.TeleportToVeinPacket;
import com.visualvein.network.VPNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xaero.lib.client.gui.widget.Tooltip;
import xaero.map.element.MapElementReader;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

import java.util.ArrayList;

public final class XaeroFluidReader extends MapElementReader<XaeroFluidMarker, XaeroRenderContext, XaeroFluidRenderer> {

    @Override
    public boolean isHidden(XaeroFluidMarker marker, XaeroRenderContext context) {
        return !XaeroDisplay.showFluid();
    }

    @Override
    public double getRenderX(XaeroFluidMarker marker, XaeroRenderContext context, float partialTicks) {
        return marker.x();
    }

    @Override
    public double getRenderZ(XaeroFluidMarker marker, XaeroRenderContext context, float partialTicks) {
        return marker.z();
    }

    @Override
    public int getInteractionBoxLeft(XaeroFluidMarker marker, XaeroRenderContext context, float partialTicks) {
        return -24;
    }

    @Override
    public int getInteractionBoxRight(XaeroFluidMarker marker, XaeroRenderContext context, float partialTicks) {
        return 24;
    }

    @Override
    public int getInteractionBoxTop(XaeroFluidMarker marker, XaeroRenderContext context, float partialTicks) {
        return -24;
    }

    @Override
    public int getInteractionBoxBottom(XaeroFluidMarker marker, XaeroRenderContext context, float partialTicks) {
        return 24;
    }

    @Override
    public int getRenderBoxLeft(XaeroFluidMarker marker, XaeroRenderContext context, float partialTicks) {
        return -24;
    }

    @Override
    public int getRenderBoxRight(XaeroFluidMarker marker, XaeroRenderContext context, float partialTicks) {
        return 24;
    }

    @Override
    public int getRenderBoxTop(XaeroFluidMarker marker, XaeroRenderContext context, float partialTicks) {
        return -24;
    }

    @Override
    public int getRenderBoxBottom(XaeroFluidMarker marker, XaeroRenderContext context, float partialTicks) {
        return 24;
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

    @Override
    public Tooltip getTooltip(XaeroFluidMarker marker, XaeroRenderContext context, boolean hovered) {
        return XaeroTooltips.lines(marker.title());
    }

    @Override
    public boolean isInteractable(xaero.map.element.render.ElementRenderLocation location,
                                  XaeroFluidMarker marker) {
        return true;
    }

    @Override
    public ArrayList<RightClickOption> getRightClickOptions(XaeroFluidMarker marker,
                                                            xaero.map.gui.IRightClickableElement parent) {
        ArrayList<RightClickOption> options = new ArrayList<>();
        options.add(new RightClickOption(
                Component.translatable("vp.menu.create_waypoint").getString(), marker.color(), parent) {
            @Override
            public void onAction(Screen screen) {
                XaeroWaypointBridge.createFluid(marker.record(), marker.name());
            }
        });
        options.add(new RightClickOption(
                Component.translatable("vp.menu.teleport").getString(), marker.color(), parent) {
            @Override
            public void onAction(Screen screen) {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.level != null) {
                    VPNetwork.sendToServer(new TeleportToVeinPacket(
                            minecraft.level.dimension().location(), marker.record().key()));
                }
            }
        });
        return options;
    }

    @Override
    public boolean isRightClickValid(XaeroFluidMarker marker) {
        return true;
    }
}
