package dev.rs2multifilter.minecraft.client;

import com.refinedmods.refinedstorage.common.grid.AbstractGridContainerMenu;
import com.refinedmods.refinedstorage.common.grid.GridContainerMenu;
import com.refinedmods.refinedstorage.common.grid.GridData;
import com.refinedmods.refinedstorage.common.grid.view.ItemGridResource;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import dev.rs2multifilter.core.CardWireCodec;
import dev.rs2multifilter.core.FilterCard;
import dev.rs2multifilter.core.FilterCategory;
import dev.rs2multifilter.core.FilterRule;
import dev.rs2multifilter.minecraft.CardComponents;
import dev.rs2multifilter.minecraft.MultiFilterMod;
import dev.rs2multifilter.minecraft.storage.InstalledCardSlot;
import dev.rs2multifilter.minecraft.storage.InstalledCardHolder;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import com.refinedmods.refinedstorage.common.grid.screen.AbstractGridScreen;
import net.neoforged.neoforge.client.event.ScreenEvent;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Integration tests execute the real RS menu, installed slots, and repository view. */
class GridTerminalInteractionTest {
    @TempDir Path settingsDirectory;
    private Player player;
    private Inventory inventory;
    private GridContainerMenu menu;

    @BeforeAll static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach void openRealTerminalMenu() {
        player = mock(Player.class);
        inventory = new Inventory(player);
        when(player.getInventory()).thenReturn(inventory);
        Level level = mock(Level.class);
        when(player.level()).thenReturn(level);
        when(level.enabledFeatures()).thenReturn(FeatureFlags.DEFAULT_FLAGS);
        menu = new GridContainerMenu(1, inventory, new GridData(false, List.of(), Set.of()));
        player.containerMenu = menu;
        assertEquals(4, menu.slots.stream().filter(InstalledCardSlot.class::isInstance).count(),
            "The actual RS resize mixin must install all four card slots");
    }

    @AfterEach void closeSession() {
        if (menu != null) GridFilterSession.detach(menu);
    }

    private List<InstalledCardSlot> cardSlots() {
        return menu.slots.stream().filter(InstalledCardSlot.class::isInstance)
            .map(InstalledCardSlot.class::cast).toList();
    }

    private ItemStack configuredCard(int number) {
        ItemStack card = new ItemStack(MultiFilterMod.FILTER_CARD.get());
        card.set(DataComponents.CUSTOM_NAME, Component.literal("卡片 " + number));
        CardComponents.write(card, FilterCard.blank().withCategory(new FilterCategory(
            "分类 " + number, "minecraft:paper", List.of(new FilterRule(FilterRule.Kind.ITEM, "minecraft:dirt", false)))));
        return card;
    }

    private long inventoryCardCount() {
        return inventory.items.stream().filter(s -> s.is(MultiFilterMod.FILTER_CARD.get()))
            .mapToLong(ItemStack::getCount).sum();
    }

    @Test void shiftClickReturnsEachOfTheFourCardsWithItsConfiguration() {
        var slots = cardSlots();
        for (int i = 0; i < 4; i++) slots.get(i).set(configuredCard(i));
        for (int i = 0; i < 4; i++) {
            ItemStack original = slots.get(i).getItem().copy();
            menu.clicked(slots.get(i).index, 0, ClickType.QUICK_MOVE, player);
            assertTrue(slots.get(i).getItem().isEmpty());
            assertEquals(i + 1, inventoryCardCount());
            assertTrue(inventory.items.stream().anyMatch(s -> ItemStack.matches(s, original)));
            assertTrue(menu.getCarried().isEmpty());
        }
    }

