package com.visualvein.client.xaero;

import com.visualvein.data.VeinGroup;
import com.visualvein.data.VeinRecord;

/**
 * One map element shown by Xaero's World Map/Minimap renderer.
 */
public record XaeroVeinMarker(String key, VeinGroup group, int x, int y, int z,
                              String name, String title, int color, boolean depleted) {

    public static XaeroVeinMarker of(VeinGroup group, String name, String title) {
        VeinRecord record = group.primary();
        int color = record.depleted ? 0xAA3333
                : (record.ores.isEmpty() ? 0x888888 : record.ores.get(0).color);
        return new XaeroVeinMarker(group.key(), group, record.centerX, record.surfaceY != 0
                ? record.surfaceY : record.centerY, record.centerZ, name, title, color, record.depleted);
    }
}
