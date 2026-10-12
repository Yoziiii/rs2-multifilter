package dev.rs2multifilter.core;

import java.util.*;

public final class CardEditor {
    private CardEditor() {}

    public static FilterCard add(FilterCard card, String name, String icon, String rulesText) {
        return card.withCategory(new FilterCategory(name, icon, RuleText.parse(rulesText)));
    }
    public static FilterCard replace(FilterCard card, int index, String name, String icon, String rulesText) {
        List<FilterCategory> categories = new ArrayList<>(card.categories());
        categories.set(index, new FilterCategory(name, icon, RuleText.parse(rulesText), categories.get(index).mode()));
        return new FilterCard(FilterCard.VERSION, categories);
    }
    public static FilterCard remove(FilterCard card, int index) {
        List<FilterCategory> categories = new ArrayList<>(card.categories());
        categories.remove(index);
        return new FilterCard(FilterCard.VERSION, categories);
    }
    public static FilterCard move(FilterCard card, int oldIndex, int newIndex) {
        List<FilterCategory> categories = new ArrayList<>(card.categories());
        categories.add(newIndex, categories.remove(oldIndex));
        return new FilterCard(FilterCard.VERSION, categories);
    }
}