    @Test void categoryStripResizeDoesNotStackFiltersAndCloseRemembersTheActualSelection() throws Exception {
        loadResources(Items.DIRT, Items.STONE);
        var card = FilterCard.blank().withCategory(new FilterCategory("泥土", "minecraft:dirt", List.of(
                new FilterRule(FilterRule.Kind.ITEM, "minecraft:dirt", false))))
            .withCategory(new FilterCategory("石头", "minecraft:stone", List.of(
                new FilterRule(FilterRule.Kind.ITEM, "minecraft:stone", false))));
        for (int i = 0; i < 4; i++) ClientCardState.update(menu.containerId, i, i == 0 ? CardWireCodec.encode(card) : "");
        var preferences = new GridPreferences(settingsDirectory.resolve("settings.properties"));
        var field = GridTabs.class.getDeclaredField("preferences");
        field.setAccessible(true);
        Object before = field.get(null);
        field.set(null, preferences);
        var screen = mock(AbstractGridScreen.class);
        when(screen.getMenu()).thenReturn(menu);
        when(screen.getExclusionZones()).thenReturn(new java.util.ArrayList<>());
        when(screen.getXSize()).thenReturn(193);
        screen.width = 320;
        when(screen.getGuiLeft()).thenReturn(50);
        when(screen.getGuiTop()).thenReturn(30);
        var init = mock(ScreenEvent.Init.Post.class);
        when(init.getScreen()).thenReturn(screen);
        var closing = mock(ScreenEvent.Closing.class);
        when(closing.getScreen()).thenReturn(screen);
        try (var client = mockStatic(Minecraft.class)) {
            var minecraft = mock(Minecraft.class);
            var font = mock(net.minecraft.client.gui.Font.class);
            when(font.width(anyString())).thenAnswer(call -> call.<String>getArgument(0).length() * 6);
            var fontField = Minecraft.class.getDeclaredField("font");
            fontField.setAccessible(true);
            fontField.set(minecraft, font);
            client.when(Minecraft::getInstance).thenReturn(minecraft);
            GridTabs.onScreenInit(init);
            var widgets = org.mockito.ArgumentCaptor.forClass(net.minecraft.client.gui.components.events.GuiEventListener.class);
            verify(init, times(8)).addListener(widgets.capture());
            assertEquals(3, widgets.getAllValues().stream().map(Button.class::cast).filter(b -> b.visible).count());
            assertFalse(((Button) widgets.getAllValues().get(6)).visible);
            assertFalse(((Button) widgets.getAllValues().get(7)).visible);
            verify(screen).addSideButton(any(RememberCategoryButton.class));
            ((EditorButton) widgets.getAllValues().get(1)).onPress();
            assertVisible(Items.DIRT);
            var resize = mock(ScreenEvent.Init.Post.class);
            when(resize.getScreen()).thenReturn(screen);
            GridTabs.onScreenInit(resize);
            var resizedWidgets = org.mockito.ArgumentCaptor.forClass(net.minecraft.client.gui.components.events.GuiEventListener.class);
            verify(resize, times(8)).addListener(resizedWidgets.capture());
            ((EditorButton) resizedWidgets.getAllValues().get(2)).onPress();
            assertVisible(Items.STONE);
            ((EditorButton) resizedWidgets.getAllValues().get(0)).onPress();
            assertVisible(Items.DIRT, Items.STONE);
            assertEquals(2, ClientCardState.card(menu.containerId).categories().size());
            ((EditorButton) resizedWidgets.getAllValues().get(2)).onPress();
            GridTabs.onScreenClosing(closing);
            assertEquals(1, new GridPreferences(settingsDirectory.resolve("settings.properties")).restore("unknown", card));
            assertFalse(ClientCardState.hasSync(menu.containerId));
        } finally {
            GridTabs.onScreenClosing(closing);
            field.set(null, before);
        }
    }

