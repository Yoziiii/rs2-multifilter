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
                    if (title.isBlank()) title = attr.getClass().getSimpleName();
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
        Level level = Minecraft.getInstance().level;
        if (level == null || stack.isEmpty() || !encoded.startsWith("create:")) return false;
        try {
            Object attr = CACHE.get(encoded);
            if (attr == null) {
                Class<?> type = Class.forName(ATTRIBUTE_CLASS);
                Method load = type.getMethod("loadStatic", CompoundTag.class,
                    net.minecraft.core.HolderLookup.Provider.class);
                String rawNbt = new String(HEX.parseHex(encoded.substring(7)), StandardCharsets.UTF_8);
                if (rawNbt.length() > 2048) return false;
                CompoundTag compound = TagParser.parseTag(rawNbt);
                attr = load.invoke(null, compound, level.registryAccess());
                if (attr == null) return false;
                if (CACHE.size() > 1024) CACHE.clear();
                CACHE.put(encoded, attr);
            }
            return Boolean.TRUE.equals(
                attr.getClass().getMethod("appliesTo", ItemStack.class, Level.class)
                    .invoke(attr, stack, level)
            );
        } catch (Exception | LinkageError e) {
            return false;
        }
    }
}
