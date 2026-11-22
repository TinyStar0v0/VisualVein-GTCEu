package com.visualvein.data;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * Translation helpers shared by the server (command output) and the client (map labels).
 */
public final class VeinNames {

    private VeinNames() {
    }

    public static Component veinName(ResourceLocation id) {
        String path = id.getPath();
        return Component.translatableWithFallback("gtceu.jei.ore_vein." + path, prettify(path));
    }

    public static String plainVeinName(ResourceLocation id) {
        return veinName(id).getString();
    }

    public static Component oreName(OreEntry ore) {
        if (!ore.translationKey.isEmpty()) {
            return Component.translatableWithFallback(ore.translationKey, prettify(lastPath(ore.id)));
        }
        return Component.literal(prettify(lastPath(ore.id)));
    }

    public static Component layerName(String layer) {
        return Component.translatableWithFallback("vp.layer." + layer, prettify(layer));
    }

    public static Component dimensionName(ResourceLocation dimension) {
        String path = dimension.getPath();
        return Component.translatableWithFallback("vp.dimension." + path, prettify(path));
    }

    public static String prettify(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String[] parts = value.split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) {
                result.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return result.toString();
    }

    public static String lastPath(String id) {
        int colon = id.indexOf(':');
        String path = colon >= 0 ? id.substring(colon + 1) : id;
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    public static ChatFormatting layerColor(String layer) {
        return switch (layer) {
            case "stone" -> ChatFormatting.GRAY;
            case "deepslate" -> ChatFormatting.DARK_GRAY;
            case "netherrack" -> ChatFormatting.DARK_RED;
            case "endstone" -> ChatFormatting.DARK_PURPLE;
            default -> ChatFormatting.AQUA;
        };
    }
}
