package dev.rs2multifilter.minecraft.client;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class AttributeSelectorTest {
    private static final List<AttributeSelector.Entry> OPTIONS = List.of(
        entry("所属模组：Create", "mod:create"),
        entry("标签为 #create:handheld…", "#create:handheld_in_deployer_use"),
        entry("物品：小齿轮", "create:cogwheel"));

    private static AttributeSelector.Entry entry(String text, String raw) {
        return new AttributeSelector.Entry(Component.literal(text), raw, Component.literal(text));
    }

    @Test void scrollChangesOnlyTheCandidateAndOnlyInsideItsBounds() {
        var changes = new AtomicInteger();
        var selector = new AttributeSelector(42, 47, 172, 20,
            v -> fail("Scroll must not copy or add"), index -> changes.incrementAndGet());
        selector.setOptions(OPTIONS, 0);
        assertFalse(selector.mouseScrolled(41, 50, 0, -1));
        assertFalse(selector.mouseScrolled(214, 50, 0, -1));
        assertFalse(selector.mouseScrolled(50, 67, 0, -1));
        assertFalse(selector.mouseScrolled(55, 138, 0, -1), "Inventory is outside the selector");
        assertFalse(selector.mouseScrolled(225, 50, 0, -1), "Add button is outside the selector");
        assertTrue(selector.mouseScrolled(50, 50, 0, -1));
        assertEquals(1, selector.selectedIndex());
        assertEquals(1, changes.get());
    }

    @Test void endsAreClampedAndStillConsumeScrollWithoutChangingAnyAction() {
        var changes = new AtomicInteger();
        var selector = new AttributeSelector(42, 47, 172, 20, v -> fail(), i -> changes.incrementAndGet());
        selector.setOptions(OPTIONS, 0);
        assertTrue(selector.mouseScrolled(50, 50, 0, 1));
        assertEquals(0, changes.get());
        for (int i = 0; i < 10; i++) selector.mouseScrolled(50, 50, 0, -1);
        assertEquals(2, selector.selectedIndex());
        assertEquals(2, changes.get());
        assertFalse(selector.mouseScrolled(50, 50, 1, 0));
        assertTrue(selector.mouseScrolled(50, 50, 0, .25));
        assertEquals(1, selector.selectedIndex());
    }

    @Test void rightClickCopiesTheNewSelectionFullValueWithoutSelectionCallback() {
        var copied = new AtomicReference<String>();
        var changes = new AtomicInteger();
        var selector = new AttributeSelector(42, 47, 172, 20, copied::set, i -> changes.incrementAndGet());
        selector.setOptions(OPTIONS, 1);
        selector.mouseClicked(50, 50, 1);
        selector.mouseReleased(50, 50, 1);
        assertEquals("#create:handheld_in_deployer_use", copied.get());
        assertEquals(1, selector.selectedIndex());
        assertEquals(0, changes.get());
        selector.mouseScrolled(50, 50, 0, -1);
        selector.mouseClicked(50, 50, 1);
        assertEquals("create:cogwheel", copied.get(), "Copy must not retain an old option's data");
    }

    @Test void emptyHiddenAndDisabledSelectorsCannotScrollOrCopy() {
        var selector = new AttributeSelector(42, 47, 172, 20, v -> fail(), i -> fail());
        selector.setOptions(List.of(), 100);
        assertFalse(selector.mouseScrolled(50, 50, 0, -1));
        assertFalse(selector.mouseClicked(50, 50, 1));
        selector.setOptions(OPTIONS, 0);
        selector.visible = false;
        assertFalse(selector.mouseScrolled(50, 50, 0, -1));
        selector.visible = true;
        selector.active = false;
        assertFalse(selector.mouseScrolled(50, 50, 0, -1));
    }

    @Test void replacingSamplesResetsOrClampsSelectionSafely() {
        var selector = new AttributeSelector(42, 47, 172, 20, v -> {}, i -> {});
        selector.setOptions(OPTIONS, 99);
        assertEquals(2, selector.selectedIndex());
        selector.setOptions(List.of(OPTIONS.getFirst()), 0);
        assertEquals(0, selector.selectedIndex());
        assertEquals(1, selector.optionCount());
        selector.setOptions(List.of(), 0);
        assertEquals(0, selector.optionCount());
    }

    @Test void hoverContainsMultipleCompleteCandidatesAndExactlyOneSelectedMarker() {
        var selector = new AttributeSelector(42, 47, 172, 20, v -> {}, i -> {});
        selector.setOptions(OPTIONS, 1);
        var lines = selector.hoverLines(Component.literal("下界合金剑"), 16);
        assertEquals(3, lines.stream().filter(c -> c.getString().startsWith("> ") || c.getString().startsWith("-> ")).count());
        assertEquals(1, lines.stream().filter(c -> c.getString().startsWith("-> ")).count());
        assertTrue(lines.get(2).getString().startsWith("-> "));
        assertTrue(lines.getFirst().getString().contains("2/3"));
        assertTrue(lines.getLast().getString().contains("滚动选择"));
        assertNull(selector.getTooltip(), "No encoded, single-option vanilla tooltip may remain");
    }

    @Test void hoverWindowFollowsSelectionAndClampsAtBothEnds() {
        var options = java.util.stream.IntStream.range(0, 60).mapToObj(i -> entry("完整属性 " + i, "value:" + i)).toList();
        var selector = new AttributeSelector(42, 47, 172, 20, v -> {}, i -> {});
        selector.setOptions(options, 30);
        var lines = selector.hoverLines(Component.literal("参考样本"), 16);
        assertEquals(16, lines.stream().filter(c -> c.getString().matches("(?:->|>) 完整属性 .*" )).count());
        assertTrue(lines.stream().anyMatch(c -> c.getString().equals("-> 完整属性 30")));
        selector.setOptions(options, 59);
        assertTrue(selector.hoverLines(Component.literal("样本"), 16).stream().anyMatch(c -> c.getString().equals("-> 完整属性 59")));
        selector.setOptions(options, 0);
        assertFalse(selector.hoverLines(Component.literal("样本"), 16).get(1).getString().equals("> …"));
    }

    @Test void hoverKeepsFullLongLabelsAndDisappearsWhenDisabledOrHidden() {
        String label = "标签为 #create:" + "complete_path_".repeat(15);
        var selector = new AttributeSelector(42, 47, 172, 20, v -> {}, i -> {});
        selector.setOptions(List.of(entry(label, "opaque:123")), 0);
        assertEquals("-> " + label, selector.hoverLines(Component.literal("样本"), 16).get(1).getString());
        selector.visible = false;
        assertTrue(selector.hoverLines(Component.literal("样本"), 16).isEmpty());
        selector.visible = true;
        selector.active = false;
        assertTrue(selector.hoverLines(Component.literal("样本"), 16).isEmpty());
    }
}
