package dev.rs2multifilter.minecraft.client;

import dev.rs2multifilter.core.*;
import dev.rs2multifilter.minecraft.menu.CardEditorLayout;
import dev.rs2multifilter.minecraft.menu.CardEditorMenu;
import net.minecraft.SharedConstants;
import net.minecraft.client.InputType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real screen/control event handlers, with no rendered client or OS clipboard. */
class CardEditorScreenInteractionTest {
    private CardEditorScreen screen;
    private FilterCard initial;
    private CardEditorMenu menu;

    @BeforeAll static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach void openScreen() throws Exception {
        var rules = IntStream.range(0, 9).mapToObj(i -> new FilterRule(FilterRule.Kind.TAG,
            "test:tag_" + i, i % 2 == 0)).toList();
        initial = new FilterCard(FilterCard.VERSION,
            List.of(new FilterCategory("原分类", "minecraft:paper", rules)));
        menu = mock(CardEditorMenu.class);
        when(menu.initialCard()).thenReturn(initial);
        var inventory = new Inventory(mock(Player.class));
        screen = new CardEditorScreen(menu, inventory, Component.literal("编辑"));
        var font = mock(Font.class);
        when(font.width(anyString())).thenAnswer(call -> call.<String>getArgument(0).length() * 6);
        when(font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(call -> {
            String text = call.getArgument(0);
            return text.substring(0, Math.min(text.length(), Math.max(0, call.<Integer>getArgument(1) / 6)));
        });
        Field fontField = Screen.class.getDeclaredField("font");
        fontField.setAccessible(true);
        fontField.set(screen, font);
        // Vanilla rebuildWidgets restores keyboard focus through the client.
        // Supply that context without starting a window or touching the clipboard.
        var client = mock(Minecraft.class);
        when(client.getLastInputType()).thenReturn(InputType.MOUSE);
        Field clientField = Screen.class.getDeclaredField("minecraft");
        clientField.setAccessible(true);
        clientField.set(screen, client);
        screen.width = 320;
        screen.height = 240;
        Class<?> optionType = Class.forName(CardEditorScreen.class.getName() + "$AttributeOption");
        var constructor = optionType.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        @SuppressWarnings("unchecked") var options = (List<Object>) field("attributes");
        options.add(constructor.newInstance(Component.literal("所属模组"), FilterRule.Kind.MOD, "minecraft"));
        options.add(constructor.newInstance(Component.literal("完整标签"), FilterRule.Kind.TAG, "create:handheld_in_deployer_use"));
        screen.init();
    }

    @Test void wheelDoesNotAddRulesOrDiscardAnUncommittedName() throws Exception {
        EditBox name = (EditBox) field("name");
        name.setValue("正在编辑的新名称");
        int x = (screen.width - CardEditorLayout.WIDTH) / 2 + CardEditorLayout.ATTRIBUTE.x() + 2;
        int y = (screen.height - CardEditorLayout.HEIGHT) / 2 + CardEditorLayout.ATTRIBUTE.y() + 2;
        assertTrue(screen.mouseScrolled(x, y, 0, -1));
        assertEquals(1, field("attributeIndex"));
        assertSame(name, field("name"), "Scrolling must update in place rather than rebuild widgets");
        assertEquals("正在编辑的新名称", name.getValue());
        assertEquals(initial, field("working"));
        verify(menu, never()).clicked(anyInt(), anyInt(), any(), any());
    }

    @Test void wheelOverInventoryOrButtonsCannotChangeAttributes() throws Exception {
        int left = (screen.width - CardEditorLayout.WIDTH) / 2;
        int top = (screen.height - CardEditorLayout.HEIGHT) / 2;
        assertFalse(screen.mouseScrolled(left + CardEditorLayout.INVENTORY_X,
            top + CardEditorLayout.INVENTORY_Y, 0, -1));
        assertFalse(screen.mouseScrolled(left + CardEditorLayout.INCLUDE.x() + 2,
            top + CardEditorLayout.INCLUDE.y() + 2, 0, -1));
        assertEquals(0, field("attributeIndex"));
        assertEquals(initial, field("working"));
    }

    @Test void popupWheelIsLocalAndDoesNotSelectAnAttributeOrEditRules() throws Exception {
        invoke("openRules");
        var area = (CardEditorLayout.Rect) field("ruleArea");
        assertTrue(screen.mouseScrolled(1, 1, 0, -1));
        assertEquals(0, field("ruleOffset"));
        for (int i = 0; i < 20; i++) screen.mouseScrolled(area.x() + 1, area.y() + 1, 0, -1);
        assertEquals(4, field("ruleOffset"));
        assertEquals(0, field("attributeIndex"));
        assertEquals(initial, field("working"));
    }

    @Test void rightClickingPopupDeleteAndThenReleasingCannotDeleteOrReachTheMenu() throws Exception {
        invoke("openRules");
        int x = (int) field("popupX") + 222, y = (int) field("popupY") + 28;
        assertTrue(screen.mouseClicked(x, y, 1));
        assertTrue(screen.mouseReleased(x, y, 1));
        assertEquals(initial, field("working"));
        verify(menu, never()).clicked(anyInt(), anyInt(), any(), any());
    }

    @Test void closingPopupConsumesBothClickAndReleaseWithoutChangingConfiguration() throws Exception {
        invoke("openRules");
        assertTrue(screen.mouseClicked(1, 1, 1));
        assertEquals(false, field("rulesOpen"));
        assertTrue(screen.mouseReleased(1, 1, 1));
        assertEquals(initial, field("working"));
        verify(menu, never()).clicked(anyInt(), anyInt(), any(), any());
    }

    @Test void rebuildingForViewportChangeKeepsTheDraftAndRealignsSelector() throws Exception {
        ((EditBox) field("name")).setValue("尚未保存的名字");
        screen.width = 640;
        screen.height = 360;
        screen.init();
        assertEquals("尚未保存的名字", ((EditBox) field("name")).getValue());
        var selector = (AttributeSelector) field("selector");
        int expectedX = (640 - CardEditorLayout.WIDTH) / 2 + CardEditorLayout.ATTRIBUTE.x();
        int expectedY = (360 - CardEditorLayout.HEIGHT) / 2 + CardEditorLayout.ATTRIBUTE.y();
        assertEquals(expectedX, selector.getX());
        assertEquals(expectedY, selector.getY());
        assertFalse(selector.mouseScrolled(68, 55, 0, -1));
        assertTrue(selector.mouseScrolled(expectedX + 2, expectedY + 2, 0, -1));
    }

    @Test void normalAndReverseAddButtonsKeepTheirRealRuleFlagsAndSelection() throws Exception {
        ((EditorButton) field("includeButton")).onPress();
        var card = (FilterCard) field("working");
        assertEquals(10, card.categories().getFirst().rules().size());
        assertEquals(new FilterRule(FilterRule.Kind.MOD, "minecraft", false),
            card.categories().getFirst().rules().getLast());
        assertEquals(0, field("attributeIndex"));
        assertFalse(((EditorButton) field("includeButton")).active);
        assertTrue(((EditorButton) field("excludeButton")).active);
        ((EditorButton) field("excludeButton")).onPress();
        card = (FilterCard) field("working");
        assertEquals(11, card.categories().getFirst().rules().size());
        assertEquals(new FilterRule(FilterRule.Kind.MOD, "minecraft", true),
            card.categories().getFirst().rules().getLast());
        verify(menu, never()).clicked(anyInt(), anyInt(), any(), any());
    }

    @Test void deletingAfterScrollingRemovesExactlyTheDisplayedRule() throws Exception {
        invoke("openRules");
        var area = (CardEditorLayout.Rect) field("ruleArea");
        screen.mouseScrolled(area.x() + 1, area.y() + 1, 0, -1);
        @SuppressWarnings("unchecked") var widgets = (List<EditorButton>) field("ruleWidgets");
        widgets.get(2).onPress();
        var rules = ((FilterCard) field("working")).categories().getFirst().rules();
        assertEquals(8, rules.size());
        assertFalse(rules.contains(initial.categories().getFirst().rules().get(1)));
        assertTrue(rules.contains(initial.categories().getFirst().rules().getFirst()));
        assertTrue(rules.contains(initial.categories().getFirst().rules().getLast()));
    }

    @Test void rulePopupFitsAllSupportedViewportSizes() throws Exception {
        for (int[] viewport : new int[][]{{320, 240}, {426, 240}, {480, 270}, {640, 360}, {960, 540}}) {
            screen.width = viewport[0];
            screen.height = viewport[1];
            screen.init();
            invoke("openRules");
            int x = (int) field("popupX"), y = (int) field("popupY");
            assertTrue(x >= 4 && y >= 4);
            assertTrue(x + 248 <= screen.width - 4);
            assertTrue(y + 142 <= screen.height - 4);
        }
    }

    @Test void anUnsupportedLongCandidateCannotCrashOpeningOrCorruptExistingRules() throws Exception {
        Class<?> type = Class.forName(CardEditorScreen.class.getName() + "$AttributeOption");
        var constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        @SuppressWarnings("unchecked") var options = (List<Object>) field("attributes");
        options.clear();
        options.add(constructor.newInstance(Component.literal("长标签"), FilterRule.Kind.TAG,
            "test:" + "a".repeat(260)));
        screen.init();
        ((EditorButton) field("includeButton")).onPress();
        assertEquals(initial, field("working"));
        assertEquals(true, field("isError"));
    }

    private Object field(String name) throws Exception {
        var field = CardEditorScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(screen);
    }

    private void invoke(String name) throws Exception {
        var method = CardEditorScreen.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(screen);
    }
}
