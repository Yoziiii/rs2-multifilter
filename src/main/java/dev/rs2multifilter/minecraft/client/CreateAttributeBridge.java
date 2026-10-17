package dev.rs2multifilter.minecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional Create 6.0+ attribute integration, using Create's OWN discovery,
 * serialization and appliesTo predicates. No permanent linkage to Create:
 * a pack without Create can still load our mod and regular rules.
 */
public final class CreateAttributeBridge {
    private static final String ATTRIBUTE_CLASS =
        "com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute";
    private static final Map<String, Object> CACHE = new ConcurrentHashMap<>();
    private static final HexFormat HEX = HexFormat.of();
    private CreateAttributeBridge() {}

    public record Attribute(String label, String value) {}

    public static List<Attribute> getSampleAttributes(ItemStack sample) {
        Level level = Minecraft.getInstance().level;
        if (level == null || sample.isEmpty()) return List.of();
        List<Attribute> result = new ArrayList<>();
        try {
            Class<?> type = Class.forName(ATTRIBUTE_CLASS);
            Method discover = type.getMethod("getAllAttributes", ItemStack.class, Level.class);
            Method save = type.getMethod("saveStatic", type,
                net.minecraft.core.HolderLookup.Provider.class);
            Method format = type.getMethod("format", boolean.class);
            Object list = discover.invoke(null, sample, level);
            if (!(list instanceof List<?> attributes)) return List.of();
            for (Object attr : attributes) {
                try {
                    CompoundTag nbt = (CompoundTag) save.invoke(null, attr, level.registryAccess());
                    String encoded = "create:" + HEX.formatHex(nbt.toString().getBytes(StandardCharsets.UTF_8));
                    if (encoded.length() > 4096 || encoded.length() < 9) continue;
                    String title = ((Component) format.invoke(attr, false)).getString();
                    if (title.isBlank()) title = "Create 附属属性";
                    result.add(new Attribute(title, encoded));
                    CACHE.put(encoded, attr);
                } catch (ReflectiveOperationException ignored) {
                    // Some Create add-ons may register attributes with unsupported codecs.
                }
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Create is optional; no-Create installations retain base attributes.
        }
        return List.copyOf(result);
    }

    /** Human-readable translation of a persisted Create item attribute. */
    public static String describe(String encoded) {
        try {
            Object attribute = CACHE.get(encoded);
            if (attribute == null) {
                Level level = Minecraft.getInstance().level;
                if (level == null || !encoded.startsWith("create:")) return "Create 属性";
                Class<?> type = Class.forName(ATTRIBUTE_CLASS);
                Method load = type.getMethod("loadStatic", CompoundTag.class,
                    net.minecraft.core.HolderLookup.Provider.class);
                String nbt = new String(HEX.parseHex(encoded.substring(7)),
                    StandardCharsets.UTF_8);
                if (nbt.length() > 2048) return "Create 属性";
                attribute = load.invoke(null, TagParser.parseTag(nbt), level.registryAccess());
                if (attribute == null) return "Create 属性";
                CACHE.put(encoded, attribute);
            }
            Object component = attribute.getClass().getMethod("format", boolean.class)
                .invoke(attribute, false);
            return component instanceof Component text ? text.getString() : "Create 属性";
        } catch (Exception | LinkageError ignored) {
            return "Create 属性";
        }
    }

    public static boolean matches(String encoded, ItemStack stack) {
        return evaluate(encoded, stack).matched();
    }

    /** Reload persisted data at a card check/retry, so a stale discovered object cannot hide a missing type. */
    public static AttributeResult availability(String encoded) {
        CACHE.remove(encoded);
        try {
            load(encoded, Minecraft.getInstance().level);
            return AttributeResult.match(true);
        } catch (Exception | LinkageError e) {
            CACHE.remove(encoded);
            return AttributeResult.unavailable("Create 属性无法加载（模组或注册可能缺失）：" + AttributeResult.failure(e));
        }
    }

    private static Object load(String encoded, Level level) throws Exception {
        if (level == null) throw new IllegalStateException("当前世界不可用");
        if (!encoded.startsWith("create:")) throw new IllegalArgumentException("属性编码无效");
        Class<?> type = Class.forName(ATTRIBUTE_CLASS);
        Method load = type.getMethod("loadStatic", CompoundTag.class, net.minecraft.core.HolderLookup.Provider.class);
        String rawNbt = new String(HEX.parseHex(encoded.substring(7)), StandardCharsets.UTF_8);
        if (rawNbt.length() > 2048) throw new IllegalArgumentException("属性数据过长");
        Object attr = load.invoke(null, TagParser.parseTag(rawNbt), level.registryAccess());
        if (attr == null) throw new IllegalStateException("属性注册不存在，或原属性数据无法解码");
        if (CACHE.size() > 1024) CACHE.clear();
        CACHE.put(encoded, attr);
        return attr;
    }

    public static AttributeResult evaluate(String encoded, ItemStack stack) {
        Level level = Minecraft.getInstance().level;
        try {
            if (level == null) throw new IllegalStateException("当前世界不可用");
            Object attr = CACHE.get(encoded);
            if (attr == null) attr = load(encoded, level);
            if (stack.isEmpty()) return AttributeResult.match(false);
            // Invoke the public interface, including attributes implemented by non-public add-on classes.
            Object result = Class.forName(ATTRIBUTE_CLASS).getMethod("appliesTo", ItemStack.class, Level.class)
                .invoke(attr, stack, level);
            if (!(result instanceof Boolean matches)) throw new IllegalStateException("属性接口未返回布尔结果");
            return AttributeResult.match(matches);
        } catch (Exception | LinkageError e) {
            CACHE.remove(encoded);
            return AttributeResult.unavailable("Create 属性执行失败：" + AttributeResult.failure(e));
        }
    }
}
