package dev.rs2multifilter.minecraft.client;

import dev.rs2multifilter.core.FilterCard;
import dev.rs2multifilter.minecraft.MultiFilterMod;
import com.refinedmods.refinedstorage.common.grid.screen.AbstractGridScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.ArrayList;
import net.minecraft.client.renderer.Rect2i;

/** Native-sized gray category strip. Actual cards are rendered by the real menu. */
@EventBusSubscriber(modid = MultiFilterMod.ID, value = Dist.CLIENT)
public final class GridTabs {
    // Match the terminal's left edge and the existing upgrade panel's right edge.
    public static final int HEADER_WIDTH = 197 + 30;
    private static final Map<AbstractGridScreen<?>, TabState> STATES = new WeakHashMap<>();
    private static GridPreferences preferences;
    private GridTabs() {}
    private record TabState(GridFilterSession session, GridCategoryState selection, Runnable refresh, int[] page) {}

    private static GridPreferences preferences() {
        if (preferences == null) preferences = new GridPreferences(
            FMLPaths.CONFIGDIR.get().resolve("rs2multifilter-client.properties"));
        return preferences;
    }
    private static String scope() {
        Minecraft client = Minecraft.getInstance();
        if (client.getSingleplayerServer() != null)
            return "local:" + client.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        var server = client.getCurrentServer();
        return server == null ? "unknown" : "server:" + server.ip.toLowerCase(java.util.Locale.ROOT);
    }
    @SubscribeEvent public static void onScreenClosing(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof AbstractGridScreen<?> screen) {
            // JEI temporarily replaces the screen but keeps this exact menu open.
            // ScreenEvent.Closing is not a container-close notification.
            var client = Minecraft.getInstance();
            var player = client == null ? null : client.player;
            if (player == null || player.containerMenu != screen.getMenu()) close(screen);
        }
    }
    private static void close(AbstractGridScreen<?> screen) {
        TabState state = STATES.remove(screen);
        if (state != null) state.selection.rememberCurrent();
        GridFilterSession.detach(screen.getMenu());
        ClientCardState.clear(screen.getMenu().containerId);
    }
    @SubscribeEvent public static void onClientTick(ClientTickEvent.Post event) {
        if (STATES.isEmpty()) return;
        var client = Minecraft.getInstance();
        var player = client == null ? null : client.player;
        // Also handles a server-side close while JEI is covering the terminal.
        // Only compare menu identities; do not read/rebuild categories each tick.
        for (var screen : new ArrayList<>(STATES.keySet())) {
            if (player == null || player.containerMenu != screen.getMenu()) close(screen);
        }
    }
    @SubscribeEvent public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        for (var screen : new ArrayList<>(STATES.keySet())) close(screen);
        ClientCardState.clearAll();
    }
    @SubscribeEvent public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof AbstractGridScreen<?> screen)) return;
        TabState old = STATES.get(screen);
        GridCategoryState selection = old == null ? new GridCategoryState(preferences(), scope()) : old.selection;
        int menuId = screen.getMenu().containerId;
        selection.synchronize(ClientCardState.card(menuId), ClientCardState.hasCompleteSync(menuId));
        boolean sameCard = old != null && old.session.card().equals(selection.card());
        GridFilterSession session = old == null ? GridFilterSession.attach(screen.getMenu(), selection.card()) : old.session;
        session.updateCard(selection.card());
        session.select(selection.selected());
        int x = screen.getGuiLeft(), y = Math.max(0, screen.getGuiTop() - 23);
        int width = barWidth(screen);
        var font = Minecraft.getInstance().font;
        CategoryNavigation[] navigation = {new CategoryNavigation(selection.card().categories().stream()
            .map(c -> c.name()).toList(), font::width, width)};
        FilterCard[] layoutCard = {selection.card()};
        int[] page = {!sameCard
            ? navigation[0].pageFor(selection.selected()) : navigation[0].clamp(old.page[0])};
        Runnable[] refresh = {() -> {}};
        var tabs = new ArrayList<TabButton>();
        for (int i = 0; i < CategoryNavigation.maxVisibleTabs(width); i++) {
            var tab = new TabButton(x, y, b -> {
                selection.choose(((TabButton)b).category);
                session.select(selection.selected());
                refresh[0].run();
            });
            tabs.add(tab);
            event.addListener(tab);
        }
        var previous = Button.builder(Component.literal("<"),
            b -> { page[0] = navigation[0].clamp(page[0] - 1); refresh[0].run(); })
            .bounds(x + navigation[0].previousX(), y + 3, CategoryNavigation.ARROW_WIDTH, 18).build();
        var next = Button.builder(Component.literal(">"),
            b -> { page[0] = navigation[0].clamp(page[0] + 1); refresh[0].run(); })
            .bounds(x + navigation[0].nextX(), y + 3, CategoryNavigation.ARROW_WIDTH, 18).build();
        event.addListener(previous);
        event.addListener(next);
        refresh[0] = () -> {
            FilterCard card = selection.card();
            if (!layoutCard[0].equals(card)) {
                navigation[0] = new CategoryNavigation(card.categories().stream().map(c -> c.name()).toList(), font::width, width);
                page[0] = navigation[0].pageFor(selection.selected());
                layoutCard[0] = card;
            }
            page[0] = navigation[0].clamp(page[0]);
            var shown = navigation[0].tabs(page[0]);
            for (int i = 0; i < tabs.size(); i++) {
                var button = tabs.get(i);
                button.visible = i < shown.size();
                if (!button.visible) continue;
                var tab = shown.get(i);
                button.category = tab.category();
                button.terminalLeft = x;
                button.chosen = tab.category() == selection.selected();
                button.setX(x + tab.x());
                button.setWidth(terminalTabWidth(tab, i == shown.size() - 1));
                String title = tab.category() < 0 ? "全部" : card.categories().get(tab.category()).name();
                button.setMessage(Component.literal(title));
                button.icon = tab.category() < 0 ? new ItemStack(Items.GRASS_BLOCK) : iconFor(card.categories().get(tab.category()).iconItemId());
                button.setTooltip(Tooltip.create(Component.literal(tab.category() < 0
                    ? "全部\n显示全部物品，保留终端原有搜索与显示设置"
                    : title + " · " + (tab.category() + 1) + "/" + card.categories().size())));
            }
            previous.visible = next.visible = navigation[0].paginated();
            previous.active = page[0] > 0;
            next.active = page[0] + 1 < navigation[0].count();
            String pages = (page[0] + 1) + "/" + navigation[0].count();
            previous.setTooltip(Tooltip.create(Component.literal("上一页分类 · " + pages)));
            next.setTooltip(Tooltip.create(Component.literal("下一页分类 · " + pages)));
        };
        screen.addSideButton(new RememberCategoryButton(selection));
        screen.getExclusionZones().add(new Rect2i(screen.getGuiLeft(), y, barWidth(screen), 24));
        screen.getExclusionZones().add(new Rect2i(screen.getGuiLeft() + 197, screen.getGuiTop(), 30, 82));
        STATES.put(screen, new TabState(session, selection, refresh[0], page));
        refresh[0].run();
    }

    private static int barWidth(AbstractGridScreen<?> screen) {
        return HEADER_WIDTH;
    }
    /** Correct only the full row's one-pixel overhang; paging and gaps stay unchanged. */
    public static int terminalTabWidth(CategoryNavigation.Tab tab, boolean last) {
        return last && tab.x() + tab.width() == 194 ? tab.width() - 1 : tab.width();
    }
    /** Reserve room for the attached header when RS's minimum 3 rows would overlap it. */
    public static int fitRows(int nativeRows, int viewportHeight, int bottomHeight) {
        int rows = Math.max(1, nativeRows);
        while (rows > 1 && (viewportHeight - 19 - 18 * rows - bottomHeight) / 2 < 23) rows--;
        return rows;
    }
    private static ItemStack iconFor(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key != null && BuiltInRegistries.ITEM.containsKey(key)
            ? new ItemStack(BuiltInRegistries.ITEM.get(key)) : new ItemStack(Items.PAPER);
    }
    /** Called after RS paints its background, BEFORE vanilla draws slot items. */
    public static boolean isCategoryStripAt(AbstractGridScreen<?> screen, double mx, double my) {
        int x = screen.getGuiLeft(), y = Math.max(0, screen.getGuiTop() - 23);
        return mx >= x && mx < x + barWidth(screen) && my >= y && my < y + 24;
    }

    public static void renderAttachedPanels(AbstractGridScreen<?> screen, GuiGraphics g) {
        int x = screen.getGuiLeft(), y = screen.getGuiTop();
        // The unmodified RS upgrade panel is exactly 30x82 at UV 180,0.
        // Its 4 opaque slots align with the existing menu at 204,6 + n*18.
        g.blit(RsUi.FILTER, x + 197, y, 180, 0, 30, 82);
    }
    @SubscribeEvent public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractGridScreen<?> screen)) return;
        TabState state = STATES.get(screen);
        if (state == null) return;
        state.session.keepNativeControlsWorking();
        state.selection.synchronize(ClientCardState.card(screen.getMenu().containerId),
            ClientCardState.hasCompleteSync(screen.getMenu().containerId));
        if (!state.session.card().equals(state.selection.card()) || state.session.selected() != state.selection.selected()) {
            state.session.updateCard(state.selection.card());
            state.session.select(state.selection.selected());
            state.refresh.run();
        }
    }
    private static final class TabButton extends EditorButton {
        ItemStack icon = ItemStack.EMPTY;
        int category = -1;
        int terminalLeft;
        boolean chosen;
        TabButton(int x, int y, OnPress press) {
            super(x, y, CategoryNavigation.TAB_WIDTH, CategoryNavigation.TAB_HEIGHT, Component.literal("全部"), press);
        }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float tick) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 1);
            // Paint after the terminal background: the selected tab's open base
            // bridges only its own part of the terminal's four-pixel top edge.
            RsUi.iconTab(g, getX(), getY(), chosen, terminalLeft, getWidth());
            g.renderItem(icon, getX() + 5, getY() + (chosen ? 5 : 6));
            g.pose().popPose();
            // Message is narration/tooltip data only; no label is drawn inside a tab.
        }
    }
}
