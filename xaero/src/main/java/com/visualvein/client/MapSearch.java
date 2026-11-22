package com.visualvein.client;

import com.visualvein.data.FluidVeinRecord;
import com.visualvein.data.OreEntry;
import com.visualvein.data.VeinGroup;
import com.visualvein.data.VeinNames;
import com.visualvein.data.VeinRecord;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fullscreen-map search state. The query is intentionally client-only and is never sent to the
 * server or applied to the minimap.
 */
public final class MapSearch {

    private static final Map<String, Pinyin> PINYIN_CACHE = new ConcurrentHashMap<>();
    private static final Map<Character, String> FALLBACK_PINYIN = new HashMap<>();

    static {
        // Small fallback for clients where ICU4J is unavailable. The normal path uses ICU4J's
        // Han-Latin transliterator, which covers the full GT material vocabulary.
        String[][] fallback = {
                {"铜", "tong"}, {"铁", "tie"}, {"锡", "xi"}, {"铅", "qian"}, {"锌", "xin"},
                {"镍", "nie"}, {"钴", "gu"}, {"银", "yin"}, {"金", "jin"}, {"铂", "bo"},
                {"钨", "wu"}, {"钼", "mu"}, {"锰", "meng"}, {"铬", "ge"}, {"钛", "tai"},
                {"铝", "lv"}, {"镁", "mei"}, {"锂", "li"}, {"钠", "na"}, {"钾", "jia"},
                {"钙", "gai"}, {"铀", "you"}, {"钍", "tu"}, {"煤", "mei"}, {"石", "shi"},
                {"矿", "kuang"}, {"脉", "mai"}, {"黄", "huang"}, {"深", "shen"}, {"板", "ban"},
                {"岩", "yan"}, {"地", "di"}, {"狱", "yu"}, {"末", "mo"}, {"沙", "sha"},
                {"盐", "yan"}, {"硫", "liu"}, {"磷", "lin"}, {"硅", "gui"}, {"油", "you"},
                {"天", "tian"}, {"然", "ran"}, {"气", "qi"}, {"水", "shui"}, {"稀", "xi"},
                {"土", "tu"}, {"黑", "hei"}, {"白", "bai"}, {"红", "hong"}, {"蓝", "lan"},
                {"绿", "lv"}, {"青", "qing"}, {"紫", "zi"}, {"晶", "jing"}
        };
        for (String[] entry : fallback) {
            for (int i = 0; i < entry[0].length(); i++) {
                FALLBACK_PINYIN.put(entry[0].charAt(i), entry[1]);
            }
        }
    }

    private static String query = "";

    private MapSearch() {
    }

    public static String query() {
        return query;
    }

    public static boolean active() {
        return !query.isBlank();
    }

    public static void setQuery(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.equals(query)) {
            return;
        }
        query = normalized;
        ClientHooks.searchChanged.run();
    }

    public static void clear() {
        if (query.isEmpty()) {
            return;
        }
        query = "";
        ClientHooks.searchChanged.run();
    }

    public static boolean matchesOre(VeinGroup group) {
        if (!active()) {
            return true;
        }
        String needle = query.toLowerCase(Locale.ROOT);
        for (VeinRecord record : group.layers()) {
            if (matches(needle, VeinNames.veinName(record.veinId).getString())
                    || matches(needle, record.veinId.toString())) {
                return true;
            }
            for (OreEntry ore : record.ores) {
                if (matchesOre(needle, ore)) {
                    return true;
                }
            }
            for (OreEntry ore : record.surfaceOres) {
                if (matchesOre(needle, ore)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesOre(String needle, OreEntry ore) {
        return matches(needle, VeinNames.oreName(ore).getString())
                || matches(needle, ore.id)
                || matches(needle, ore.translationKey);
    }

    public static boolean matchesFluid(FluidVeinRecord record) {
        if (!active()) {
            return true;
        }
        String needle = query.toLowerCase(Locale.ROOT);
        Fluid fluid = BuiltInRegistries.FLUID.get(record.fluidId());
        if (fluid != null) {
            Component name = new FluidStack(fluid, 1).getDisplayName();
            if (matches(needle, name.getString())) {
                return true;
            }
        }
        ResourceLocation id = record.fluidId();
        return matches(needle, id.toString()) || matches(needle, id.getPath());
    }

    private static boolean matches(String needle, String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains(needle)) {
            return true;
        }
        String latin = needle.replaceAll("[^a-z0-9]", "");
        if (latin.isEmpty()) {
            return false;
        }
        Pinyin pinyin = pinyinOf(value);
        return pinyin.full.contains(latin) || pinyin.initials.contains(latin);
    }

    private static Pinyin pinyinOf(String value) {
        return PINYIN_CACHE.computeIfAbsent(value, MapSearch::computePinyin);
    }

    private static Pinyin computePinyin(String value) {
        String latin = transliterate(value);
        String normalized = Normalizer.normalize(latin, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        StringBuilder full = new StringBuilder();
        StringBuilder initials = new StringBuilder();
        for (String token : normalized.split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            full.append(token);
            initials.append(token.charAt(0));
        }
        return new Pinyin(full.toString(), initials.toString());
    }

    private static String transliterate(String value) {
        try {
            Class<?> type = Class.forName("com.ibm.icu.text.Transliterator");
            Object transliterator = type.getMethod("getInstance", String.class)
                    .invoke(null, "Han-Latin");
            return (String) type.getMethod("transliterate", String.class)
                    .invoke(transliterator, value);
        } catch (Throwable ignored) {
            StringBuilder fallback = new StringBuilder();
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                String pinyin = FALLBACK_PINYIN.get(c);
                fallback.append(pinyin == null ? String.valueOf(c) : pinyin).append(' ');
            }
            return fallback.toString();
        }
    }

    private record Pinyin(String full, String initials) {
    }
}
