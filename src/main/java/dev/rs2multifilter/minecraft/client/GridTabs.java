package dev.rs2multifilter.minecraft.client;

import dev.rs2multifilter.core.FilterCard;
import dev.rs2multifilter.minecraft.CardComponents;
import dev.rs2multifilter.minecraft.MultiFilterMod;
import com.refinedmods.refinedstorage.common.grid.screen.AbstractGridScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = MultiFilterMod.ID, value = Dist.CLIENT)
public final class GridTabs {
    private GridTabs() {}

    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof AbstractGridScreen<?> screen) {
            GridFilterSession.detach(screen.getMenu());
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
                if (!candidate.categories().isEmpty()) { card = candidate; break; }
            }
        }
        if (card == null) return;
        final FilterCard selectedCard = card;
        var session = GridFilterSession.attach(screen.getMenu(), selectedCard);
        final int x = Math.max(2, screen.width - 91);
        final int y = Math.max(5, screen.height / 2 - 84);
        final int[] page = {0};
        final int pageSize = 6;
        final int total = selectedCard.categories().size();
        var buttons = new java.util.ArrayList<Button>();
        final Runnable[] refresh = { () -> {} };
        for (int slot = 0; slot < pageSize; slot++) {
            final int indexOnPage = slot;
            Button b = Button.builder(Component.literal(""), btn -> {
                int categoryIndex = page[0] * pageSize + indexOnPage;
                if (categoryIndex < total) { session.select(categoryIndex); refresh[0].run(); }
            }).bounds(x, y + 22 + slot * 21, 89, 20).build();
            buttons.add(b);
            event.addListener(b);
        }
        Button all = Button.builder(Component.literal("全部"), b -> { session.select(-1); refresh[0].run(); }).bounds(x, y, 89, 20).build();
        event.addListener(all);
        refresh[0] = () -> {
            all.setMessage(Component.literal(session.selected() < 0 ? "» 全部" : "全部"));
            for (int i = 0; i < pageSize; i++) {
                int index = page[0] * pageSize + i;
                buttons.get(i).visible = index < total;
                if (index < total) {
                    String title = selectedCard.categories().get(index).name();
                    buttons.get(i).setMessage(Component.literal((session.selected() == index ? "» " : "") + title));
                }
            }
        };
        Button back = Button.builder(Component.literal("<"), b -> {
            page[0] = Math.max(0, page[0] - 1); refresh[0].run();
        }).bounds(x, y + 22 + pageSize * 21, 43, 20).build();
        Button forward = Button.builder(Component.literal(">"), b -> {
            page[0] = Math.min((total - 1) / pageSize, page[0] + 1); refresh[0].run();
        }).bounds(x + 46, y + 22 + pageSize * 21, 43, 20).build();
        event.addListener(back);
        event.addListener(forward);
        refresh[0].run();
    }
}
