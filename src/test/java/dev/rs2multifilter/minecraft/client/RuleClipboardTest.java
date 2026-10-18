package dev.rs2multifilter.minecraft.client;

import dev.rs2multifilter.core.FilterRule;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class RuleClipboardTest {
    private static final String TAG = "create:handheld_in_deployer_use";

    @Test void nativeItemAndFluidTagsCopyTheirFullNamesWithHash() {
        assertEquals("#" + TAG, RuleClipboard.value(FilterRule.Kind.TAG, TAG));
        assertEquals("#c:water", RuleClipboard.value(FilterRule.Kind.FLUID_TAG, "c:water"));
    }

    @Test void createTagCopiesTheFullInternalTagRatherThanItsTranslatedLabel() {
        String encoded = encode("{attribute:{type:\"create:in_tag\",value:\"" + TAG + "\"}}");
        assertEquals("#" + TAG, RuleClipboard.value(FilterRule.Kind.CREATE_ATTRIBUTE, encoded));
    }

    @Test void otherInternalValuesRemainCompleteAndUnmodified() {
        String opaque = "exact:" + "abcd".repeat(1000);
        assertEquals(opaque, RuleClipboard.value(FilterRule.Kind.EXACT_ITEM, opaque));
        assertEquals("minecraft:netherite_sword", RuleClipboard.value(FilterRule.Kind.ITEM, "minecraft:netherite_sword"));
        String addon = encode("{attribute:{type:\"addon:custom\",value:\"long internal value\"}}");
        assertEquals(addon, RuleClipboard.value(FilterRule.Kind.CREATE_ATTRIBUTE, addon));
    }

    @Test void malformedOrUnknownCreateDataIsCopiedVerbatim() {
        assertEquals("create:abcd", RuleClipboard.value(FilterRule.Kind.CREATE_ATTRIBUTE, "create:abcd"));
    }

    @Test void rightClickCopiesWithoutPressingTheEntryOrItsAction() {
        var presses = new AtomicInteger();
        var copied = new AtomicReference<String>();
        var button = new RuleEntryButton(10, 20, 140, 17,
            Component.literal("标签为 #create:handheld..."), b -> presses.incrementAndGet(), "#" + TAG, copied::set);
        assertTrue(button.mouseClicked(30, 25, 1));
        button.mouseReleased(30, 25, 1);
        assertEquals("#" + TAG, copied.get());
        assertEquals(0, presses.get(), "Right click must not add, delete, or select a rule");
        button.onPress();
        assertEquals(1, presses.get(), "The original left/keyboard action remains available");
    }

    @Test void copyDoesNotFireOutsideTheEntryOrOnHiddenDisabledEntries() {
        var copies = new AtomicInteger();
        var button = new RuleEntryButton(10, 20, 140, 17, Component.literal("词条"),
            b -> fail("Unexpected primary action"), "#" + TAG, value -> copies.incrementAndGet());
        assertFalse(button.mouseClicked(0, 0, 1));
        button.active = false;
        assertFalse(button.mouseClicked(30, 25, 1));
        button.active = true;
        button.visible = false;
        assertFalse(button.mouseClicked(30, 25, 1));
        assertEquals(0, copies.get());
    }

    @Test void candidateCopyIncludesTypeNameAndFullOriginalValue() {
        String copy = RuleClipboard.describe(FilterRule.Kind.TAG, "标签为 #" + TAG, TAG, null);
        assertTrue(copy.contains("词条类型：物品标签"));
        assertTrue(copy.contains("显示名称：标签为 #" + TAG));
        assertTrue(copy.contains("完整内部值：" + TAG));
        assertTrue(copy.contains("可读值：#" + TAG));
        assertTrue(copy.contains("候选词条（未添加）"));
        assertFalse(copy.contains("…"));
    }

    @Test void selectedCopyDistinguishesIncludeAndExcludeAndKeepsOpaqueData() {
        String raw = "exact:" + "abcdef".repeat(600);
        for (boolean exclude : new boolean[]{false, true}) {
            String copy = RuleClipboard.describe(FilterRule.Kind.EXACT_ITEM, "完整物品名称", raw, exclude);
            assertTrue(copy.contains("规则方向：" + (exclude ? "排除" : "包含")));
            assertTrue(copy.endsWith(raw));
        }
    }

    @Test void createCopyRetainsTheSerializedDataAlongWithTheReadableTag() {
        String raw = encode("{attribute:{type:\"create:in_tag\",value:\"" + TAG + "\"}}");
        String copy = RuleClipboard.describe(FilterRule.Kind.CREATE_ATTRIBUTE, "标签属性", raw, true);
        assertTrue(copy.contains("完整内部值：" + raw));
        assertTrue(copy.contains("可读值：#" + TAG));
        assertTrue(copy.contains("规则方向：排除"));
    }

    @Test void connectedKeywordsAreDecodedFromTheActualCodecForDifferentValues() {
        for (String keyword : new String[]{"millstone", "gearbox", "cogwheel"}) {
            String raw = encode("{attribute:{type:\"create_connected:id_contains\",value:\"" + keyword + "\"}}");
            String copy = RuleClipboard.describe(FilterRule.Kind.CREATE_ATTRIBUTE,
                "在 ID 中包含词语 \"" + keyword + "\"", raw, null);
            assertTrue(copy.contains("关键词：" + keyword));
            assertTrue(copy.contains("物品 ID 中包含指定关键词"));
            assertTrue(copy.indexOf("关键词：" + keyword) < copy.indexOf(raw));
            assertTrue(copy.contains("create_connected:id_contains"));
            assertTrue(copy.contains("完整内部值：" + raw));
        }
    }

    @Test void unknownTypesAreNotGuessedFromFieldNamesOrDisplayWords() {
        String raw = encode("{attribute:{type:\"other:id_contains\",value:\"millstone\",nested:{other:[1,2,3]}}}");
        String copy = RuleClipboard.describe(FilterRule.Kind.CREATE_ATTRIBUTE, "附属属性的完整描述", raw, true);
        assertFalse(copy.contains("关键词："));
        assertTrue(copy.contains("详细条件以完整描述和数据为准"));
        assertTrue(copy.contains("附属属性的完整描述"));
        assertTrue(copy.contains("nested"));
        assertTrue(copy.contains(raw));
        assertTrue(copy.contains("规则方向：排除"));
    }

    @Test void wrongCodecFieldTypesAreNotAssignedKnownSemantics() {
        String raw = encode("{attribute:{type:\"create_connected:id_contains\",value:17}}");
        String copy = RuleClipboard.describe(FilterRule.Kind.CREATE_ATTRIBUTE, "原显示内容", raw, false);
        assertFalse(copy.contains("关键词：17"));
        assertTrue(copy.contains(raw));
        assertTrue(copy.contains("规则方向：包含"));
    }

    @Test void exactItemCopyIncludesIdComponentsAndCompleteEncoding() {
        String snbt = "{id:\"minecraft:diamond_sword\",count:1,components:{\"minecraft:damage\":19,\"minecraft:custom_name\":\"名字\"}}";
        String raw = "exact:" + HexFormat.of().formatHex(snbt.getBytes(StandardCharsets.UTF_8));
        String copy = RuleClipboard.describe(FilterRule.Kind.EXACT_ITEM, "精确物品 名字", raw, false);
        assertTrue(copy.contains("物品 ID：minecraft:diamond_sword"));
        assertTrue(copy.contains("minecraft:damage"));
        assertTrue(copy.contains("19"));
        assertTrue(copy.contains("名字"));
        assertTrue(copy.endsWith(raw));
    }

    @Test void ordinaryTooltipsNeverShowEncodedDataOrInternalEnums() {
        String raw = encode("{attribute:{type:\"create_connected:id_contains\",value:\"millstone\"}}");
        String shown = String.join("\n", RuleClipboard.tooltip(FilterRule.Kind.CREATE_ATTRIBUTE, "在 ID 中包含 millstone", raw, null));
        assertTrue(shown.contains("millstone"));
        assertFalse(shown.contains(raw));
        assertFalse(shown.contains("CREATE_ATTRIBUTE"));
        assertFalse(shown.contains("{attribute:"));
        assertFalse(shown.contains("create_connected:id_contains"));
    }

    @Test void allSupportedRuleKindsHaveReadableCopyWithLosslessValues() {
        for (FilterRule.Kind kind : FilterRule.Kind.values()) {
            String raw = switch (kind) {
                case MOD -> "create";
                case FOOD, DAMAGEABLE, DAMAGED, ENCHANTED -> "true";
                case EXACT_ITEM -> "exact:" + "abcd".repeat(100);
                case CREATE_ATTRIBUTE -> encode("{attribute:{type:\"addon:unknown\",payload:{text:\"保持完整\"}}}");
                default -> "test:complete_long_value";
            };
            String copy = RuleClipboard.describe(kind, "完整显示名称", raw, false);
            assertTrue(copy.contains("显示名称：完整显示名称"));
            assertTrue(copy.contains("匹配含义："));
            assertTrue(copy.contains("完整内部值：" + raw));
            assertFalse(copy.contains("词条类型：" + kind.name()));
        }
    }

    @Test void knownTypedCreateFieldsRetainTheirMeaningAndValues() {
        for (String[] sample : new String[][]{
                {"create:has_name", "\"My Sword\"", "名称：My Sword"},
                {"create:added_by", "\"farmersdelight\"", "模组 ID：farmersdelight"},
                {"create:has_enchant", "\"minecraft:sharpness\"", "附魔 ID：minecraft:sharpness"},
                {"create:has_fluid", "\"minecraft:water\"", "流体 ID：minecraft:water"},
                {"create_connected:max_damage", "2031", "最大耐久：2031"},
                {"create_connected:stack_size", "64", "最大堆叠数量：64"},
                {"create:book_copy", "2", "复制代数：2"}}) {
            String raw = encode("{attribute:{type:\"" + sample[0] + "\",value:" + sample[1] + "}}");
            String copy = RuleClipboard.describe(FilterRule.Kind.CREATE_ATTRIBUTE, "完整属性名", raw, false);
            assertTrue(copy.contains(sample[2]), copy);
            assertTrue(copy.contains(raw));
        }
    }

    @Test void singletonAndMalformedDataRemainUsefulAndLossless() {
        String trait = encode("{attribute:{type:\"create:enchanted\"}}");
        assertTrue(RuleClipboard.describe(FilterRule.Kind.CREATE_ATTRIBUTE, "已附魔", trait, null).contains("匹配含义：已经附魔"));
        for (String bad : new String[]{"create:ff", "create:xyz", "create:abcd", encode("{attribute:\"not a compound\"}")}) {
            String copy = RuleClipboard.describe(FilterRule.Kind.CREATE_ATTRIBUTE, "完整原显示名称", bad, null);
            assertTrue(copy.contains("完整原显示名称"));
            assertTrue(copy.contains("无法可靠解析"));
            assertTrue(copy.endsWith(bad));
        }
    }

    private static String encode(String snbt) {
        return "create:" + HexFormat.of().formatHex(snbt.getBytes(StandardCharsets.UTF_8));
    }
}
