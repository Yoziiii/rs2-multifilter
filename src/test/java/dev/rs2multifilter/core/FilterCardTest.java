package dev.rs2multifilter.core;

import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FilterCardTest {
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

    @Test void rejectsCorruptData() {
        assertThrows(IllegalArgumentException.class, () -> CardWireCodec.decode("v1:garbage"));
        assertThrows(IllegalArgumentException.class, () -> CardWireCodec.decode("nonsense"));
        assertThrows(IllegalArgumentException.class, () -> RuleText.parse("+tag:nocolon"));
        assertThrows(IllegalArgumentException.class, () -> new FilterCategory("", "minecraft:stone", List.of(new FilterRule(FilterRule.Kind.MOD, "create", false))));
    }
}
