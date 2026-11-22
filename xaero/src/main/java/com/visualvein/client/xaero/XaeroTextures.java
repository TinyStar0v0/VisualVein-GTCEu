package com.visualvein.client.xaero;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialStack;
import com.mojang.blaze3d.platform.NativeImage;
import com.visualvein.VisualVein;
import com.visualvein.data.OreEntry;
import com.visualvein.data.VeinRecord;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds the same single 16x16 composed ore marker used by the JourneyMap build: host-rock base,
 * tinted GTCEu ore overlay, light frame and depletion cross.
 */
public final class XaeroTextures {

    private static final Map<String, ResourceLocation> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> ICON_SETS = Set.of(
            "diamond", "dull", "fine", "flint", "lapis", "lignite", "paper", "powder", "quartz", "rough");

    private XaeroTextures() {
    }

    public static ResourceLocation markerTexture(XaeroVeinMarker marker) {
        return CACHE.computeIfAbsent(marker.key() + ":" + marker.depleted(),
                ignored -> create(marker));
    }

    private static ResourceLocation create(XaeroVeinMarker marker) {
        VeinRecord record = marker.group().primary();
        ResourceLocation dimension = Minecraft.getInstance().level == null
                ? new ResourceLocation("minecraft", "overworld")
                : Minecraft.getInstance().level.dimension().location();
        NativeImage base = load(baseTextureFor(record, dimension));
        NativeImage ore = load(oreTexture(record));
        NativeImage composite = compose(base, ore, tintFor(record), record.depleted);
        if (base != null) {
            base.close();
        }
        if (ore != null) {
            ore.close();
        }
        if (composite == null) {
            return new ResourceLocation(VisualVein.MODID, "textures/gui/ore_icon.png");
        }
        ResourceLocation id = new ResourceLocation(VisualVein.MODID,
                "dynamic/marker_" + Integer.toUnsignedString(marker.key().hashCode())
                        + "_" + (record.depleted ? "d" : "a"));
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(composite));
        return id;
    }

    @Nullable
    private static NativeImage load(ResourceLocation texture) {
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(texture).orElse(null);
            if (resource != null) {
                try (var input = resource.open()) {
                    return NativeImage.read(input);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static ResourceLocation oreTexture(VeinRecord record) {
        for (OreEntry ore : record.ores) {
            ResourceLocation blockId = ResourceLocation.tryParse(ore.id);
            if (blockId == null || !BuiltInRegistries.BLOCK.containsKey(blockId)) {
                continue;
            }
            try {
                Block block = BuiltInRegistries.BLOCK.get(blockId);
                MaterialStack stack = ChemicalHelper.getMaterial(block);
                String iconSet = stack == null || stack.material().getMaterialIconSet() == null
                        ? "dull" : stack.material().getMaterialIconSet().name;
                if (!ICON_SETS.contains(iconSet)) {
                    iconSet = "dull";
                }
                return new ResourceLocation("gtceu", "textures/block/material_sets/" + iconSet + "/ore.png");
            } catch (Throwable ignored) {
            }
        }
        return new ResourceLocation(VisualVein.MODID, "textures/gui/ore_icon.png");
    }

    private static ResourceLocation baseTextureFor(VeinRecord record, ResourceLocation dimension) {
        for (OreEntry ore : record.ores) {
            String id = ore.id.toLowerCase();
            if (id.contains("deepslate")) {
                return new ResourceLocation("minecraft", "textures/block/deepslate.png");
            }
            if (id.contains("netherrack")) {
                return new ResourceLocation("minecraft", "textures/block/netherrack.png");
            }
            if (id.contains("end_stone") || id.contains("endstone")) {
                return new ResourceLocation("minecraft", "textures/block/end_stone.png");
            }
        }
        String path = dimension.getPath();
        if (path.contains("nether")) {
            return new ResourceLocation("minecraft", "textures/block/netherrack.png");
        }
        if (path.contains("end")) {
            return new ResourceLocation("minecraft", "textures/block/end_stone.png");
        }
        return new ResourceLocation("minecraft", "textures/block/stone.png");
    }

    private static int tintFor(VeinRecord record) {
        if (record.depleted) {
            return 0xFF6666;
        }
        for (OreEntry ore : record.ores) {
            ResourceLocation blockId = ResourceLocation.tryParse(ore.id);
            if (blockId == null || !BuiltInRegistries.BLOCK.containsKey(blockId)) {
                continue;
            }
            try {
                MaterialStack stack = ChemicalHelper.getMaterial(BuiltInRegistries.BLOCK.get(blockId));
                Material material = stack == null ? null : stack.material();
                if (material != null) {
                    return material.getMaterialRGB();
                }
            } catch (Throwable ignored) {
            }
        }
        return record.ores.isEmpty() ? 0xFFFFFF : record.ores.get(0).color;
    }

    @Nullable
    private static NativeImage compose(@Nullable NativeImage base, @Nullable NativeImage ore,
                                       int tint, boolean depleted) {
        try {
            NativeImage output = new NativeImage(16, 16, false);
            int tintR = (tint >> 16) & 0xFF;
            int tintG = (tint >> 8) & 0xFF;
            int tintB = tint & 0xFF;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int basePixel = base == null ? 0 : base.getPixelRGBA(x, y);
                    int baseA = (basePixel >>> 24) & 0xFF;
                    int baseB = (basePixel >> 16) & 0xFF;
                    int baseG = (basePixel >> 8) & 0xFF;
                    int baseR = basePixel & 0xFF;
                    if (ore == null) {
                        output.setPixelRGBA(x, y, boostPixel(basePixel));
                        continue;
                    }
                    int orePixel = ore.getPixelRGBA(x, y);
                    int alpha = (orePixel >>> 24) & 0xFF;
                    int oreR = (orePixel & 0xFF) * tintR / 255;
                    int oreG = ((orePixel >> 8) & 0xFF) * tintG / 255;
                    int oreB = ((orePixel >> 16) & 0xFF) * tintB / 255;
                    int outR = (oreR * alpha + baseR * (255 - alpha)) / 255;
                    int outG = (oreG * alpha + baseG * (255 - alpha)) / 255;
                    int outB = (oreB * alpha + baseB * (255 - alpha)) / 255;
                    int outA = Math.max(baseA, alpha);
                    output.setPixelRGBA(x, y, (outA << 24)
                            | (boost(outB) << 16) | (boost(outG) << 8) | boost(outR));
                }
            }
            for (int i = 0; i < 16; i++) {
                int border = 0xFFD8E0E0;
                output.setPixelRGBA(i, 0, border);
                output.setPixelRGBA(i, 15, border);
                output.setPixelRGBA(0, i, border);
                output.setPixelRGBA(15, i, border);
            }
            if (depleted) {
                int red = 0xFF3030FF;
                for (int i = 0; i < 16; i++) {
                    output.setPixelRGBA(i, i, red);
                    output.setPixelRGBA(i, 15 - i, red);
                    if (i < 15) {
                        output.setPixelRGBA(i + 1, i, red);
                        output.setPixelRGBA(i + 1, 15 - i, red);
                    }
                }
            }
            return output;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int boostPixel(int pixel) {
        int a = (pixel >>> 24) & 0xFF;
        int b = (pixel >> 16) & 0xFF;
        int g = (pixel >> 8) & 0xFF;
        int r = pixel & 0xFF;
        return (a << 24) | (boost(b) << 16) | (boost(g) << 8) | boost(r);
    }

    private static int boost(int channel) {
        return Math.min(255, (channel * 5) / 4 + 24);
    }
}
