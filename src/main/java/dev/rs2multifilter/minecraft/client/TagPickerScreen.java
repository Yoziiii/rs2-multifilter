package dev.rs2multifilter.minecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.function.Consumer;

public final class TagPickerScreen extends Screen {
    private final Screen previous;
    private final List<String> tags;
    private final Consumer<String> onPick;
    private int page;
    private static final int PAGE_SIZE = 7;

    public TagPickerScreen(Screen previous, List<String> tags, Consumer<String> onPick) {
        super(Component.literal("从副手物品选择标签"));
        this.previous = previous;
        this.tags = List.copyOf(tags);
        this.onPick = onPick;
    }

    @Override protected void init() {
        int x = width / 2 - 150;
        int y = height / 2 - 115;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = page * PAGE_SIZE + i;
            if (index >= tags.size()) break;
            String tag = tags.get(index);
            addRenderableWidget(Button.builder(Component.literal(tag), b -> {
                onPick.accept(tag);
                Minecraft.getInstance().setScreen(previous);
            }).bounds(x, y + i * 25, 300, 22).build());
        }
        addRenderableWidget(Button.builder(Component.literal("上一页"), b -> { page = Math.max(0, page - 1); rebuildWidgets(); }).bounds(x, y + 183, 95, 20).build());
        addRenderableWidget(Button.builder(Component.literal("返回"), b -> onClose()).bounds(x + 101, y + 183, 98, 20).build());
        addRenderableWidget(Button.builder(Component.literal("下一页"), b -> {
            page = Math.min((tags.size() - 1) / PAGE_SIZE, page + 1); rebuildWidgets();
        }).bounds(x + 205, y + 183, 95, 20).build());
    }

    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        renderBackground(g, mx, my, delta);
        g.drawCenteredString(font, "点击标签即可添加到当前过滤分类", width / 2, height / 2 - 134, 0xFFFFFF);
        g.drawCenteredString(font, "第 " + (page + 1) + " / " + Math.max(1, (tags.size() + PAGE_SIZE - 1) / PAGE_SIZE) + " 页", width / 2, height / 2 + 96, 0xAAAAAA);
        super.render(g, mx, my, delta);
    }

    @Override public void onClose() { Minecraft.getInstance().setScreen(previous); }
}
