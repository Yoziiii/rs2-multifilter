package dev.rs2multifilter.minecraft.client;

import dev.rs2multifilter.core.FilterRule;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/** Clipboard values are derived from rule data, never from translated/shortened labels. */
public final class RuleClipboard {
    private RuleClipboard() {}

    public static String value(FilterRule.Kind kind, String value) {
        if (kind == FilterRule.Kind.TAG || kind == FilterRule.Kind.FLUID_TAG) return "#" + value;
        if (kind == FilterRule.Kind.CREATE_ATTRIBUTE && value.startsWith("create:")) {
            try {
                String snbt = new String(HexFormat.of().parseHex(value.substring(7)), StandardCharsets.UTF_8);
                CompoundTag attribute = TagParser.parseTag(snbt).getCompound("attribute");
                // Create 1.21.1 uses a dispatched codec with type + value. Preserve
                // opaque/add-on attributes verbatim if no known value can be read.
                if ("create:in_tag".equals(attribute.getString("type"))) {
                    String tag = attribute.getString("value");
                    if (!tag.isEmpty()) return tag.startsWith("#") ? tag : "#" + tag;
                }
            } catch (Exception ignored) { }
        }
        return value;
    }
}
