package dev.neko.fastmapcodec;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Drop-in replacement for the Codec produced by {@code Codec.unboundedMap()}
 * (i.e. {@code com.mojang.serialization.codecs.UnboundedMapCodec}, which
 * delegates decode() to {@code BaseMapCodec}'s default method).
 * <p>
 * The vanilla implementation accumulates decoded entries into an
 * {@code Object2ObjectArrayMap} (fastutil), a structure backed by two
 * parallel arrays where every {@code putIfAbsent} does a linear scan
 * (equals() comparisons) over all previously inserted entries. Building a
 * map of N entries this way costs O(N^2) total comparisons -- fine for the
 * handful-of-fields maps most Codecs decode, catastrophic for large,
 * player-controlled data (e.g. PlayerAdvancements on modpacks with tens of
 * thousands of advancement entries: multi-second main-thread stalls on
 * every join).
 * <p>
 * This class swaps the accumulator for {@code Object2ObjectOpenHashMap}
 * (O(1) amortized insertion), while preserving the original semantics:
 * <ul>
 *   <li>Duplicate keys are detected via {@code putIfAbsent} (matching
 *       vanilla's "Duplicate entry for key" behavior) rather than silently
 *       overwritten.</li>
 *   <li>Null keys/values from a misbehaving Codec fail fast rather than
 *       being silently inserted.</li>
 *   <li>The returned map is wrapped as unmodifiable (O(1) view, not a full
 *       O(n) copy like the vanilla path's {@code ImmutableMap.copyOf}) so
 *       callers can't accidentally mutate it.</li>
 * </ul>
 * Encoding is delegated to a straightforward per-entry serialization; it
 * was never the bottleneck (advancement files are read far more often than
 * written from scratch), so no special-casing is done there.
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
        return ops.getMap(input).flatMap(mapLike -> decodeMap(ops, mapLike, input));
    }

    private <T> DataResult<Pair<Map<K, V>, T>> decodeMap(DynamicOps<T> ops, MapLike<T> mapLike, T input) {
        Object2ObjectMap<K, V> read = new Object2ObjectOpenHashMap<>();
        List<Pair<T, T>> failed = new ArrayList<>();

        for (Pair<T, T> pair : (Iterable<Pair<T, T>>) mapLike.entries()::iterator) {
            DataResult<K> keyResult = keyCodec.parse(ops, pair.getFirst());
            DataResult<V> valueResult = valueCodec.parse(ops, pair.getSecond());

            Optional<K> k = keyResult.result();
            Optional<V> v = valueResult.result();

            if (k.isPresent() && v.isPresent()) {
                K key = k.get();
                V value = v.get();
                if (key == null || value == null) {
                    // Should be unreachable -- a well-behaved Codec never
                    // decodes to a present-but-null Optional. Fail fast
                    // rather than silently letting null into the map, since
                    // we no longer route through Guava's null-hostile
                    // ImmutableMap.
                    throw new NullPointerException(
                        "Codec produced a present Optional wrapping null (key=" + key + ", value=" + value + ")");
                }
                V existing = read.putIfAbsent(key, value);
                if (existing != null) {
                    // Matches vanilla: a repeated key is a decode error, not
                    // a silent overwrite.
                    failed.add(pair);
                }
            } else {
                failed.add(pair);
            }
        }

        Map<K, V> elements = Collections.unmodifiableMap(read); // O(1) view, not a copy
        Pair<Map<K, V>, T> success = Pair.of(elements, input);

        if (failed.isEmpty()) {
            return DataResult.success(success);
        }
        T errors = ops.createMap(failed.stream());
        return DataResult.error(() -> "Duplicate entry or decode failure, missed input: " + errors, success);
    }

    @Override
    public <T> DataResult<T> encode(Map<K, V> input, DynamicOps<T> ops, T prefix) {
        var mapBuilder = ops.mapBuilder();
        for (Map.Entry<K, V> entry : input.entrySet()) {
            mapBuilder.add(
                keyCodec.encodeStart(ops, entry.getKey()),
                valueCodec.encodeStart(ops, entry.getValue())
            );
        }
        return mapBuilder.build(prefix);
    }
}
