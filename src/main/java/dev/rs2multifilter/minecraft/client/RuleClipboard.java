package dev.rs2multifilter.minecraft.client;

import dev.rs2multifilter.core.FilterRule;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

/** Presentation only: rule values, codecs and matching predicates are untouched. */
public final class RuleClipboard {
    public record Details(String type, String meaning, List<String> readable, String decoded) {}
    private static final Map<String, String> TRAITS = Map.ofEntries(
        Map.entry("placeable", "可以放置为方块"), Map.entry("consumable", "具有食物组件"),
        Map.entry("fluid_container", "可以容纳流体"), Map.entry("enchanted", "已经附魔"),
        Map.entry("max_enchanted", "具有达到最高等级的附魔"), Map.entry("renamed", "具有自定义名称"),
        Map.entry("damaged", "已经损耗耐久"), Map.entry("badly_damaged", "耐久损耗超过四分之三"),
        Map.entry("not_stackable", "无法堆叠"), Map.entry("equipable", "可以穿戴"),
        Map.entry("furnace_fuel", "可以作为熔炉燃料"), Map.entry("washable", "可以洗涤"),
        Map.entry("hauntable", "可以缠魂"), Map.entry("crushable", "可以粉碎或磨粉"),
        Map.entry("smeltable", "可以熔炼"), Map.entry("smokable", "可以烟熏"),
        Map.entry("blastable", "可以在高炉中冶炼"), Map.entry("compostable", "可以堆肥"));

    public static Details details(FilterRule.Kind kind, String raw) {
        return switch (kind) {
            case MOD -> info("所属模组", "匹配来自指定模组的物品", "模组 ID：" + raw);
            case ITEM -> info("物品种类", "按物品注册 ID 匹配，忽略附魔、耐久和组件", "物品 ID：" + raw);
            case TAG -> info("物品标签", "匹配拥有指定物品标签的物品", "标签：#" + raw);
            case FLUID -> info("流体", "匹配指定流体", "流体 ID：" + raw);
            case FLUID_TAG -> info("流体标签", "匹配拥有指定流体标签的流体", "标签：#" + raw);
            case FOOD -> info("食物属性", "匹配具有食物组件的物品");
            case DAMAGEABLE -> info("耐久属性", "匹配具有耐久、可损坏的物品");
            case DAMAGED -> info("耐久损耗", "匹配已经损耗耐久的物品");
            case ENCHANTED -> info("附魔属性", "匹配已经附魔的物品");
            case EXACT_ITEM -> exact(raw);
            case CREATE_ATTRIBUTE -> create(raw);
        };
    }

    public static String describe(FilterRule.Kind kind, String displayName, String raw, Boolean exclude) {
        Details d = details(kind, raw);
        String direction = exclude == null ? "候选词条（未添加）" : exclude ? "排除" : "包含";
        StringBuilder text = new StringBuilder("显示名称：").append(displayName)
            .append("\n词条类型：").append(d.type()).append("\n规则方向：").append(direction)
            .append("\n匹配含义：").append(d.meaning());
        for (String line : d.readable()) text.append('\n').append(line);
        text.append("\n\n以下为完整底层数据，供进一步分析：");
        if (!d.decoded().isEmpty()) text.append("\n解析后的结构（SNBT）：").append(d.decoded());
        text.append("\n完整内部值：").append(raw);
        String readable = value(kind, raw);
        if (!readable.equals(raw)) text.append("\n可读值：").append(readable);
        return text.toString();
    }

    /** Ordinary UI never exposes encoded values, enum names or implementation classes. */
    public static List<String> tooltip(FilterRule.Kind kind, String label, String raw, Boolean exclude) {
        Details d = details(kind, raw);
        List<String> lines = new ArrayList<>();
        lines.add(exclude == null ? label : (exclude ? "排除：" : "包含：") + label);
        lines.add(d.meaning());
        for (String line : d.readable())
            if (!line.startsWith("属性注册 ID：")) lines.add(line);
        lines.add("右键复制");
        return List.copyOf(lines);
    }

    public static String value(FilterRule.Kind kind, String raw) {
        if (kind == FilterRule.Kind.TAG || kind == FilterRule.Kind.FLUID_TAG) return "#" + raw;
        if (kind == FilterRule.Kind.CREATE_ATTRIBUTE) {
            CompoundTag root = decode(raw, "create:");
            if (root != null) {
                CompoundTag attribute = root.getCompound("attribute");
                if ("create:in_tag".equals(attribute.getString("type")) && attribute.contains("value", Tag.TAG_STRING)) {
                    String tag = attribute.getString("value");
                    if (!tag.isEmpty()) return tag.startsWith("#") ? tag : "#" + tag;
                }
            }
        }
        return raw;
    }

