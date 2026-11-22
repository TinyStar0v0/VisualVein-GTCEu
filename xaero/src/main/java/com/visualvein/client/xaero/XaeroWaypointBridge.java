package com.visualvein.client.xaero;

import com.visualvein.VisualVein;
import com.visualvein.client.ClientVeinCache;
import com.visualvein.client.WaypointSurface;
import com.visualvein.data.FluidVeinRecord;
import com.visualvein.data.VeinNames;
import com.visualvein.data.VeinRecord;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import xaero.common.XaeroMinimapSession;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.common.minimap.waypoints.WaypointWorld;
import xaero.common.minimap.waypoints.WaypointsManager;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypointManager;
import xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypoints;

/**
 * Creates a real Xaero waypoint at the nearest recorded vein. The Xaero third-party waypoint set is
 * shared by Xaero's Minimap and World Map.
 */
public final class XaeroWaypointBridge {

    private static final ResourceLocation ORIGIN = new ResourceLocation(VisualVein.MODID, "veins");

    private XaeroWaypointBridge() {
    }

    public static void createNearest() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        ClientVeinCache.DimensionData data = ClientVeinCache.get(minecraft.level.dimension().location());
        if (data == null) {
            message("vp.msg.no_data");
            return;
        }
        VeinRecord record = data.nearest(minecraft.player.getX(), minecraft.player.getZ());
        if (record == null) {
            message("vp.msg.no_data");
            return;
        }
        create(record);
    }

    public static void createFluid(FluidVeinRecord record, String name) {
        try {
            XaeroMinimapSession session = XaeroMinimapSession.getCurrentSession();
            if (session == null) {
                message("vp.msg.no_xaero");
                return;
            }
            WaypointsManager manager = session.getWaypointsManager();
            WaypointWorld world = manager == null ? null : manager.getCurrentWorld();
            if (world == null) {
                message("vp.msg.no_data");
                return;
            }
            ThirdPartyWaypoints set = world.getContainer().getThirdPartyWaypointManager().get(ORIGIN);
            set.setEnabledStateGetter(() -> true);
            String id = "fluid:" + record.key();
            if (set.get(id) != null) {
                message("vp.msg.waypoint_exists", name);
                return;
            }
            int y = WaypointSurface.y(world.getDimId(), record.centerX(), record.centerZ(), 64);
            set.add(id, new Waypoint(record.centerX(), y, record.centerZ(), "VP: " + name,
                    initials(name), WaypointColor.AQUA, WaypointPurpose.NORMAL));
            message("vp.msg.waypoint_created", name);
        } catch (Throwable throwable) {
            VisualVein.LOGGER.warn("Could not create Xaero fluid waypoint", throwable);
            message("vp.msg.no_xaero");
        }
    }

    public static void create(VeinRecord record) {
        try {
            XaeroMinimapSession session = XaeroMinimapSession.getCurrentSession();
            if (session == null) {
                message("vp.msg.no_xaero");
                return;
            }
            WaypointsManager manager = session.getWaypointsManager();
            WaypointWorld world = manager == null ? null : manager.getCurrentWorld();
            if (world == null) {
                message("vp.msg.no_data");
                return;
            }
            ThirdPartyWaypointManager manager3 = world.getContainer().getThirdPartyWaypointManager();
            ThirdPartyWaypoints set = manager3.get(ORIGIN);
            set.setEnabledStateGetter(() -> true);
            String name = "VP: " + VeinNames.veinName(record.veinId).getString();
            String id = record.key();
            if (set.get(id) != null) {
                message("vp.msg.waypoint_exists", name);
                return;
            }
            int y = WaypointSurface.y(world.getDimId(), record);
            String initials = initials(name);
            Waypoint waypoint = new Waypoint(record.centerX, y, record.centerZ, name, initials,
                    record.depleted ? WaypointColor.RED : WaypointColor.GOLD,
                    WaypointPurpose.NORMAL);
            set.add(id, waypoint);
            message("vp.msg.waypoint_created", name);
        } catch (Throwable throwable) {
            VisualVein.LOGGER.warn("Could not create Xaero waypoint", throwable);
            message("vp.msg.no_xaero");
        }
    }

    private static String initials(String name) {
        String[] parts = name.replaceAll("[^\\p{L}\\p{N}]", " ").trim().split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty() && result.length() < 2) {
                result.append(Character.toUpperCase(part.charAt(0)));
            }
        }
        return result.isEmpty() ? "VP" : result.toString();
    }

    private static void message(String key, Object... args) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable(key, args), true);
        }
    }
}
