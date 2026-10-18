package dev.rs2multifilter.minecraft.client;

import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.List;
import java.util.function.IntConsumer;

/** One current option, as in Create. Scrolling never performs the add action. */
public final class AttributeSelector extends RuleEntryButton {
    public record Entry(Component title, String clipboard, Component tooltip) {}
    private List<Entry> options = List.of();
    private int selected;
    private final IntConsumer changed;

    public AttributeSelector(int x, int y, int w, int h,
                             java.util.function.Consumer<String> copy, IntConsumer changed) {
        super(x, y, w, h, Component.literal("从背包选择参考物品"), b -> {}, "", copy);
        this.changed = changed;
    }

    public int selectedIndex() { return selected; }
    public int optionCount() { return options.size(); }

    public void setOptions(List<Entry> options, int index) {
        this.options = List.copyOf(options);
        selected = Math.max(0, Math.min(index, options.size() - 1));
        active = !options.isEmpty();
        updateOption();
    }

    private void updateOption() {
        if (options.isEmpty()) {
            setMessage(Component.literal("从背包选择参考物品"));
            setTooltip(Tooltip.create(Component.literal("点击背包物品，仅选择一个幽灵样本，不消耗物品")));
            setClipboardValue("");
            return;
        }
        Entry option = options.get(selected);
        setMessage(option.title());
        setClipboardValue(option.clipboard());
        setTooltip(null);
    }

    public List<Component> hoverLines(Component sampleName, int visibleEntries) {
        if (!active || !visible || options.isEmpty()) return List.of();
        int count = Math.max(1, Math.min(options.size(), visibleEntries));
        int start = Math.max(0, Math.min(selected - count / 2, options.size() - count));
        var lines = new java.util.ArrayList<Component>();
        lines.add(sampleName.copy().append(" · " + (selected + 1) + "/" + options.size()).withStyle(ChatFormatting.BLUE));
        if (start > 0) lines.add(Component.literal("> …").withStyle(ChatFormatting.DARK_GRAY));
        for (int i = start; i < start + count; i++)
            lines.add(Component.literal(i == selected ? "-> " : "> ").append(options.get(i).title())
                .withStyle(i == selected ? ChatFormatting.WHITE : ChatFormatting.GRAY));
        if (start + count < options.size()) lines.add(Component.literal("> …").withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.literal("滚动选择").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        return List.copyOf(lines);
    }

    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (!active || !visible || !isMouseOver(x, y) || dy == 0) return false;
        int next = Math.max(0, Math.min(options.size() - 1, selected + (dy < 0 ? 1 : -1)));
        if (next != selected) {
            selected = next;
            updateOption();
            changed.accept(selected);
        }
        // Consume at either end too: never scroll an underlying inventory/overlay.
        return true;
    }
}
