package dev.rs2multifilter.core;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ReverseRuleTest {
    private static final FilterRule INCLUDE = new FilterRule(FilterRule.Kind.ITEM, "minecraft:dirt", false);
    private static final FilterRule EXCLUDE = new FilterRule(FilterRule.Kind.ITEM, "minecraft:dirt", true);
    private static final FilterRule.ResourceView DIRT = new FilterRule.ResourceView("minecraft:dirt", Set.of());
    private static final FilterRule.ResourceView STONE = new FilterRule.ResourceView("minecraft:stone", Set.of());

    @Test void ordinaryAndReverseRulesHaveOppositeResults() {
        var ordinary = new FilterCategory("普通", "minecraft:dirt", List.of(INCLUDE));
        var reverse = new FilterCategory("反向", "minecraft:dirt", List.of(EXCLUDE));
        assertTrue(ordinary.matches(DIRT));
        assertFalse(ordinary.matches(STONE));
        assertFalse(reverse.matches(DIRT));
        assertTrue(reverse.matches(STONE));
    }

    @Test void exclusionsVetoMatchesInEveryExistingMode() {
        for (var mode : FilterCategory.Mode.values()) {
            var category = new FilterCategory("混合", "minecraft:dirt",
                List.of(new FilterRule(FilterRule.Kind.MOD, "minecraft", false), EXCLUDE), mode);
            assertFalse(category.matches(DIRT), mode.toString());
            assertEquals(mode != FilterCategory.Mode.DENY, category.matches(STONE), mode.toString());
        }
    }

    @Test void onlyExclusionsAndEmptyCategoriesKeepTheirExistingMeaning() {
        for (var mode : FilterCategory.Mode.values()) {
            var category = new FilterCategory("反向", "minecraft:dirt", List.of(EXCLUDE), mode);
            assertFalse(category.matches(DIRT));
            assertTrue(category.matches(STONE));
            assertFalse(new FilterCategory("空", "minecraft:paper", List.of(), mode).matches(STONE));
        }
    }

    @Test void savedAndCopiedCardsPreserveReverseFlagsAndFullValues() {
        var rules = List.of(INCLUDE, EXCLUDE, new FilterRule(FilterRule.Kind.TAG,
            "create:handheld_in_deployer_use", true));
        var original = FilterCard.blank().withCategory(new FilterCategory("复制", "minecraft:paper", rules));
        var restored = CardWireCodec.decode(CardWireCodec.encode(original));
        var copied = CardCopyRules.copyConfiguration(restored, FilterCard.blank());
        assertEquals(original, restored);
        assertEquals(original, copied);
        assertEquals(rules, copied.categories().getFirst().rules());
    }

    @Test void legacyV1CardsStillLoadTheirOriginalExclusionFlag() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(0x52534632);
            out.writeByte(1);
            out.writeShort(1);
            out.writeUTF("旧分类");
            out.writeUTF("minecraft:paper");
            out.writeShort(1);
            out.writeByte(FilterRule.Kind.ITEM.ordinal());
            out.writeBoolean(true);
            out.writeUTF("minecraft:dirt");
        }
        var decoded = CardWireCodec.decode("v1:" + Base64.getEncoder().encodeToString(bytes.toByteArray()));
        var category = decoded.categories().getFirst();
        assertEquals(FilterCategory.Mode.ANY, category.mode());
        assertEquals(EXCLUDE, category.rules().getFirst());
        assertFalse(category.matches(DIRT));
        assertTrue(category.matches(STONE));
    }
}
