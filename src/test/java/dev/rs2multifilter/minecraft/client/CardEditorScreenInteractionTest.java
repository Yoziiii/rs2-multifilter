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
    private net.minecraft.client.KeyboardHandler keyboard;
    private Font font;

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
        font = mock(Font.class);
        when(font.width(anyString())).thenAnswer(call -> call.<String>getArgument(0).length() * 6);
        when(font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(call -> {
            String text = call.getArgument(0);
            return text.substring(0, Math.min(text.length(), Math.max(0, call.<Integer>getArgument(1) / 6)));
        });
        when(font.plainSubstrByWidth(anyString(), anyInt(), anyBoolean())).thenAnswer(call -> {
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
        keyboard = mock(net.minecraft.client.KeyboardHandler.class);
        var keyboardField = Minecraft.class.getDeclaredField("keyboardHandler");
        keyboardField.setAccessible(true);
        keyboardField.set(client, keyboard);
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

    @Test void emptyCardGetsExactlyOneEditableDefaultWithoutSilentlySaving() throws Exception {
        when(menu.initialCard()).thenReturn(FilterCard.blank());
        var opened = new CardEditorScreen(menu, new Inventory(mock(Player.class)), Component.literal("过滤升级"));
        var working = CardEditorScreen.class.getDeclaredField("working");
        working.setAccessible(true);
        FilterCard draft = (FilterCard) working.get(opened);
        assertEquals(1, draft.categories().size());
        assertEquals("默认分类", draft.categories().getFirst().name());
        assertEquals(draft, draft.forEditing().forEditing());
        assertTrue(menu.initialCard().categories().isEmpty(), "Opening does not silently save the draft");
    }

    @Test void titleRenderingUsesTheNewNameWithoutAShadow() {
        var g = mock(net.minecraft.client.gui.GuiGraphics.class);
        when(g.pose()).thenReturn(new com.mojang.blaze3d.vertex.PoseStack());
        var inventory = new net.minecraft.world.SimpleContainer(36);
        for (int i = 0; i < 36; i++) when(menu.getSlot(i)).thenReturn(
            new net.minecraft.world.inventory.Slot(inventory, i, 15 + i % 9 * 18, 144 + i / 9 * 18));
        screen.renderBg(g, 0, 0, 0);
        verify(g).drawString(eq(font), eq("过滤升级"), anyInt(), anyInt(), anyInt(), eq(false));
        verify(g, never()).drawCenteredString(any(Font.class), anyString(), anyInt(), anyInt(), anyInt());
    }

    @Test void toolbarUsesPixelIconsWithAccessibleNamesAndUnchangedActions() {
        var toolbar = screen.children().stream().filter(EditorButton.class::isInstance)
            .map(EditorButton.class::cast).filter(b -> !(b instanceof AttributeSelector)).toList();
        assertFalse(toolbar.isEmpty());
        assertTrue(toolbar.stream().allMatch(b -> b.icon() != null));
        assertTrue(toolbar.stream().allMatch(b -> !b.getMessage().getString().isBlank()));
    }

    @Test void deletingTheLastCategorySurvivesResizeAndReopeningCreatesOnlyOneDefault() throws Exception {
        invoke("deleteCategory");
        assertTrue(((FilterCard) field("working")).categories().isEmpty());
        screen.init();
        assertTrue(((FilterCard) field("working")).categories().isEmpty());
        var savedEmpty = CardWireCodec.decode(CardWireCodec.encode((FilterCard) field("working")));
        when(menu.initialCard()).thenReturn(savedEmpty);
        var reopened = new CardEditorScreen(menu, new Inventory(mock(Player.class)), Component.literal("过滤升级"));
        var working = CardEditorScreen.class.getDeclaredField("working");
        working.setAccessible(true);
        assertEquals(1, ((FilterCard) working.get(reopened)).categories().size());
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

    @Test void inlineRuleWheelIsLocalAndDoesNotSelectAnAttributeOrEditRules() throws Exception {
        var area = (CardEditorLayout.Rect) field("ruleArea");
        assertFalse(screen.mouseScrolled(1, 1, 0, -1));
        assertEquals(0, field("ruleOffset"));
        for (int i = 0; i < 20; i++) screen.mouseScrolled(area.x() + 1, area.y() + 1, 0, -1);
        assertEquals(4, field("ruleOffset"));
        assertEquals(0, field("attributeIndex"));
        assertEquals(initial, field("working"));
    }

    @Test void rightClickingInlineDeleteAndThenReleasingCannotDeleteOrReachTheMenu() throws Exception {
        var area = (CardEditorLayout.Rect) field("ruleArea");
        int x = area.x() + area.width() - 12, y = area.y() + 2;
        assertTrue(screen.mouseClicked(x, y, 1));
        assertTrue(screen.mouseReleased(x, y, 1));
        assertEquals(initial, field("working"));
        verify(menu, never()).clicked(anyInt(), anyInt(), any(), any());
    }

    @Test void inlineRuleManagementNeverCoversTheSelectorOrRealInventory() throws Exception {
        var area = (CardEditorLayout.Rect) field("ruleArea");
        int left = (screen.width - CardEditorLayout.WIDTH) / 2;
        int top = (screen.height - CardEditorLayout.HEIGHT) / 2;
        assertFalse(area.contains(left + CardEditorLayout.REFERENCE_X, top + CardEditorLayout.REFERENCE_Y));
        assertFalse(area.contains(left + CardEditorLayout.INVENTORY_X, top + CardEditorLayout.INVENTORY_Y));
        assertFalse(area.contains(left + CardEditorLayout.ATTRIBUTE.x(), top + CardEditorLayout.ATTRIBUTE.y()));
        assertFalse(screen.mouseScrolled(left + CardEditorLayout.INVENTORY_X, top + CardEditorLayout.INVENTORY_Y, 0, -1));
        assertEquals(initial, field("working"));
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
        var area = (CardEditorLayout.Rect) field("ruleArea");
        screen.mouseScrolled(area.x() + 1, area.y() + 1, 0, -1);
        @SuppressWarnings("unchecked") var widgets = (List<EditorButton>) field("ruleWidgets");
        widgets.get(1).onPress();
        var rules = ((FilterCard) field("working")).categories().getFirst().rules();
        assertEquals(8, rules.size());
        assertFalse(rules.contains(initial.categories().getFirst().rules().get(1)));
        assertTrue(rules.contains(initial.categories().getFirst().rules().getFirst()));
        assertTrue(rules.contains(initial.categories().getFirst().rules().getLast()));
    }

    @Test void inlineRulesFitAllSupportedViewportSizes() throws Exception {
        for (int[] viewport : new int[][]{{320, 240}, {426, 240}, {480, 270}, {640, 360}, {960, 540}}) {
            screen.width = viewport[0];
            screen.height = viewport[1];
            screen.init();
            var area = (CardEditorLayout.Rect) field("ruleArea");
            assertTrue(area.x() >= 0 && area.y() >= 0);
            assertTrue(area.x() + area.width() <= screen.width);
            assertTrue(area.y() + area.height() <= screen.height);
        }
    }

    @Test void hoveringSelectorExposesMultipleCandidatesAndLeavingClosesThem() throws Exception {
        var selector = (AttributeSelector) field("selector");
        var shown = screen.candidateTooltipAt(selector.getX() + 1, selector.getY() + 1, 16);
        assertEquals(2, shown.stream().filter(c -> c.getString().startsWith("> ") || c.getString().startsWith("-> ")).count());
        assertTrue(screen.candidateTooltipAt(0, 0, 16).isEmpty());
        assertTrue(screen.candidateTooltipAt(selector.getX() + selector.getWidth(), selector.getY(), 16).isEmpty());
        assertEquals(initial, field("working"));
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

    @Test void categoryCreationSwitchDeletionAndDraftCommitKeepExistingRules() throws Exception {
        ((EditBox) field("name")).setValue("第一类的新名字");
        invoke("addCategory");
        var card = (FilterCard) field("working");
        assertEquals(2, card.categories().size());
        assertEquals("第一类的新名字", card.categories().getFirst().name());
        assertEquals(initial.categories().getFirst().rules(), card.categories().getFirst().rules());
        var move = CardEditorScreen.class.getDeclaredMethod("moveCategory", int.class);
        move.setAccessible(true);
        move.invoke(screen, -1);
        assertEquals(0, field("category"));
        invoke("deleteCategory");
        assertEquals(1, ((FilterCard) field("working")).categories().size());
    }

    @Test void modeButtonsPreserveTheOrdinaryAndExcludeRules() throws Exception {
        for (var pair : java.util.Map.of(CardEditorLayout.ANY, FilterCategory.Mode.ANY,
                CardEditorLayout.ALL, FilterCategory.Mode.ALL, CardEditorLayout.DENY, FilterCategory.Mode.DENY).entrySet()) {
            button(pair.getKey()).onPress();
            var category = ((FilterCard) field("working")).categories().getFirst();
            assertEquals(pair.getValue(), category.mode());
            assertEquals(initial.categories().getFirst().rules(), category.rules());
        }
    }

    @Test void iconAndFuzzyControlsRemainConnectedWithoutChangingUnrelatedRules() throws Exception {
        var sample = CardEditorScreen.class.getDeclaredField("sample");
        sample.setAccessible(true);
        sample.set(screen, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE));
        screen.init();
        assertTrue(button(CardEditorLayout.ICON).active);
        button(CardEditorLayout.ICON).onPress();
        assertEquals("minecraft:stone", ((FilterCard) field("working")).categories().getFirst().iconItemId());
        sample.set(screen, net.minecraft.world.item.ItemStack.EMPTY);
        screen.init();
        button(CardEditorLayout.FUZZY).onPress();
        assertEquals(false, field("fuzzy"));
        assertEquals(initial.categories().getFirst().rules(), ((FilterCard) field("working")).categories().getFirst().rules());
    }

    @Test void saveEmitsTheCompleteConfigurationWithThePendingName() throws Exception {
        ((EditBox) field("name")).setValue("准备保存的分类");
        var payload = new java.util.concurrent.atomic.AtomicReference<dev.rs2multifilter.minecraft.network.SaveCardPayload>();
        try (var packets = mockStatic(net.neoforged.neoforge.network.PacketDistributor.class)) {
            packets.when(() -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                any(dev.rs2multifilter.minecraft.network.SaveCardPayload.class)))
                .thenAnswer(call -> { payload.set(call.getArgument(0)); return null; });
            button(CardEditorLayout.SAVE).onPress();
        }
        assertNotNull(payload.get());
        var restored = CardWireCodec.decode(payload.get().data());
        assertEquals("准备保存的分类", restored.categories().getFirst().name());
        assertEquals(initial.categories().getFirst().rules(), restored.categories().getFirst().rules());
        assertEquals(field("working"), restored);
    }

    private EditorButton button(CardEditorLayout.Rect r) {
        int x = (screen.width - CardEditorLayout.WIDTH) / 2 + r.x();
        int y = (screen.height - CardEditorLayout.HEIGHT) / 2 + r.y();
        return screen.children().stream().filter(c -> c instanceof EditorButton)
            .map(EditorButton.class::cast).filter(b -> b.getX() == x && b.getY() == y).reduce((a, b) -> b).orElseThrow();
    }

    @Test void candidateRightClickUsesTheRealClipboardCallbackAndReportsSuccess() throws Exception {
        var selector = (AttributeSelector) field("selector");
        selector.mouseScrolled(selector.getX() + 1, selector.getY() + 1, 0, -1);
        assertTrue(screen.mouseClicked(selector.getX() + 1, selector.getY() + 1, 1));
        assertTrue(screen.mouseReleased(selector.getX() + 1, selector.getY() + 1, 1));
        var copied = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(keyboard).setClipboard(copied.capture());
        assertTrue(copied.getValue().contains("#create:handheld_in_deployer_use"));
        assertTrue(copied.getValue().contains("词条类型：物品标签"));
        assertEquals("已复制", field("notice"));
        assertEquals(initial, field("working"));
        assertEquals(1, field("attributeIndex"));
        verify(menu, never()).clicked(anyInt(), anyInt(), any(), any());
    }

    @Test void inlineRuleRightClickCopiesDirectionAndFullValueWithoutDeleting() throws Exception {
        var area = (CardEditorLayout.Rect) field("ruleArea");
        screen.mouseClicked(area.x() + 3, area.y() + 3, 1);
        screen.mouseReleased(area.x() + 3, area.y() + 3, 1);
        var copied = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(keyboard).setClipboard(copied.capture());
        assertTrue(copied.getValue().contains("规则方向：排除"));
        assertTrue(copied.getValue().contains("完整内部值：test:tag_0"));
        assertTrue(copied.getValue().contains("显示名称：标签：test:tag_0"));
        assertEquals(initial, field("working"));
    }

    @Test void hoverRenderingPassesMultipleReadableLinesToTheMinecraftTooltipRenderer() throws Exception {
        when(font.split(any(net.minecraft.network.chat.FormattedText.class), anyInt())).thenAnswer(call ->
            List.of(net.minecraft.util.FormattedCharSequence.forward(
                call.<net.minecraft.network.chat.FormattedText>getArgument(0).getString(), net.minecraft.network.chat.Style.EMPTY)));
        var graphics = mock(net.minecraft.client.gui.GuiGraphics.class);
        var selector = (AttributeSelector) field("selector");
        int x = selector.getX() + 2, y = selector.getY() + 2;
        screen.renderTooltip(graphics, x, y);
        verify(graphics).renderTooltip(eq(font), argThat((List<net.minecraft.util.FormattedCharSequence> lines) -> lines.size() >= 4), eq(x), eq(y));
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
