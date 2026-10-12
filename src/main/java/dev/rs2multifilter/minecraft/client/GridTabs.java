package dev.rs2multifilter.minecraft.client;

import dev.rs2multifilter.core.FilterCard;
import dev.rs2multifilter.minecraft.MultiFilterMod;
import dev.rs2multifilter.minecraft.network.SwapInstalledCardPayload;
import com.refinedmods.refinedstorage.common.grid.screen.AbstractGridScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Physical card slot + narrow item-icon filter tabs, mounted above the RS grid.
 * No buttons are placed in the JEI/EMI region at the right of the screen.
 */
@EventBusSubscriber(modid = MultiFilterMod.ID, value = Dist.CLIENT)
public final class GridTabs {
    private static final int PER_PAGE = 5;
    private static final Map<AbstractGridScreen<?>, TabState> STATES = new WeakHashMap<>();
    private GridTabs() {}

    private record IconButton(Button button, ItemStack icon) {}
    private static final class TabState {
        FilterCard card;
        final GridFilterSession session;
        final List<Button> slotButtons;
        final List<IconButton> icons;
        final Runnable refresh;
        TabState(FilterCard card, GridFilterSession session, List<Button> slotButtons,
                 List<IconButton> icons, Runnable refresh) {
            this.card = card;
            this.session = session;
            this.slotButtons = slotButtons;
            this.icons = icons;
            this.refresh = refresh;
        }
    }

    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof AbstractGridScreen<?> screen) {
            GridFilterSession.detach(screen.getMenu());
            STATES.remove(screen);
            ClientCardState.clear(screen.getMenu().containerId);
        }
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof AbstractGridScreen<?> screen)) return;
        int menuId = screen.getMenu().containerId;
        FilterCard initial = ClientCardState.card(menuId);
        GridFilterSession session = GridFilterSession.attach(screen.getMenu(), initial);
        final FilterCard[] current = {initial};
        final int x = screen.getGuiLeft() + 8;
        final int y = Math.max(2, screen.getGuiTop() - 23);
        final int[] page = {0};
        final Runnable[] refresh = {() -> {}};

        // Classic RS used four vertical filter slots to the right of the grid.
        // Match the original x=204 and y=6+18*slot GUI coordinates.
        List<Button> slotButtons = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            final int cardSlot = i;
            Button slot = Button.builder(Component.literal(""), b ->
                PacketDistributor.sendToServer(new SwapInstalledCardPayload(menuId, cardSlot))
            ).bounds(screen.getGuiLeft() + 204, screen.getGuiTop() + 6 + i * 18, 18, 18)
             .tooltip(Tooltip.create(Component.literal("旧版过滤槽 " + (i + 1) + "：拿着卡点击插入，空手点击取回"))).build();
            slotButtons.add(slot);
            event.addListener(slot);
        }

        Button all = Button.builder(Component.literal("全"), b -> {
            session.select(-1);
            refresh[0].run();
        }).bounds(x, y, 20, 20).tooltip(Tooltip.create(Component.literal("全部物品"))).build();
        event.addListener(all);
        List<Button> tabs = new ArrayList<>();
        List<IconButton> icons = new ArrayList<>();
        for (int i = 0; i < PER_PAGE; i++) {
            final int slotIndex = i;
            Button button = Button.builder(Component.empty(), b -> {
                int selected = page[0] * PER_PAGE + slotIndex;
                if (selected < current[0].categories().size()) {
                    session.select(selected);
                    refresh[0].run();
                }
            }).bounds(x + 21 * (i + 1), y, 20, 20).build();
            tabs.add(button);
            icons.add(new IconButton(button, new ItemStack(Items.PAPER)));
            event.addListener(button);
        }

        Button previous = Button.builder(Component.literal("‹"), b -> {
            page[0] = Math.max(0, page[0] - 1);
            refresh[0].run();
        }).bounds(x + 126, y, 18, 20).build();
        Button next = Button.builder(Component.literal("›"), b -> {
            page[0] = Math.min(Math.max(0, (current[0].categories().size() - 1) / PER_PAGE), page[0] + 1);
            refresh[0].run();
        }).bounds(x + 145, y, 18, 20).build();
        event.addListener(previous);
        event.addListener(next);

        refresh[0] = () -> {
            int total = current[0].categories().size();
            for (int i = 0; i < slotButtons.size(); i++) {
                slotButtons.get(i).setMessage(Component.literal(ClientCardState.installed(menuId, i) ? "" : "+"));
            }
            all.setMessage(Component.literal(session.selected() < 0 ? "●" : "全"));
            for (int i = 0; i < PER_PAGE; i++) {
                int idx = page[0] * PER_PAGE + i;
                Button button = tabs.get(i);
                button.visible = idx < total;
                if (idx < total) {
                    var category = current[0].categories().get(idx);
                    button.setMessage(Component.literal(session.selected() == idx ? "•" : ""));
                    button.setTooltip(Tooltip.create(Component.literal(category.name())));
                    icons.set(i, new IconButton(button, iconFor(category.iconItemId())));
                }
            }
            previous.active = page[0] > 0;
            next.active = (page[0] + 1) * PER_PAGE < total;
        };
        refresh[0].run();
        STATES.put(screen, new TabState(initial, session, slotButtons, icons, refresh[0]));
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

        int menuId = screen.getMenu().containerId;
        FilterCard latest = ClientCardState.card(menuId);
        if (!latest.equals(state.card)) {
            state.card = latest;
            state.session.updateCard(latest);
            state.refresh.run();
        }
        for (int i = 0; i < state.slotButtons.size(); i++) {
            Button slot = state.slotButtons.get(i);
            boolean occupied = ClientCardState.installed(menuId, i);
            slot.setMessage(Component.literal(occupied ? "" : "+"));
            if (occupied) {
                event.getGuiGraphics().renderItem(new ItemStack(MultiFilterMod.FILTER_CARD.get()),
                    slot.getX() + 1, slot.getY() + 1);
            }
        }
        for (IconButton icon : state.icons) {
            if (icon.button().visible)
                event.getGuiGraphics().renderItem(icon.icon(), icon.button().getX() + 2, icon.button().getY() + 2);
        }
    }
}
