package dev.rs2multifilter.minecraft.client;

import dev.rs2multifilter.core.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GridPreferencesTest {
    @TempDir Path directory;
    private FilterCard card() {
        return FilterCard.blank().withCategory(new FilterCategory("机械动力", "minecraft:paper", List.of()))
            .withCategory(new FilterCategory("建材", "minecraft:stone", List.of()));
    }
    private GridPreferences store() { return new GridPreferences(directory.resolve("client.properties")); }

    @Test void preferenceAndLastCategorySurviveReloadWithoutChangingTheCard() {
        var preferences = store();
        var card = card();
        String before = CardWireCodec.encode(card);
        preferences.record("world-a", card, 1);
        assertEquals(1, store().restore("world-a", card));
        assertTrue(store().remembersCategory());
        assertEquals(before, CardWireCodec.encode(card));
    }
    @Test void disabledMemoryReopensWithAllAndDoesNotResurrectAnOldChoice() {
        var preferences = store();
        preferences.record("world-a", card(), 1);
        preferences.setRemember(false);
        preferences.record("world-a", card(), 0);
        var reopened = store();
        assertFalse(reopened.remembersCategory());
        assertEquals(-1, reopened.restore("world-a", card()));
        reopened.setRemember(true);
        assertEquals(-1, reopened.restore("world-a", card()));
    }
    @Test void otherWorldsChangedRulesAndReorderedCategoriesNeverRestoreTheWrongIndex() {
        var preferences = store();
        var card = card();
        preferences.record("world-a", card, 1);
        assertEquals(-1, preferences.restore("world-b", card));
        var reordered = new FilterCard(FilterCard.VERSION, card.categories().reversed());
        assertEquals(-1, preferences.restore("world-a", reordered));
        var changed = card.withCategory(new FilterCategory("新增", "minecraft:paper", List.of()));
        assertEquals(-1, preferences.restore("world-a", changed));
    }
    @Test void allItemsAndInvalidIndicesAreHandledSafely() {
        var preferences = store();
        preferences.record("world", card(), 0);
        preferences.record("world", card(), 99);
        assertEquals(0, store().restore("world", card()));
        preferences.record("world", card(), -1);
        assertEquals(-1, store().restore("world", card()));
        assertEquals(-1, preferences.restore("world", FilterCard.blank()));
    }
    @Test void corruptOrOversizedPreferenceFilesCannotPreventOpeningTheTerminal() throws Exception {
        Path file = directory.resolve("client.properties");
        Files.writeString(file, "remember=tru\\uNOTHEX");
        assertDoesNotThrow(this::store);
        Files.writeString(file, "x".repeat(33_000));
        assertDoesNotThrow(this::store);
    }
    @Test void historyIsBounded() throws Exception {
        var preferences = store();
        for (int i = 0; i < 70; i++) preferences.record("world-" + i, card(), 0);
        assertTrue(Files.size(directory.resolve("client.properties")) < 10_000);
        assertEquals(-1, preferences.restore("world-0", card()));
        assertEquals(0, store().restore("world-69", card()));
    }
    @Test void remembersCategoriesAcrossAllFourCardsWithoutUsingTheSingleCardCodec() {
        var categories = java.util.stream.IntStream.range(0, 256).mapToObj(i ->
            new FilterCategory("分类 " + i, "minecraft:paper", List.<FilterRule>of())).toList();
        var merged = new FilterCard(FilterCard.VERSION, categories);
        var preferences = store();
        preferences.record("world", merged, 255);
        assertEquals(255, store().restore("world", merged));
    }
    @Test void restoresOnlyAfterTheFullFourSlotSyncAndNeverFromAnIntermediateCard() {
        var preferences = store();
        preferences.record("world", card(), 1);
        var state = new GridCategoryState(preferences, "world");
        state.synchronize(FilterCard.blank(), false);
        state.synchronize(FilterCard.blank().withCategory(card().categories().getFirst()), false);
        assertEquals(-1, state.selected());
        assertTrue(state.card().categories().isEmpty());
        state.synchronize(card(), true);
        assertEquals(1, state.selected());
        state.synchronize(card(), true);
        assertEquals(1, state.selected());
    }
    @Test void replacingOrRemovingUpgradesResetsSelectionSafely() {
        var preferences = store();
        var state = new GridCategoryState(preferences, "world");
        state.synchronize(card(), true);
        state.choose(1);
        state.synchronize(FilterCard.blank(), true);
        assertEquals(-1, state.selected());
        state.synchronize(card(), true);
        assertEquals(-1, state.selected());
    }
    @Test void cyclingWrapsThroughAllAndEveryCategoryWithoutWritingRules() {
        var state = new GridCategoryState(store(), "world");
        state.synchronize(card(), true);
        state.cycle(1); assertEquals(0, state.selected());
        state.cycle(1); assertEquals(1, state.selected());
        state.cycle(1); assertEquals(-1, state.selected());
        state.cycle(-1); assertEquals(1, state.selected());
        assertEquals(card(), state.card());
        state.toggleRemember();
        assertFalse(state.remembersCategory());
        var reopened = new GridCategoryState(store(), "world");
        reopened.synchronize(card(), true);
        assertEquals(-1, reopened.selected());
    }
}
