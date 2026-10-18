package dev.rs2multifilter.minecraft.client;

import dev.rs2multifilter.core.CardWireCodec;
import dev.rs2multifilter.core.FilterCard;
import dev.rs2multifilter.core.FilterCategory;
import dev.rs2multifilter.core.CardReadResult;
import dev.rs2multifilter.minecraft.storage.InstalledCardHolder;
import java.util.*;

/** Four synchronized installed cards, with flattened tabs in physical slot order. */
public final class ClientCardState {
    private static final FilterCard EMPTY = FilterCard.blank();
    private static final Map<Integer, Snapshot> SYNCED = new HashMap<>();
    private static final Map<Integer, Integer> REVISIONS = new HashMap<>();
    private ClientCardState() {}
    /** Scoped to one live client menu; raw data is retained even when unreadable. */
    private static final class Snapshot {
        final String[] raw = new String[InstalledCardHolder.SLOT_COUNT];
        final CardReadResult[] parsed = new CardReadResult[InstalledCardHolder.SLOT_COUNT];
        final Source[] sources = new Source[InstalledCardHolder.SLOT_COUNT];
        FilterCard merged;
        int received;

        FilterCard slot(int index) {
            if (raw[index] == null || raw[index].isEmpty()) return EMPTY;
            if (parsed[index] == null) {
                parsed[index] = CardReadResult.inspect(raw[index]);
                if (parsed[index].readable()) sources[index] = new Source(index, parsed[index].requireCard());
            }
            return parsed[index].readable() ? parsed[index].requireCard() : EMPTY;
        }
        FilterCard card() {
            if (merged == null) {
                List<FilterCategory> categories = new ArrayList<>();
                for (int index = 0; index < raw.length; index++) categories.addAll(slot(index).categories());
                merged = new FilterCard(FilterCard.VERSION, categories.subList(0,
                    Math.min(categories.size(), FilterCard.MAX_CATEGORIES)));
            }
            return merged;
        }
    }

    public static void update(int menuId, int slot, String data) {
        if (menuId < 0 || menuId > 255 || slot < 0 || slot >= InstalledCardHolder.SLOT_COUNT ||
            data == null || data.length() > CardWireCodec.MAX_ENCODED_CHARS) return;
        var snapshot = SYNCED.computeIfAbsent(menuId, ignored -> new Snapshot());
        if (Objects.equals(snapshot.raw[slot], data)) return;
        snapshot.raw[slot] = data;
        snapshot.parsed[slot] = null;
        snapshot.sources[slot] = null;
        snapshot.merged = null;
        snapshot.received |= 1 << slot;
        REVISIONS.merge(menuId, 1, Integer::sum);
    }
    public static boolean installed(int menuId, int slot) {
        var snapshot = SYNCED.get(menuId);
        return snapshot != null && snapshot.raw[slot] != null && !snapshot.raw[slot].isEmpty();
    }
    public static boolean hasSync(int menuId) { return SYNCED.containsKey(menuId); }
    public static boolean hasCompleteSync(int menuId) {
        var snapshot = SYNCED.get(menuId);
        return snapshot != null && snapshot.received == (1 << InstalledCardHolder.SLOT_COUNT) - 1;
    }
    public static FilterCard card(int menuId) {
        var snapshot = SYNCED.get(menuId);
        return snapshot == null ? EMPTY : snapshot.card();
    }
    public static void clear(int menuId) { SYNCED.remove(menuId); REVISIONS.remove(menuId); }
    public static int revision(int menuId) { return REVISIONS.getOrDefault(menuId,0); }
    /** Physical ownership for runtime fault isolation. Does not change tab ordering or stored data. */
    public record Source(int slot, FilterCard card) {}
    public static Source source(int menuId, int category) {
        var snapshot = SYNCED.get(menuId);
        if (snapshot == null || category < 0) return null;
        int offset=0;
        for (int slot = 0; slot < InstalledCardHolder.SLOT_COUNT; slot++) {
            FilterCard card = snapshot.slot(slot);
            if (category < offset + card.categories().size()) return snapshot.sources[slot];
            offset += card.categories().size();
        }
        return null;
    }
    public static void clearAll() { SYNCED.clear(); REVISIONS.clear(); }
}
