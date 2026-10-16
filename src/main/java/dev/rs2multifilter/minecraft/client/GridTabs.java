package dev.rs2multifilter.minecraft.client;

import dev.rs2multifilter.core.FilterCard;
import dev.rs2multifilter.minecraft.MultiFilterMod;
import com.refinedmods.refinedstorage.common.grid.screen.AbstractGridScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
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
import java.util.Map;
import java.util.WeakHashMap;

/** Native-sized gray category strip. Actual cards are rendered by the real menu. */
@EventBusSubscriber(modid = MultiFilterMod.ID, value = Dist.CLIENT)
public final class GridTabs {
    private static final Map<AbstractGridScreen<?>, TabState> STATES = new WeakHashMap<>();
    private static GridPreferences preferences;
    private GridTabs() {}
    private record TabState(GridFilterSession session, GridCategoryState selection, Runnable refresh) {}

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
            TabState state = STATES.remove(screen);
            if (state != null) state.selection.rememberCurrent();
            GridFilterSession.detach(screen.getMenu());
            ClientCardState.clear(screen.getMenu().containerId);
        }
    }
    @SubscribeEvent public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof AbstractGridScreen<?> screen)) return;
        // Resize must reuse the session, rather than capturing our category
        // predicate as the native search filter and stacking both filters.
        TabState old = STATES.get(screen);
        GridCategoryState selection = old == null ? new GridCategoryState(preferences(), scope()) : old.selection;
        int menuId = screen.getMenu().containerId;
        selection.synchronize(ClientCardState.card(menuId), ClientCardState.hasCompleteSync(menuId));
        GridFilterSession session = old == null ? GridFilterSession.attach(screen.getMenu(), selection.card()) : old.session;
        session.updateCard(selection.card());
        session.select(selection.selected());
        int x = screen.getGuiLeft(), y = Math.max(2, screen.getGuiTop() - 23);
        Runnable[] refresh = {() -> {}};
        Runnable apply = () -> { session.select(selection.selected()); refresh[0].run(); };
        var previous = new EditorButton(x + 4, y + 3, 16, 18, Component.literal("上一个分类"), b -> {
            selection.cycle(-1); apply.run();
        });
        previous.setIcon(EditorIcon.PREVIOUS);
        previous.setTooltip(Tooltip.create(Component.literal("上一个分类")));
        var category = new CategoryButton(x + 23, y + 3, b -> { selection.cycle(1); apply.run(); });
        var next = new EditorButton(x + 126, y + 3, 16, 18, Component.literal("下一个分类"), b -> {
            selection.cycle(1); apply.run();
        });
        next.setIcon(EditorIcon.NEXT);
        next.setTooltip(Tooltip.create(Component.literal("下一个分类")));
        var all = new EditorButton(x + 145, y + 3, 18, 18, Component.literal("全部物品"), b -> {
            selection.choose(-1); apply.run();
        });
        all.setIcon(EditorIcon.GRID);
        all.setTooltip(Tooltip.create(Component.literal("全部物品：保留终端原有搜索与显示设置")));
        var remember = new EditorButton(x + 167, y + 3, 18, 18, Component.literal("记住分类"), b -> {
            selection.toggleRemember(); refresh[0].run();
        });
        remember.setIcon(EditorIcon.PIN);
        remember.setAccent(0xFF547E91);
        refresh[0] = () -> {
            FilterCard card = selection.card();
            int index = selection.selected();
            all.setSelected(index < 0);
            category.active = previous.active = next.active = !card.categories().isEmpty();
            String title = index < 0 ? "全部物品" : card.categories().get(index).name();
            category.setMessage(Component.literal(title));
            category.icon = index < 0 ? new ItemStack(Items.PAPER) : iconFor(card.categories().get(index).iconItemId());
            category.setTooltip(Tooltip.create(Component.literal(title + (index < 0 ? "" : " · " + (index + 1) + "/" + card.categories().size())
                + "\n点击切换至下一个分类；编辑分类请右键打开过滤升级")));
            remember.setSelected(selection.remembersCategory());
            remember.setTooltip(Tooltip.create(Component.literal("记住上一次分类：" + (selection.remembersCategory() ? "开启" : "关闭")
                + "\n开启：重新打开终端时恢复同一配置的分类\n关闭：重新打开时显示全部物品")));
        };
        for (var button : java.util.List.of(previous, category, next, all, remember)) event.addListener(button);
        STATES.put(screen, new TabState(session, selection, refresh[0]));
        refresh[0].run();
    }
    private static ItemStack iconFor(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key != null && BuiltInRegistries.ITEM.containsKey(key)
            ? new ItemStack(BuiltInRegistries.ITEM.get(key)) : new ItemStack(Items.PAPER);
    }
    /** Called after RS paints its background, BEFORE vanilla draws slot items. */
    public static boolean isCategoryStripAt(AbstractGridScreen<?> screen, double mx, double my) {
        int x = screen.getGuiLeft(), y = Math.max(2, screen.getGuiTop() - 23);
        return mx >= x && mx < x + 190 && my >= y && my < y + 24;
    }

    public static void renderAttachedPanels(AbstractGridScreen<?> screen, GuiGraphics g) {
        int x = screen.getGuiLeft(), y = screen.getGuiTop();
        EditorButton.frame(g, x + 197, y, 31, 82, 0xFFC6C6C6);
        for (int slot = 0; slot < 4; slot++) {
            int sx = x + 204, sy = y + 6 + slot * 18;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFFFFFFFF);
            g.fill(sx - 1, sy - 1, sx + 17, sy, 0xFF373737);
            g.fill(sx - 1, sy - 1, sx, sy + 17, 0xFF373737);
            g.fill(sx, sy, sx + 16, sy + 16, 0xFF8B8B8B);
        }
        EditorButton.frame(g, x, Math.max(2, y - 23), 190, 24, 0xFFC6C6C6);
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
    private static final class CategoryButton extends EditorButton {
        ItemStack icon = new ItemStack(Items.PAPER);
        CategoryButton(int x, int y, OnPress press) { super(x, y, 100, 18, Component.literal("全部物品"), press); }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float tick) {
            frame(g, getX(), getY(), getWidth(), getHeight(), isHoveredOrFocused() ? 0xFFE0E0DA : 0xFFD2D2CC);
            g.renderItem(icon, getX() + 2, getY() + 1);
            var font = Minecraft.getInstance().font;
            String text = EditorText.fit(getMessage().getString(), getWidth() - 24, font::width);
            g.drawString(font, text, getX() + 21, getY() + 5, active ? 0xFF404648 : 0xFF858580, false);
        }
    }
}
