package dev.rs2multifilter.minecraft.client;

import com.refinedmods.refinedstorage.common.grid.GridContainerMenu;
import com.refinedmods.refinedstorage.common.grid.GridData;
import com.refinedmods.refinedstorage.common.grid.screen.AbstractGridScreen;
import dev.rs2multifilter.core.*;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real RS menu and production screen handlers; JEI's screen transition is simulated, not JEI input. */
class GridScreenLifecycleTest {
    @TempDir Path directory;
    private Minecraft minecraft;
    private LocalPlayer player;
    private GridContainerMenu menu;
    private AbstractGridScreen<?> screen;
    private MockedStatic<Minecraft> client;
    private java.lang.reflect.Field preferencesField;
    private Object oldPreferences;
    private GridPreferences preferences;

    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @BeforeEach void open() throws Exception {
        ClientCardState.clearAll();
        player = mock(LocalPlayer.class);
        var inventory = new Inventory(player);
        when(player.getInventory()).thenReturn(inventory);
        var level = mock(Level.class);
        when(player.level()).thenReturn(level);
        when(level.enabledFeatures()).thenReturn(FeatureFlags.DEFAULT_FLAGS);
        menu = new GridContainerMenu(17, inventory, new GridData(false, List.of(), Set.of()));
        player.containerMenu = menu;
        minecraft = mock(Minecraft.class);
        minecraft.player = player;
        var fontField = Minecraft.class.getDeclaredField("font");
        fontField.setAccessible(true); fontField.set(minecraft, mock(Font.class));
        client = mockStatic(Minecraft.class); client.when(Minecraft::getInstance).thenReturn(minecraft);
        preferencesField = GridTabs.class.getDeclaredField("preferences");
        preferencesField.setAccessible(true); oldPreferences = preferencesField.get(null);
        preferences = new GridPreferences(directory.resolve("preferences.properties"));
        preferencesField.set(null, preferences);
        screen = mock(AbstractGridScreen.class);
        when(screen.getMenu()).thenReturn(menu);
        when(screen.getExclusionZones()).thenReturn(new ArrayList<>());
        when(screen.getGuiLeft()).thenReturn(50); when(screen.getGuiTop()).thenReturn(30);
    }
    @AfterEach void close() throws Exception {
        if (minecraft != null) {
            minecraft.player = null;
            closing();
        }
        ClientCardState.clearAll();
        if (preferencesField != null) preferencesField.set(null, oldPreferences);
        if (client != null) client.close();
    }
    private List<Button> initialize() {
        var event = mock(ScreenEvent.Init.Post.class);
        when(event.getScreen()).thenReturn(screen);
        GridTabs.onScreenInit(event);
        var captured = org.mockito.ArgumentCaptor.forClass(GuiEventListener.class);
        verify(event, times(10)).addListener(captured.capture());
        return captured.getAllValues().stream().map(Button.class::cast).toList();
    }
    private void closing() {
        var event = mock(ScreenEvent.Closing.class); when(event.getScreen()).thenReturn(screen);
        GridTabs.onScreenClosing(event);
    }
    private Object field(Object object, String name) throws Exception {
        var f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object);
    }
    private GridFilterSession session() throws Exception {
        var f = GridTabs.class.getDeclaredField("STATES"); f.setAccessible(true);
        Object state = ((Map<?, ?>) f.get(null)).get(screen);
        return (GridFilterSession) field(state, "session");
    }
    private FilterCard synchronize(int count) {
        var card = new FilterCard(FilterCard.VERSION, java.util.stream.IntStream.range(0, count)
            .mapToObj(i -> new FilterCategory("Category " + i, i % 2 == 0 ? "minecraft:dirt" : "minecraft:stone",
                List.of(new FilterRule(FilterRule.Kind.ITEM, "minecraft:dirt", i % 2 == 0)))).toList());
        for (int i = 0; i < 4; i++) ClientCardState.update(menu.containerId, i, i == 0 ? CardWireCodec.encode(card) : "");
        return card;
    }
    static Stream<org.junit.jupiter.params.provider.Arguments> transitions() {
        return Stream.of("item lookup and E return", "recipe view and close", "recipe transfer and return")
            .flatMap(path -> Stream.of(0, 2, 13).flatMap(count -> Stream.of(false, true)
                .map(remember -> org.junit.jupiter.params.provider.Arguments.of(path, count, remember))));
    }
    @ParameterizedTest(name = "{0}: {1} categories, remember={2}")
    @MethodSource("transitions")
    void temporaryScreenSwitchRetainsCardsSelectionAndSession(String path, int count, boolean remember) throws Exception {
        preferences.setRemember(remember);
        FilterCard original = synchronize(count);
        int revision = ClientCardState.revision(menu.containerId);
        var buttons = initialize();
        int selected = -1;
        if (count > 0) {
            if (count > 7) buttons.get(9).onPress();
            buttons.get(1).onPress();
            selected = (int) field(buttons.get(1), "category");
        }
        GridFilterSession originalSession = session();
        for (int cycle = 0; cycle < 5; cycle++) {
            // JEI holds the same parent screen/menu. No server menu-open event or card resync occurs.
            closing();
            assertTrue(ClientCardState.hasCompleteSync(menu.containerId), path + " must not discard installed-card packets");
            assertSame(originalSession, session(), "Keep H4 suspension/recovery and native filter ownership");
            buttons = initialize();
            assertEquals(original, ClientCardState.card(menu.containerId));
            assertEquals(revision, ClientCardState.revision(menu.containerId));
            assertSame(originalSession, session());
            assertEquals(selected, session().selected(), "A temporary screen switch is not a terminal reopen");
            assertEquals(count == 0 ? 1 : Math.min(count + 1, 8),
                buttons.subList(0, 8).stream().filter(b -> b.visible).count());
            for (var button : buttons.subList(0, 8)) if (button.visible) {
                int category = (int) field(button, "category");
                ItemStack icon = (ItemStack) field(button, "icon");
                assertEquals(category < 0 ? "全部" : original.categories().get(category).name(), button.getMessage().getString());
                assertTrue(icon.is(category < 0 ? Items.GRASS_BLOCK : category % 2 == 0 ? Items.DIRT : Items.STONE));
                assertEquals(category == selected, field(button, "chosen"));
            }
            verify(screen, never()).onClose();
        }
    }
}
