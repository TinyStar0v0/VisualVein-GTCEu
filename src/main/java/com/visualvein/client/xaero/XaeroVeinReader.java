package com.visualvein.client.xaero;

import com.visualvein.network.SetVeinStatusPacket;
import com.visualvein.network.TeleportToVeinPacket;
import com.visualvein.network.VPNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xaero.lib.client.gui.widget.Tooltip;
import xaero.map.element.MapElementReader;
import xaero.map.element.render.ElementRenderLocation;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

import java.util.ArrayList;

public final class XaeroVeinReader extends MapElementReader<XaeroVeinMarker, XaeroRenderContext, XaeroVeinRenderer> {

    @Override
    public boolean isHidden(XaeroVeinMarker marker, XaeroRenderContext context) {
        return !XaeroDisplay.showOre();
    }

    @Override
    public double getRenderX(XaeroVeinMarker marker, XaeroRenderContext context, float partialTicks) {
        return marker.x();
    }

    @Override
    public double getRenderZ(XaeroVeinMarker marker, XaeroRenderContext context, float partialTicks) {
        return marker.z();
    }

    @Override
    public double getRenderY(XaeroVeinMarker marker, XaeroRenderContext context, float partialTicks) {
        return marker.y();
    }

    @Override
    public boolean hasYCoordinate() {
        return true;
    }

    @Override
    public int getInteractionBoxLeft(XaeroVeinMarker marker, XaeroRenderContext context, float partialTicks) {
        return -32;
    }

    @Override
    public int getInteractionBoxRight(XaeroVeinMarker marker, XaeroRenderContext context, float partialTicks) {
        return 32;
    }

    @Override
    public int getInteractionBoxTop(XaeroVeinMarker marker, XaeroRenderContext context, float partialTicks) {
        return -32;
    }

    @Override
    public int getInteractionBoxBottom(XaeroVeinMarker marker, XaeroRenderContext context, float partialTicks) {
        return 32;
    }

    @Override
    public int getRenderBoxLeft(XaeroVeinMarker marker, XaeroRenderContext context, float partialTicks) {
        return -32;
    }

    @Override
    public int getRenderBoxRight(XaeroVeinMarker marker, XaeroRenderContext context, float partialTicks) {
        return 32;
    }

    @Override
    public int getRenderBoxTop(XaeroVeinMarker marker, XaeroRenderContext context, float partialTicks) {
        return -32;
    }

    @Override
    public int getRenderBoxBottom(XaeroVeinMarker marker, XaeroRenderContext context, float partialTicks) {
        return 32;
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
    public Tooltip getTooltip(XaeroVeinMarker marker, XaeroRenderContext context, boolean hovered) {
        return XaeroTooltips.lines(marker.title());
    }

    public static void sendStatus(XaeroVeinMarker marker, boolean depleted) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        VPNetwork.sendToServer(new SetVeinStatusPacket(
                minecraft.level.dimension().location(),
                marker.group().primary().key(),
                depleted));
    }

    @Override
    public boolean isInteractable(ElementRenderLocation location, XaeroVeinMarker marker) {
        return true;
    }

    @Override
    public ArrayList<RightClickOption> getRightClickOptions(XaeroVeinMarker marker,
                                                            xaero.map.gui.IRightClickableElement parent) {
        ArrayList<RightClickOption> options = new ArrayList<>();
        options.add(new RightClickOption(
                Component.translatable("vp.menu.create_waypoint").getString(), marker.color(), parent) {
            @Override
            public void onAction(Screen screen) {
                XaeroWaypointBridge.create(marker.group().primary());
            }
        });
        options.add(new RightClickOption(
                Component.translatable("vp.menu.teleport").getString(), marker.color(), parent) {
            @Override
            public void onAction(Screen screen) {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.level != null) {
                    VPNetwork.sendToServer(new TeleportToVeinPacket(
                            minecraft.level.dimension().location(), marker.group().primary().key()));
                }
            }
        });
        options.add(new RightClickOption(
                Component.translatable("vp.menu.mark_depleted").getString(), marker.color(), parent) {
            @Override
            public void onAction(Screen screen) {
                sendStatus(marker, true);
            }
        });
        options.add(new RightClickOption(
                Component.translatable("vp.menu.mark_active").getString(), marker.color(), parent) {
            @Override
            public void onAction(Screen screen) {
                sendStatus(marker, false);
            }
        });
        return options;
    }

    @Override
    public boolean isRightClickValid(XaeroVeinMarker marker) {
        return true;
    }
}
