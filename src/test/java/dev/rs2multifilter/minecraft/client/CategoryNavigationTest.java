package dev.rs2multifilter.minecraft.client;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class CategoryNavigationTest {
    private CategoryNavigation nav(List<String> names) { return new CategoryNavigation(names, s -> s.length() * 6, 185); }
    @Test void allIsAViewBeforeTheSingleCustomCategory() {
        var names = List.of("分类1");
        var nav = nav(names);
        assertEquals(List.of(-1, 0), nav.tabs(0).stream().map(CategoryNavigation.Tab::category).toList());
        assertFalse(nav.paginated());
        assertEquals(List.of("分类1"), names);
    }
    @Test void multipleCategoriesAppearTogetherAndArrowsAreAbsentWhenTheyFit() {
        var nav = nav(List.of("分类1", "分类2", "分类3"));
        assertFalse(nav.paginated());
        assertEquals(List.of(-1, 0, 1, 2), nav.tabs(0).stream().map(CategoryNavigation.Tab::category).toList());
    }
    @Test void overflowingPagesKeepAllFixedAndEveryOriginalIndexExactlyOnce() {
        var names = IntStream.range(0, 64).mapToObj(i -> "很长的原分类名称 " + i).toList();
        var nav = nav(names);
        assertTrue(nav.paginated());
        var actual = new java.util.ArrayList<Integer>();
        for (int page = 0; page < nav.count(); page++) {
            assertEquals(-1, nav.tabs(page).getFirst().category());
            assertTrue(nav.tabs(page).size() >= 2);
            for (var tab : nav.tabs(page)) if (tab.category() >= 0) actual.add(tab.category());
        }
        assertEquals(IntStream.range(0, 64).boxed().toList(), actual);
        assertEquals(0, nav.clamp(-1));
        assertEquals(nav.count() - 1, nav.clamp(999));
        assertEquals(64, names.size());
    }
    @Test void everyPageFitsItsActualAvailableWidthWithoutCoveringArrows() {
        for (int width : new int[]{112, 140, 175, 185}) {
            var nav = new CategoryNavigation(List.of("短", "很长的名字".repeat(5), "分类3", "分类4", "分类5"), String::length, width);
            for (int page = 0; page < nav.count(); page++) {
                int previousEnd = -1;
                for (var tab : nav.tabs(page)) {
                    assertTrue(tab.x() >= previousEnd);
                    assertTrue(tab.x() + tab.width() <= (nav.paginated() ? width - CategoryNavigation.ARROWS : width));
                    previousEnd = tab.x() + tab.width();
                }
            }
        }
    }
    @Test void aRememberedSelectionOpensItsPageAndDeletionDoesNotInsertFakeCategories() {
        var names = new java.util.ArrayList<>(IntStream.range(0, 12).mapToObj(i -> "原名字" + i).toList());
        var nav = nav(names);
        assertTrue(nav.tabs(nav.pageFor(10)).stream().anyMatch(t -> t.category() == 10));
        names.remove(1);
        var after = nav(names);
        assertEquals("原名字2", names.get(1));
        assertEquals(11, names.size());
        assertEquals(-1, after.tabs(0).getFirst().category());
    }
    @Test void noInstalledUpgradeStillHasOnlyTheAllView() {
        var nav = nav(List.of());
        assertEquals(1, nav.tabs(0).size());
        assertEquals(-1, nav.tabs(0).getFirst().category());
        assertFalse(nav.paginated());
    }
    @Test void smallGuiScalesReserveHeaderSpaceWithoutOverlappingTheNativeTitle() {
        for (int height : new int[]{240, 270, 360, 540}) {
            for (int bottom : new int[]{99, 156}) {
                int original = Math.max(3, Math.min(12, (height - 19 - bottom) / 18 - 3));
                int rows = GridTabs.fitRows(original, height, bottom);
                assertTrue(rows >= 1 && rows <= original);
                int top = (height - 19 - rows * 18 - bottom) / 2;
                assertTrue(top >= 23);
                assertTrue(Math.max(0, top - 23) + 24 <= top + 1);
            }
        }
        assertEquals(1, GridTabs.fitRows(3, 240, 156));
        assertEquals(2, GridTabs.fitRows(3, 270, 156));
    }
}
