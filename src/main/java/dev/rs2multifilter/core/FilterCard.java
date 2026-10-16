package dev.rs2multifilter.core;

import java.util.List;

public record FilterCard(int dataVersion, List<FilterCategory> categories) {
    public static final int VERSION = 2;
    public static final int MAX_CATEGORIES = 256;
    public static final int MAX_PER_CARD = 64;

    public FilterCard {
        if (dataVersion != VERSION) throw new IllegalArgumentException("Unsupported card data version " + dataVersion);
        categories = List.copyOf(categories);
        if (categories.size() > MAX_CATEGORIES) throw new IllegalArgumentException("Too many categories");
    }

    public static FilterCard blank() { return new FilterCard(VERSION, List.of()); }
    /** Editor-only draft initialization; persisted empty cards keep their format and meaning. */
    public FilterCard forEditing() {
        return categories.isEmpty()
            ? withCategory(new FilterCategory("默认分类", "minecraft:paper", List.of())) : this;
    }
    public FilterCard copy() { return new FilterCard(dataVersion, categories); }
    public FilterCard withCategory(FilterCategory category) {
        var copy = new java.util.ArrayList<>(categories);
        if (categories.size() >= MAX_PER_CARD) throw new IllegalArgumentException("卡片最多 64 个分类");
        copy.add(category);
        return new FilterCard(VERSION, copy);
    }
}