    private static Details exact(String raw) {
        CompoundTag stack = decode(raw, "exact:");
        if (stack == null) return info("精确物品", "按完整物品组件匹配；编码未能解析，已保留原值");
        List<String> fields = new ArrayList<>();
        if (stack.contains("id", Tag.TAG_STRING)) fields.add("物品 ID：" + stack.getString("id"));
        return new Details("精确物品", "匹配相同物品及其组件，包括名称、附魔和耐久等", fields, stack.toString());
    }

    private static Details create(String raw) {
        CompoundTag root = decode(raw, "create:");
        if (root == null || !root.contains("attribute", Tag.TAG_COMPOUND))
            return info("Create 附属属性", "无法可靠解析此属性，请结合完整显示名称和原始数据分析");
        CompoundTag attr = root.getCompound("attribute");
        String type = attr.getString("type");
        List<String> fields = new ArrayList<>();
        String title = "Create 附属属性", meaning = "由 Create 或附属模组定义，详细条件以完整描述和数据为准";
        // Registry IDs and codec field types are verified against upstream, never guessed from labels.
        if (attr.contains("value", Tag.TAG_STRING)) {
            String v = attr.getString("value");
            switch (type) {
                case "create_connected:id_contains" -> {
                    title = "物品 ID 关键词";
                    meaning = "匹配物品 ID 中包含指定关键词的物品（区分大小写）";
                    fields.add("关键词：" + v);
                }
                case "create:in_tag" -> { title = "物品标签"; meaning = "匹配拥有指定物品标签的物品"; fields.add("标签：" + (v.startsWith("#") ? v : "#" + v)); }
                case "create:added_by" -> { title = "所属模组"; meaning = "匹配由指定模组创建的物品"; fields.add("模组 ID：" + v); }
                case "create:has_name" -> { title = "自定义名称"; meaning = "匹配具有指定自定义名称的物品"; fields.add("名称：" + v); }
                case "create:book_author" -> { title = "书籍作者"; meaning = "匹配指定作者的成书"; fields.add("作者：" + v); }
                case "create:has_enchant" -> { title = "指定附魔"; meaning = "匹配具有指定附魔的物品"; fields.add("附魔 ID：" + v); }
                case "create:has_fluid" -> { title = "容纳流体"; meaning = "匹配容纳指定流体的物品"; fields.add("流体 ID：" + v); }
                case "create:in_item_group" -> { title = "创造模式物品栏分类"; meaning = "匹配指定创造模式分类中的物品"; fields.add("分类 ID：" + v); }
                case "create:has_color" -> { title = "颜色属性"; meaning = "匹配具有指定染料颜色的物品"; fields.add("颜色：" + v); }
                case "create:shulker_fill_level" -> { title = "潜影盒装载情况"; meaning = "匹配指定装载状态的潜影盒"; fields.add("装载状态：" + v); }
                default -> { }
            }
        } else if (attr.contains("value", Tag.TAG_INT)) {
            int v = attr.getInt("value");
            switch (type) {
                case "create_connected:max_damage" -> { title = "最大耐久"; meaning = "匹配最大耐久等于指定数值的物品"; fields.add("最大耐久：" + v); }
                case "create_connected:stack_size" -> { title = "最大堆叠数量"; meaning = "匹配最大堆叠数量等于指定数值的物品"; fields.add("最大堆叠数量：" + v); }
                case "create:book_copy" -> { title = "书籍复制代数"; meaning = "匹配指定复制代数的成书"; fields.add("复制代数：" + v); }
                default -> { }
            }
        } else if (type.startsWith("create:") && !attr.contains("value")) {
            String trait = TRAITS.get(type.substring("create:".length()));
            if (trait != null) { title = "Create 物品特性"; meaning = trait; }
        }
        if (!type.isEmpty()) fields.add("属性注册 ID：" + type);
        return new Details(title, meaning, List.copyOf(fields), root.toString());
    }

    private static CompoundTag decode(String raw, String prefix) {
        if (!raw.startsWith(prefix)) return null;
        try {
            byte[] bytes = HexFormat.of().parseHex(raw.substring(prefix.length()));
            String text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
            return TagParser.parseTag(text);
        } catch (Exception e) { return null; }
    }

    private static Details info(String type, String meaning, String... readable) {
        return new Details(type, meaning, List.of(readable), "");
    }
    private RuleClipboard() {}
}
