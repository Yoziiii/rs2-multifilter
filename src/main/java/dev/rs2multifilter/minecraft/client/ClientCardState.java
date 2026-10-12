package dev.rs2multifilter.minecraft.client;

import dev.rs2multifilter.core.CardWireCodec;
import dev.rs2multifilter.core.FilterCard;
import java.util.HashMap;
import java.util.Map;

/** Keeps server-supplied configuration by current menu ID; the terminal is authoritative. */
public final class ClientCardState {
    private static final Map<Integer, String> SYNCED = new HashMap<>();
    private ClientCardState() {}

    public static void update(int menuId, String data) {
        if (menuId < 0 || menuId > 255) return;
        if (data.length() > CardWireCodec.MAX_ENCODED_CHARS) return;
        SYNCED.put(menuId, data);
    }
    public static boolean hasSync(int menuId) { return SYNCED.containsKey(menuId); }
    public static boolean installed(int menuId) { return !SYNCED.getOrDefault(menuId, "").isEmpty(); }
    public static FilterCard card(int menuId) {
        try { return CardWireCodec.decode(SYNCED.getOrDefault(menuId, "")); }
        catch (RuntimeException ex) { return FilterCard.blank(); }
    }
    public static void clear(int menuId) { SYNCED.remove(menuId); }
    public static void clearAll() { SYNCED.clear(); }
}