    @Test void pagingChangesOnlyVisibleTabsAndClickingALaterPageUsesItsOriginalCategoryIndex() throws Exception {
        loadResources(Items.DIRT, Items.STONE);
        var categories = java.util.stream.IntStream.range(0, 12).mapToObj(i -> new FilterCategory("C" + i,
            "minecraft:paper", List.of(new FilterRule(FilterRule.Kind.ITEM, i % 2 == 0 ? "minecraft:dirt" : "minecraft:stone", false)))).toList();
        var card = new FilterCard(FilterCard.VERSION, categories);
        for (int i = 0; i < 4; i++) ClientCardState.update(menu.containerId, i, i == 0 ? CardWireCodec.encode(card) : "");
        var preferenceField = GridTabs.class.getDeclaredField("preferences");
        preferenceField.setAccessible(true);
        Object before = preferenceField.get(null);
        preferenceField.set(null, new GridPreferences(settingsDirectory.resolve("paging.properties")));
        var screen = mock(AbstractGridScreen.class);
        when(screen.getMenu()).thenReturn(menu);
        when(screen.getGuiLeft()).thenReturn(50);
        when(screen.getGuiTop()).thenReturn(30);
        when(screen.getXSize()).thenReturn(193);
        when(screen.getExclusionZones()).thenReturn(new java.util.ArrayList<>());
        screen.width = 320;
        var init = mock(ScreenEvent.Init.Post.class);
        when(init.getScreen()).thenReturn(screen);
        var closing = mock(ScreenEvent.Closing.class);
        when(closing.getScreen()).thenReturn(screen);
        try (var client = mockStatic(Minecraft.class)) {
            var minecraft = mock(Minecraft.class);
            var font = mock(net.minecraft.client.gui.Font.class);
            when(font.width(anyString())).thenAnswer(call -> call.<String>getArgument(0).length() * 6);
            var fontField = Minecraft.class.getDeclaredField("font");
            fontField.setAccessible(true); fontField.set(minecraft, font);
            client.when(Minecraft::getInstance).thenReturn(minecraft);
            GridTabs.onScreenInit(init);
            var captured = org.mockito.ArgumentCaptor.forClass(net.minecraft.client.gui.components.events.GuiEventListener.class);
            verify(init, times(8)).addListener(captured.capture());
            var buttons = captured.getAllValues().stream().map(Button.class::cast).toList();
            assertTrue(buttons.get(7).visible);
            assertEquals(Button.class, buttons.get(6).getClass());
            assertEquals(Button.class, buttons.get(7).getClass());
            assertEquals("<", buttons.get(6).getMessage().getString());
            assertEquals(">", buttons.get(7).getMessage().getString());
            buttons.get(7).onPress();
            assertVisible(Items.DIRT, Items.STONE);
            assertEquals("全部", buttons.get(0).getMessage().getString());
            assertEquals("C4", buttons.get(1).getMessage().getString());
            buttons.get(2).onPress();
            assertVisible(Items.STONE);
            buttons.get(7).onPress();
            assertVisible(Items.STONE);
            buttons.get(0).onPress();
            assertVisible(Items.DIRT, Items.STONE);
            assertEquals(card, ClientCardState.card(menu.containerId));
            assertFalse(buttons.get(7).active);
            buttons.get(6).onPress();
            assertVisible(Items.DIRT, Items.STONE);
            assertEquals("C4", buttons.get(1).getMessage().getString());
            assertEquals(card, ClientCardState.card(menu.containerId));
        } finally {
            GridTabs.onScreenClosing(closing);
            preferenceField.set(null, before);
        }
    }

