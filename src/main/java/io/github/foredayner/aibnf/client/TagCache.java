package io.github.foredayner.aibnf.client;

import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.world.item.component.CustomData;

/**
 * Remembers something worked out from an item's data, by that exact data object (not its
 * contents: comparing a big model's bytes every frame would cost more than the cache saves).
 * A stack whose data is replaced gets worked out again.
 */
final class TagCache<V> {

    private final Map<Integer, Map.Entry<WeakReference<CustomData>, V>> map;

    TagCache(int limit) {
        this.map = new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, Map.Entry<WeakReference<CustomData>, V>> eldest) {
                return size() > limit;
            }
        };
    }

    V get(CustomData tag, Supplier<V> make) {
        int key = System.identityHashCode(tag);
        Map.Entry<WeakReference<CustomData>, V> hit = map.get(key);
        if (hit != null && hit.getKey().get() == tag) {
            return hit.getValue();
        }
        V value = make.get();
        map.put(key, new java.util.AbstractMap.SimpleImmutableEntry<>(new WeakReference<>(tag), value));
        return value;
    }
}
