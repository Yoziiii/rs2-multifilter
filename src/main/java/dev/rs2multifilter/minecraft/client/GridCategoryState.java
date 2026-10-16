package dev.rs2multifilter.minecraft.client;

import dev.rs2multifilter.core.FilterCard;

/** Wait for all four cards before restoring; never restore from partial packets. */
public final class GridCategoryState {
    private final GridPreferences preferences;
    private final String scope;
    private FilterCard card = FilterCard.blank();
    private int selected = -1;
    private boolean initialized;

    public GridCategoryState(GridPreferences preferences, String scope) {
        this.preferences = preferences;
        this.scope = scope;
    }

    public void synchronize(FilterCard latest, boolean complete) {
        if (!complete || (initialized && card.equals(latest))) return;
        card = latest;
        selected = initialized ? -1 : preferences.restore(scope, card);
        initialized = true;
    }

    public FilterCard card() { return card; }
    public int selected() { return selected; }
    public void choose(int index) {
        if (index < -1 || index >= card.categories().size()) throw new IndexOutOfBoundsException(index);
        selected = index;
        if (initialized) preferences.record(scope, card, selected);
    }
    public void cycle(int direction) {
        choose(Math.floorMod(selected + 1 + direction, card.categories().size() + 1) - 1);
    }
    public boolean remembersCategory() { return preferences.remembersCategory(); }
    public void toggleRemember() {
        preferences.setRemember(!preferences.remembersCategory());
        rememberCurrent();
    }
    public void rememberCurrent() {
        if (initialized) preferences.record(scope, card, selected);
    }
}
