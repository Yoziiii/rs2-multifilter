package dev.rs2multifilter.core;

import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FilterCardTest {
    @Test void defaultCategoryIsEditorOnlyIdempotentAndPreservesExistingConfigurations() {
        var blank = FilterCard.blank();
        assertTrue(blank.categories().isEmpty());
        var draft = blank.forEditing();
        assertEquals(1, draft.categories().size());
        assertEquals("分类1", draft.categories().getFirst().name());
        assertEquals(draft, CardWireCodec.decode(CardWireCodec.encode(draft)));
        assertSame(draft, draft.forEditing());
        var custom = blank.withCategory(new FilterCategory("已有", "minecraft:stone", List.of()));
        assertSame(custom, custom.forEditing());
        assertEquals(draft, new FilterCard(FilterCard.VERSION, List.of()).forEditing());
    }
    private static FilterRule.ResourceView item(String id, String... tags) {
        return new FilterRule.ResourceView(id, Set.of(tags));
    }

    @Test void multipleModsTagsAndBlacklists() {
        FilterCategory category = new FilterCategory("机械与锭", "create:cogwheel",
            RuleText.parse("+mod:create\n+mod:mekanism\n+tag:c:ingots\n-item:create:shaft"));
        assertTrue(category.matches(item("create:cogwheel")));
        assertTrue(category.matches(item("mekanism:steel_casing")));
        assertTrue(category.matches(item("minecraft:iron_ingot", "c:ingots")));
        assertFalse(category.matches(item("create:shaft")));
        assertFalse(category.matches(item("minecraft:dirt")));
    }

    @Test void configurationRoundtripAndCopy() {
        FilterCard configured = FilterCard.blank().withCategory(
            new FilterCategory("建材", "minecraft:bricks", RuleText.parse("+mod:create\n-item:create:shaft")));
        String encoded = CardWireCodec.encode(configured);
        FilterCard restored = CardWireCodec.decode(encoded);
        assertEquals(configured, restored);
        assertEquals(configured, CardCopyRules.copyConfiguration(configured, FilterCard.blank()));
        assertEquals(configured, CardCopyRules.copyConfiguration(FilterCard.blank(), configured));
        assertNull(CardCopyRules.copyConfiguration(configured, configured));
        assertNull(CardCopyRules.copyConfiguration(FilterCard.blank(), FilterCard.blank()));
    }


    @Test void createStyleModesAndExcludedRules() {
        var viewCreate = item("create:cogwheel", "c:gears");
        var viewCataclysm = item("cataclysm:weapon");
        var viewOther = item("minecraft:dirt");
        var rules = RuleText.parse("+mod:create\n+mod:cataclysm");
        var any = new FilterCategory("any", "minecraft:paper", rules, FilterCategory.Mode.ANY);
        var all = new FilterCategory("all", "minecraft:paper", rules, FilterCategory.Mode.ALL);
        var deny = new FilterCategory("deny", "minecraft:paper", rules, FilterCategory.Mode.DENY);
        assertTrue(any.matches(viewCreate));
        assertTrue(any.matches(viewCataclysm));
        assertFalse(any.matches(viewOther));
        assertFalse(all.matches(viewCreate));
        assertFalse(all.matches(viewCataclysm));
        assertTrue(deny.matches(viewOther));
        assertFalse(deny.matches(viewCataclysm));
        var card = FilterCard.blank().withCategory(deny);
        assertEquals(card, CardWireCodec.decode(CardWireCodec.encode(card)));
        var veto = new FilterCategory("veto", "minecraft:paper",
            RuleText.parse("+mod:create\n-item:create:cogwheel"));
        assertFalse(veto.matches(viewCreate));
    }


    @Test void supportsExactCreateAndFluidSerializedConditions() {
        var modeRules = List.of(
            new FilterRule(FilterRule.Kind.FLUID, "minecraft:water", false),
            new FilterRule(FilterRule.Kind.FLUID_TAG, "c:water", false),
            new FilterRule(FilterRule.Kind.EXACT_ITEM, "exact:abcd", false),
            new FilterRule(FilterRule.Kind.CREATE_ATTRIBUTE, "create:abcd", false));
        var card = FilterCard.blank().withCategory(
            new FilterCategory("属性与流体", "minecraft:bucket", modeRules, FilterCategory.Mode.ANY));
        assertEquals(card, CardWireCodec.decode(CardWireCodec.encode(card)));
        assertThrows(IllegalArgumentException.class,
            () -> new FilterRule(FilterRule.Kind.EXACT_ITEM, "exact:abc", false));
    }

    @Test void physicalCardCategoryLimitIs64() {
        FilterCard card = FilterCard.blank();
        for (int i = 0; i < 64; i++)
            card = card.withCategory(new FilterCategory("分类" + i,
                "minecraft:paper", List.of()));
        assertEquals(64, card.categories().size());
        FilterCard filled = card;
        assertThrows(IllegalArgumentException.class, () ->
            filled.withCategory(new FilterCategory("extra", "minecraft:paper", List.of())));
    }

    @Test void fuzzyItemMatchesSameIdAndExactKindIsSeparatelyPreserved() {
        FilterRule fuzzy = new FilterRule(FilterRule.Kind.ITEM, "minecraft:netherite_sword", false);
        assertTrue(fuzzy.matches(item("minecraft:netherite_sword")));
        assertFalse(fuzzy.matches(item("minecraft:diamond_sword")));
        FilterRule exact = new FilterRule(FilterRule.Kind.EXACT_ITEM, "exact:abcd", false);
        assertFalse(exact.matches(item("minecraft:netherite_sword")));
        assertEquals(FilterRule.Kind.EXACT_ITEM, exact.kind());
    }

    @Test void rejectsCorruptData() {
        assertThrows(IllegalArgumentException.class, () -> CardWireCodec.decode("v1:garbage"));
        assertThrows(IllegalArgumentException.class, () -> CardWireCodec.decode("nonsense"));
        assertThrows(IllegalArgumentException.class, () -> RuleText.parse("+tag:nocolon"));
        assertThrows(IllegalArgumentException.class, () -> new FilterCategory("", "minecraft:stone", List.of(new FilterRule(FilterRule.Kind.MOD, "create", false))));
    }
}
