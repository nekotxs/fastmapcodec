package dev.neko.fastmapcodec;

import com.mojang.datafixers.util.Pair;
import com.mojang.datafixers.util.Unit;
import com.mojang.serialization.*;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.Map;
import java.util.Optional;



/**
 * Drop-in replacement for Codec.unboundedMap() that uses a real hash map
 * internally instead of DataFixerUpper's Object2ObjectArrayMap-backed
 * BaseMapCodec, avoiding the O(n^2) blowup on large maps.
 */
public final class FastUnboundedMapCodec<K, V> implements Codec<Map<K, V>> {
    private final Codec<K> keyCodec;
    private final Codec<V> valueCodec;

    public FastUnboundedMapCodec(Codec<K> keyCodec, Codec<V> valueCodec) {
        this.keyCodec = keyCodec;
        this.valueCodec = valueCodec;
    }

    @Override
    public <T> DataResult<Pair<Map<K, V>, T>> decode(DynamicOps<T> ops, T input) {
        return ops.getMap(input).flatMap(mapLike -> {
            Object2ObjectMap<K, V> read = new Object2ObjectOpenHashMap<>();
            DataResult<Unit> result = DataResult.success(Unit.INSTANCE);
            for (Pair<T, T> pair : (Iterable<Pair<T, T>>) mapLike.entries()::iterator) {
                DataResult<K> key = keyCodec.parse(ops, pair.getFirst());
                DataResult<V> value = valueCodec.parse(ops, pair.getSecond());
                Optional<K> k = key.result();
                Optional<V> v = value.result();
                if (k.isPresent() && v.isPresent()) {
                    read.put(k.get(), v.get());
                }
            }
            return result.map(u -> Pair.of((Map<K, V>) read, input));
        });
    }

    @Override
    public <T> DataResult<T> encode(Map<K, V> input, DynamicOps<T> ops, T prefix) {
        var mapBuilder = ops.mapBuilder();
        for (Map.Entry<K, V> entry : input.entrySet()) {
            mapBuilder.add(keyCodec.encodeStart(ops, entry.getKey()),
                            valueCodec.encodeStart(ops, entry.getValue()));
        }
        return mapBuilder.build(prefix);
    }
}
