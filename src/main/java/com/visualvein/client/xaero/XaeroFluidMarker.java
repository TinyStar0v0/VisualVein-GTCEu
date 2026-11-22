package com.visualvein.client.xaero;

import com.visualvein.data.FluidVeinRecord;

public record XaeroFluidMarker(String key, FluidVeinRecord record, int x, int z,
                               int color, String name, String title) {
}
