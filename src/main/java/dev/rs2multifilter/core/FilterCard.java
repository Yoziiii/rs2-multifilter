package dev.rs2multifilter.core;

import java.util.List;

public record FilterCard(int dataVersion, List<FilterCategory> categories) {
    public static final int VERSION = 1;
    public static final int MAX_CATEGORIES = 64;

    public FilterCard {
        if (dataVersion != VERSION) throw new IllegalArgumentException("Unsupported card data version " + dataVersion);
        categories = List.copyOf(categories);
        if (categories.size() > MAX_CATEGORIES) throw new IllegalArgumentException("Too many categories");
    }

    public static FilterCard blank() { return new FilterCard(VERSION, List.of()); }
    public FilterCard copy() { return new FilterCard(dataVersion, categories); }
    public FilterCard withCategory(FilterCategory category) {
        var copy = new java.util.ArrayList<>(categories);
        copy.add(category);
        return new FilterCard(VERSION, copy);
    }
}
