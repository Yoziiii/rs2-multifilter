package dev.rs2multifilter.minecraft.client;

import dev.rs2multifilter.core.FilterRule;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/** Clipboard values are derived from rule data, never from translated/shortened labels. */
public final class RuleClipboard {
    private RuleClipboard() {}

    public static String describe(FilterRule.Kind kind, String displayName, String raw, Boolean exclude) {
        String direction = exclude == null ? "候选词条（未添加）" : exclude ? "排除" : "包含";
        String result = "词条类型：" + kind.name() + "\n显示名称：" + displayName
            + "\n规则方向：" + direction + "\n完整内部值：" + raw;
        String readable = value(kind, raw);
        return readable.equals(raw) ? result : result + "\n可读值：" + readable;
    }

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
