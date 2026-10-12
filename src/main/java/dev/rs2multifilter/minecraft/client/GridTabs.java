package dev.rs2multifilter.minecraft.client;

import dev.rs2multifilter.core.FilterCard;
import dev.rs2multifilter.minecraft.CardComponents;
import dev.rs2multifilter.minecraft.MultiFilterMod;
import com.refinedmods.refinedstorage.common.grid.screen.AbstractGridScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Slim item-icon tabs mounted directly above the grid, INSIDE its horizontal extent.
 * This avoids JEI/EMI panels on the right without requiring their APIs.
 */
@EventBusSubscriber(modid = MultiFilterMod.ID, value = Dist.CLIENT)
public final class GridTabs {
    private static final int PER_PAGE = 5;
    private static final Map<AbstractGridScreen<?>, TabState> STATES = new WeakHashMap<>();

    private GridTabs() {}

    private record IconButton(Button button, ItemStack icon) {}
    private record TabState(List<IconButton> icons) {}

    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof AbstractGridScreen<?> screen) {
            GridFilterSession.detach(screen.getMenu());
            STATES.remove(screen);
        }
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof AbstractGridScreen<?> screen)) return;
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        FilterCard card = null;
        for (var item : player.getInventory().items) {
            if (item.is(MultiFilterMod.FILTER_CARD.get())) {
                FilterCard candidate = CardComponents.read(item);
                if (!candidate.categories().isEmpty()) {
                    card = candidate;
                    break;
                }
            }
        }
        if (card == null) return;

        FilterCard selectedCard = card;
        GridFilterSession session = GridFilterSession.attach(screen.getMenu(), card);
        final int left = screen.getGuiLeft() + 8;
        final int top = Math.max(2, screen.getGuiTop() - 23);
        final int[] page = {0};
        final int total = selectedCard.categories().size();
        final List<Button> tabs = new ArrayList<>();
        final List<IconButton> iconButtons = new ArrayList<>();
        final Runnable[] refresh = {() -> {}};

        Button all = Button.builder(Component.literal("全"), b -> {
            session.select(-1);
            refresh[0].run();
        }).bounds(left, top, 20, 20).tooltip(Tooltip.create(Component.literal("全部物品"))).build();
        event.addListener(all);

        for (int i = 0; i < PER_PAGE; i++) {
            final int slot = i;
            Button b = Button.builder(Component.empty(), button -> {
                int selected = page[0] * PER_PAGE + slot;
                if (selected < total) {
                    session.select(selected);
                    refresh[0].run();
                }
            }).bounds(left + 21 * (i + 1), top, 20, 20).build();
            tabs.add(b);
            iconButtons.add(new IconButton(b, new ItemStack(Items.PAPER)));
            event.addListener(b);
        }

        Button previous = Button.builder(Component.literal("‹"), b -> {
            page[0] = Math.max(0, page[0] - 1);
            refresh[0].run();
        }).bounds(left + 126, top, 18, 20).build();
        Button next = Button.builder(Component.literal("›"), b -> {
            page[0] = Math.min((total - 1) / PER_PAGE, page[0] + 1);
            refresh[0].run();
        }).bounds(left + 145, top, 18, 20).build();
        event.addListener(previous);
        event.addListener(next);

        refresh[0] = () -> {
            all.setMessage(Component.literal(session.selected() == -1 ? "●" : "全"));
            for (int i = 0; i < PER_PAGE; i++) {
                int idx = page[0] * PER_PAGE + i;
                Button b = tabs.get(i);
                b.visible = idx < total;
                if (idx < total) {
                    var category = selectedCard.categories().get(idx);
                    b.setMessage(Component.literal(session.selected() == idx ? "•" : ""));
                    b.setTooltip(Tooltip.create(Component.literal(category.name())));
                    iconButtons.set(i, new IconButton(b, iconFor(category.iconItemId())));
                }
            }
            previous.active = page[0] > 0;
            next.active = (page[0] + 1) * PER_PAGE < total;
        };
        refresh[0].run();
        STATES.put(screen, new TabState(iconButtons));
    }

    private static ItemStack iconFor(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) return new ItemStack(Items.PAPER);
        return new ItemStack(BuiltInRegistries.ITEM.get(key));
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractGridScreen<?> screen)) return;
        TabState state = STATES.get(screen);
        if (state == null) return;
        for (IconButton icon : state.icons()) {
            if (icon.button().visible) {
                event.getGuiGraphics().renderItem(icon.icon(), icon.button().getX() + 2, icon.button().getY() + 2);
            }
        }
    }
}
