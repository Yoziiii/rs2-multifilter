package dev.rs2multifilter.minecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Strict old-RS-style item comparison (item ID + damage + component/NBT data).
 * Plain ITEM rules deliberately remain ID-only, matching ignore-NBT mode.
 */
public final class ExactItemBridge {
    private static final HexFormat HEX = HexFormat.of();
    private static final Map<String, ItemStack> CACHE = new ConcurrentHashMap<>();
    private ExactItemBridge() {}

    public static String encode(ItemStack input) {
        if (input.isEmpty() || Minecraft.getInstance().level == null) return null;
        try {
            CompoundTag nbt = input.copyWithCount(1).save(Minecraft.getInstance().level.registryAccess());
            String value = "exact:" + HEX.formatHex(nbt.toString().getBytes(StandardCharsets.UTF_8));
            if (value.length() > 4096) return null;
            CACHE.put(value, input.copyWithCount(1));
            return value;
        } catch (Exception e) { return null; }
    }

    private static ItemStack read(String value) {
        ItemStack cached = CACHE.get(value);
        if (cached != null) return cached;
        if (!value.startsWith("exact:") || Minecraft.getInstance().level == null) return ItemStack.EMPTY;
        try {
            String snbt = new String(HEX.parseHex(value.substring(6)), StandardCharsets.UTF_8);
            if (snbt.length() > 2048) return ItemStack.EMPTY;
            ItemStack parsed = ItemStack.parseOptional(Minecraft.getInstance().level.registryAccess(),
                TagParser.parseTag(snbt));
            if (CACHE.size() > 512) CACHE.clear();
            CACHE.put(value, parsed);
            return parsed;
        } catch (Exception e) { return ItemStack.EMPTY; }
    }

    public static boolean matches(String value, ItemStack candidate) {
        ItemStack reference = read(value);
        return !reference.isEmpty() && !candidate.isEmpty()
            && ItemStack.isSameItemSameComponents(reference, candidate);
    }

    public static String describe(String value) {
        ItemStack stored = read(value);
        return stored.isEmpty() ? "精确物品（附加数据）"
            : "精确物品 " + stored.getHoverName().getString();
    }
}