    @Test void tabUsesTheGrassBlockOrConfiguredIconAndNeverDrawsItsName() throws Exception {
        var card = FilterCard.blank().withCategory(new FilterCategory("只在悬停显示的完整名称", "minecraft:stone", List.of()));
        for (int i = 0; i < 4; i++) ClientCardState.update(menu.containerId, i, i == 0 ? CardWireCodec.encode(card) : "");
        var preferenceField = GridTabs.class.getDeclaredField("preferences");
        preferenceField.setAccessible(true);
        Object before = preferenceField.get(null);
        preferenceField.set(null, new GridPreferences(settingsDirectory.resolve("icons.properties")));
        var screen = mock(AbstractGridScreen.class);
        when(screen.getMenu()).thenReturn(menu);
        when(screen.getGuiLeft()).thenReturn(50);
        when(screen.getGuiTop()).thenReturn(30);
        when(screen.getXSize()).thenReturn(193);
        when(screen.getExclusionZones()).thenReturn(new java.util.ArrayList<>());
        screen.width = 320;
        var init = mock(ScreenEvent.Init.Post.class);
        when(init.getScreen()).thenReturn(screen);
        var closing = mock(ScreenEvent.Closing.class);
        when(closing.getScreen()).thenReturn(screen);
        try (var client = mockStatic(Minecraft.class)) {
            var minecraft = mock(Minecraft.class);
            var font = mock(net.minecraft.client.gui.Font.class);
            var fontField = Minecraft.class.getDeclaredField("font");
            fontField.setAccessible(true); fontField.set(minecraft, font);
            client.when(Minecraft::getInstance).thenReturn(minecraft);
            GridTabs.onScreenInit(init);
            var captured = org.mockito.ArgumentCaptor.forClass(net.minecraft.client.gui.components.events.GuiEventListener.class);
            verify(init, times(8)).addListener(captured.capture());
            var buttons = captured.getAllValues().stream().map(Button.class::cast).toList();
            var g = mock(GuiGraphics.class);
            when(g.pose()).thenReturn(new com.mojang.blaze3d.vertex.PoseStack());
            ((EditorButton) buttons.get(0)).renderWidget(g, -1, -1, 0);
            ((EditorButton) buttons.get(1)).renderWidget(g, -1, -1, 0);
            verify(g).renderItem(argThat(s -> s.is(Items.GRASS_BLOCK)), anyInt(), anyInt());
            verify(g).renderItem(argThat(s -> s.is(Items.STONE)), anyInt(), anyInt());
            verify(g, never()).drawString(any(net.minecraft.client.gui.Font.class), anyString(), anyInt(), anyInt(), anyInt(), anyBoolean());
            verify(g, never()).fill(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
            verify(g).blitSprite(RsUi.TAB_SELECTED, 26, 32, 0, 28, 52, 30, 26, 4);
            verify(g).blitSprite(RsUi.TAB_UNSELECTED, 26, 32, 0, 28, 84, 26, 26, 4);
            assertEquals("只在悬停显示的完整名称", buttons.get(1).getMessage().getString());
            assertNotNull(buttons.get(1).getTooltip());
            assertEquals(CategoryNavigation.GAP, buttons.get(1).getX() - buttons.get(0).getX() - buttons.get(0).getWidth());
            assertEquals(card, ClientCardState.card(menu.containerId));
        } finally {
            GridTabs.onScreenClosing(closing);
            preferenceField.set(null, before);
        }
    }

    @Test void attachedSlotsDrawOpaqueGrayAndMatchTheNativeBackgroundHook() throws Exception {
        var screen = mock(AbstractGridScreen.class);
        when(screen.getGuiLeft()).thenReturn(50);
        when(screen.getGuiTop()).thenReturn(30);
        var graphics = mock(GuiGraphics.class);
        GridTabs.renderAttachedPanels(screen, graphics);
        verify(graphics).blit(RsUi.FILTER, 247, 30, 180, 0, 30, 82);
        var target = Class.forName("com.refinedmods.refinedstorage.common.support.stretching.AbstractStretchingScreen");
        assertEquals(void.class, target.getDeclaredMethod("renderBg", GuiGraphics.class,
            float.class, int.class, int.class).getReturnType());
        // NeoForge JUnit runs on the server distribution: client-only mixins
        // cannot be claimed as runtime-tested by this test suite.
        assertEquals(net.neoforged.api.distmarker.Dist.DEDICATED_SERVER,
            net.neoforged.fml.loading.FMLEnvironment.dist);
        try (var resource = getClass().getClassLoader().getResourceAsStream("rs2multifilter.mixins.json")) {
            assertNotNull(resource);
            var config = com.google.gson.JsonParser.parseString(new String(resource.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            assertTrue(config.getAsJsonObject().getAsJsonArray("client").asList().stream()
                .anyMatch(entry -> entry.getAsString().equals("GridPanelBackgroundMixin")));
        }
    }

    @Test void categoryStripHitBoxTracksPositionAndExcludesTheNativeInventory() {
        var screen = mock(AbstractGridScreen.class);
        when(screen.getGuiLeft()).thenReturn(50);
        when(screen.getGuiTop()).thenReturn(30);
        assertTrue(GridTabs.isCategoryStripAt(screen, 50, 7));
        assertTrue(GridTabs.isCategoryStripAt(screen, 239, 29));
        assertFalse(GridTabs.isCategoryStripAt(screen, 243, 10));
        assertFalse(GridTabs.isCategoryStripAt(screen, 50, 31));
        assertFalse(GridTabs.isCategoryStripAt(screen, 70, 80));
        when(screen.getGuiLeft()).thenReturn(100);
        when(screen.getGuiTop()).thenReturn(60);
        assertFalse(GridTabs.isCategoryStripAt(screen, 50, 7));
        assertTrue(GridTabs.isCategoryStripAt(screen, 100, 37));
    }
    @Test void nativeSidebarButtonTogglesAndPersistsMemoryWithoutChangingTheCurrentFilter() {
        var path = settingsDirectory.resolve("sidebar.properties");
        var preferences = new GridPreferences(path);
        var card = FilterCard.blank().withCategory(new FilterCategory("分类1", "minecraft:paper", List.of()));
        var selection = new GridCategoryState(preferences, "world");
        selection.synchronize(card, true);
        selection.choose(0);
        var button = new RememberCategoryButton(selection);
        button.onPress();
        assertTrue(button.getSubText().getFirst().getString().contains("关闭"));
        assertEquals(0, selection.selected());
        assertFalse(new GridPreferences(path).remembersCategory());
        assertEquals(-1, new GridPreferences(path).restore("world", card));
        button.onPress();
        assertTrue(button.getSubText().getFirst().getString().contains("开启"));
        assertEquals(0, new GridPreferences(path).restore("world", card));
        assertEquals(card, selection.card());
    }

    @Test void fullInventoryKeepsAllFourCardsAndRetryCannotDuplicateThem() {
        for (int i = 0; i < 36; i++) inventory.setItem(i, new ItemStack(Items.STONE, 64));
        var slots = cardSlots();
        for (int i = 0; i < 4; i++) slots.get(i).set(configuredCard(i));
        for (int repeat = 0; repeat < 3; repeat++) {
            for (int i = 0; i < 4; i++) {
                var before = slots.get(i).getItem().copy();
                assertTrue(menu.quickMoveStack(player, slots.get(i).index).isEmpty());
                assertTrue(ItemStack.matches(before, slots.get(i).getItem()));
            }
        }
        inventory.setItem(12, ItemStack.EMPTY);
        var original = slots.get(2).getItem().copy();
        menu.clicked(slots.get(2).index, 0, ClickType.QUICK_MOVE, player);
        assertTrue(ItemStack.matches(original, inventory.getItem(12)));
        assertTrue(slots.get(2).getItem().isEmpty());
        menu.quickMoveStack(player, slots.get(2).index);
        assertEquals(1, inventoryCardCount());
        assertEquals(3, slots.stream().filter(s -> s.hasItem()).count());
        assertTrue(menu.getCarried().isEmpty());
        verify(player, never()).drop(any(ItemStack.class), anyBoolean());
    }

    @Test void resizingKeepsExactlyFourFunctionalCardSlots() {
        menu.resized(100, 0, 0);
        assertEquals(4, cardSlots().size());
        var slot = cardSlots().get(3);
        slot.set(configuredCard(3));
        menu.clicked(slot.index, 0, ClickType.QUICK_MOVE, player);
        assertTrue(slot.getItem().isEmpty());
        assertEquals(1, inventoryCardCount());
    }

    @Test void quickMoveAlsoClearsTheRealGridBlockEntityCardStorage() throws Exception {
        var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("refinedstorage:grid"));
        var grid = ((EntityBlock) block).newBlockEntity(BlockPos.ZERO, block.defaultBlockState());
        var holder = assertInstanceOf(InstalledCardHolder.class, grid);
        var gridField = AbstractGridContainerMenu.class.getDeclaredField("grid");
        gridField.setAccessible(true);
        gridField.set(menu, grid);
        var slot = cardSlots().get(0);
        var original = configuredCard(0);
        slot.set(original);
        assertTrue(ItemStack.matches(original, holder.rs2mf$getInstalledCard(0)));
        menu.clicked(slot.index, 0, ClickType.QUICK_MOVE, player);
        assertTrue(holder.rs2mf$getInstalledCard(0).isEmpty());
        assertTrue(slot.getItem().isEmpty());
        assertEquals(1, inventoryCardCount());
        menu.quickMoveStack(player, slot.index);
        assertEquals(1, inventoryCardCount());
    }

    @Test void terminalViewShowsOrdinaryAndReverseItemRulesCorrectlyAfterSaveReload() {
        loadResources(Items.DIRT, Items.STONE, Items.APPLE);
        var ordinary = new FilterCategory("普通", "minecraft:dirt", List.of(
            new FilterRule(FilterRule.Kind.ITEM, "minecraft:dirt", false)));
        var reverse = new FilterCategory("反向", "minecraft:dirt", List.of(
            new FilterRule(FilterRule.Kind.ITEM, "minecraft:dirt", true)));
        var card = FilterCard.blank().withCategory(ordinary).withCategory(reverse);
        var restored = CardWireCodec.decode(CardWireCodec.encode(card));
        var session = GridFilterSession.attach(menu, restored);
        session.select(0);
        assertVisible(Items.DIRT);
        session.select(1);
        assertVisible(Items.STONE, Items.APPLE);
        session.select(-1);
        assertVisible(Items.DIRT, Items.STONE, Items.APPLE);
    }

    @Test void terminalExclusionsVetoIncludesAndKeepNativeSearch() {
        loadResources(Items.DIRT, Items.STONE, Items.APPLE);
        var category = new FilterCategory("混合", "minecraft:paper", List.of(
            new FilterRule(FilterRule.Kind.MOD, "minecraft", false),
            new FilterRule(FilterRule.Kind.ITEM, "minecraft:dirt", true)));
        var session = GridFilterSession.attach(menu, FilterCard.blank().withCategory(category));
        session.select(0);
        assertVisible(Items.STONE, Items.APPLE);
        menu.getRepository().setFilterAndSort(GridFilterSession.onNativeFilter(menu,
            (repo, resource) -> ((ItemGridResource) resource).getItemStack().is(Items.APPLE)));
        assertVisible(Items.APPLE);
        session.select(-1);
        assertVisible(Items.APPLE);
    }

    @Test void terminalFoodRuleAndItsReverseUseActualItemComponents() {
        loadResources(Items.DIRT, Items.APPLE);
        var card = FilterCard.blank()
            .withCategory(new FilterCategory("食物", "minecraft:apple", List.of(
                new FilterRule(FilterRule.Kind.FOOD, "true", false))))
            .withCategory(new FilterCategory("非食物", "minecraft:stone", List.of(
                new FilterRule(FilterRule.Kind.FOOD, "true", true))));
        var session = GridFilterSession.attach(menu, card);
        session.select(0);
        assertVisible(Items.APPLE);
        session.select(1);
        assertVisible(Items.DIRT);
    }

    private void loadResources(Item... items) {
        for (Item item : items) menu.getRepository().update(new ItemResource(item), 64);
    }

    private void assertVisible(Item... expected) {
        var actual = menu.getRepository().getViewList().stream()
            .map(resource -> ((ItemGridResource) resource).getItemStack().getItem()).collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.copyOf(Arrays.asList(expected)), actual);
    }
}
