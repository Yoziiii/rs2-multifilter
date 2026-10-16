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

    private static String encode(String snbt) {
        return "create:" + HexFormat.of().formatHex(snbt.getBytes(StandardCharsets.UTF_8));
    }
}
