package dev.rs2multifilter.minecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

/**
 * Strict old-RS-style item comparison (item ID + damage + component/NBT data).
 * Plain ITEM rules deliberately remain ID-only, matching ignore-NBT mode.
 */
public final class ExactItemBridge {
    private static final HexFormat HEX = HexFormat.of();
    static final int CACHE_CAPACITY = 512;
    private static final Map<String, ItemStack> CACHE = AttributeCaches.bounded(CACHE_CAPACITY);
    private ExactItemBridge() {}
    static void clearCache() { CACHE.clear(); }

    public static String encode(ItemStack input) {
        var level = AttributeCaches.level();
        if (input.isEmpty() || level == null) return null;
        try {
            net.minecraft.nbt.Tag tag = input.copyWithCount(1).save(level.registryAccess());
            if (!(tag instanceof CompoundTag nbt)) return null;
            String value = "exact:" + HEX.formatHex(nbt.toString().getBytes(StandardCharsets.UTF_8));
            if (value.length() > 4096) return null;
            CACHE.put(value, input.copyWithCount(1));
            return value;
        } catch (Exception e) { return null; }
    }

    private static ItemStack read(String value) {
        try { return readChecked(value); }
        catch (Exception | LinkageError e) { return ItemStack.EMPTY; }
    }
    private static ItemStack readChecked(String value) throws Exception {
        var level = AttributeCaches.level();
        ItemStack cached = CACHE.get(value);
        if (cached != null && !cached.isEmpty()) return cached;
        if (!value.startsWith("exact:") || level == null)
            throw new IllegalStateException("精确物品编码或当前世界不可用");
        String snbt = new String(HEX.parseHex(value.substring(6)), StandardCharsets.UTF_8);
        if (snbt.length() > 2048) throw new IllegalArgumentException("精确物品数据过长");
        ItemStack parsed = ItemStack.parseOptional(level.registryAccess(),
            TagParser.parseTag(snbt));
        if (parsed.isEmpty()) throw new IllegalStateException("引用物品或组件缺失，或精确物品数据无法解码");
        CACHE.put(value, parsed);
        return parsed;
    }

    public static String registryId(String value) {
        ItemStack reference = read(value);
        return reference.isEmpty() ? null
            : net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(reference.getItem()).toString();
    }

    public static boolean matches(String value, ItemStack candidate) {
        return evaluate(value, candidate).matched();
    }
    public static AttributeResult availability(String value) {
        CACHE.remove(value);
        try { readChecked(value); return AttributeResult.match(true); }
        catch (Exception | LinkageError e) {
            CACHE.remove(value);
            return AttributeResult.unavailable("精确物品不可用：" + AttributeResult.failure(e));
        }
    }
    public static AttributeResult evaluate(String value, ItemStack candidate) {
        try {
            ItemStack reference=readChecked(value);
            return AttributeResult.match(!candidate.isEmpty() && ItemStack.isSameItemSameComponents(reference,candidate));
        } catch (Exception | LinkageError e) {
            CACHE.remove(value);
            return AttributeResult.unavailable("精确物品执行失败：" + AttributeResult.failure(e));
        }
    }

    public static String describe(String value) {
        ItemStack stored = read(value);
        return stored.isEmpty() ? "精确物品（附加数据）"
            : "精确物品 " + stored.getHoverName().getString();
    }
}
