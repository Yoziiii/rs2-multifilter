package dev.rs2multifilter.minecraft.client;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

/** Only recreatable attribute/sample objects; never card data or predicate results. */
public final class AttributeCaches {
    private static WeakReference<Level> world = new WeakReference<>(null);
    private static WeakReference<Object> registries = new WeakReference<>(null);
    private AttributeCaches() {}

    public static <K,V> Map<K,V> bounded(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Cache capacity must be positive");
        return Collections.synchronizedMap(new LinkedHashMap<K,V>(16, .75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
                return size() > capacity;
            }
        });
    }

    /** Identity checks are constant work; no config hashing or attribute parsing on a hit. */
    public static synchronized Level level() {
        var client = Minecraft.getInstance();
        Level current = client == null ? null : client.level;
        Object access = current == null ? null : current.registryAccess();
        if (world.get() != current || registries.get() != access) {
            invalidate();
            world = new WeakReference<>(current);
            registries = new WeakReference<>(access);
        }
        return current;
    }
    static synchronized void invalidate() {
        CreateAttributeBridge.clearCache();
        ExactItemBridge.clearCache();
        world.clear();
        registries.clear();
    }
    static synchronized void unload(Object level) {
        if (world.get() == level) invalidate();
    }
}
