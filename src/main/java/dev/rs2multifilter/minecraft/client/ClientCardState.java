package dev.rs2multifilter.minecraft.client;

import dev.rs2multifilter.core.CardWireCodec;
import dev.rs2multifilter.core.FilterCard;
import dev.rs2multifilter.core.FilterCategory;
import dev.rs2multifilter.minecraft.storage.InstalledCardHolder;
import java.util.*;

/** Four synchronized installed cards, with flattened tabs in physical slot order. */
public final class ClientCardState {
    private static final Map<Integer, String[]> SYNCED = new HashMap<>();
    private ClientCardState() {}

    public static void update(int menuId, int slot, String data) {
        if (menuId < 0 || menuId > 255 || slot < 0 || slot >= InstalledCardHolder.SLOT_COUNT ||
            data == null || data.length() > CardWireCodec.MAX_ENCODED_CHARS) return;
        SYNCED.computeIfAbsent(menuId, ignored -> new String[InstalledCardHolder.SLOT_COUNT])[slot] = data;
    }
    public static boolean installed(int menuId, int slot) {
        String[] slots = SYNCED.get(menuId);
        return slots != null && slots[slot] != null && !slots[slot].isEmpty();
    }
    public static boolean hasSync(int menuId) { return SYNCED.containsKey(menuId); }
    public static FilterCard card(int menuId) {
        String[] slots = SYNCED.get(menuId);
        if (slots == null) return FilterCard.blank();
        List<FilterCategory> merged = new ArrayList<>();
        for (String slot : slots) {
            if (slot == null || slot.isEmpty()) continue;
            try { merged.addAll(CardWireCodec.decode(slot).categories()); }
            catch (RuntimeException ignored) { }
        }
        // The editor may store 64 categories on each card; a screen is allowed
        // to show all card categories even if there are more than 64 combined.
        return new FilterCard(FilterCard.VERSION, merged.subList(0,
            Math.min(merged.size(), FilterCard.MAX_CATEGORIES)));
    }
    public static void clear(int menuId) { SYNCED.remove(menuId); }
    public static void clearAll() { SYNCED.clear(); }
}