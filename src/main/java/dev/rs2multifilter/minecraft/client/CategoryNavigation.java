package dev.rs2multifilter.minecraft.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** Display-only paging. -1 is All; custom indices are never shifted or rewritten. */
public final class CategoryNavigation {
    public static final int GAP = 2, ARROWS = 34;
    public record Tab(int category, int x, int width) {}
    private final List<List<Tab>> pages;
    private final int width;

    public CategoryNavigation(List<String> names, ToIntFunction<String> textWidth, int width) {
        if (width < 112) throw new IllegalArgumentException("Navigation is too narrow");
        this.width = width;
        int allWidth = Math.max(28, Math.min(36, textWidth.applyAsInt("全部") + 10));
        int[] sizes = names.stream().mapToInt(n -> Math.max(34, Math.min(56, textWidth.applyAsInt(n) + 24))).toArray();
        int total = allWidth;
        for (int size : sizes) total += GAP + size;
        int limit = width - (total > width ? ARROWS : 0);
        var built = new ArrayList<List<Tab>>();
        var tabs = new ArrayList<Tab>();
        tabs.add(new Tab(-1, 0, allWidth));
        int x = allWidth + GAP;
        for (int i = 0; i < sizes.length; i++) {
            int size = Math.min(sizes[i], limit - allWidth - GAP);
            if (x + size > limit && tabs.size() > 1) {
                built.add(List.copyOf(tabs));
                tabs = new ArrayList<>();
                tabs.add(new Tab(-1, 0, allWidth));
                x = allWidth + GAP;
            }
            tabs.add(new Tab(i, x, size));
            x += size + GAP;
        }
        built.add(List.copyOf(tabs));
        pages = List.copyOf(built);
    }
    public int count() { return pages.size(); }
    public boolean paginated() { return count() > 1; }
    public int clamp(int page) { return Math.max(0, Math.min(page, count() - 1)); }
    public List<Tab> tabs(int page) { return pages.get(clamp(page)); }
    public int previousX() { return width - 32; }
    public int nextX() { return width - 16; }
    public int pageFor(int category) {
        for (int i = 0; i < pages.size(); i++)
            for (Tab tab : pages.get(i)) if (tab.category == category) return i;
        return 0;
    }
}
