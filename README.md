# fastmapcodec

Fixes TPS freeze on player join caused by large advancement files (and the
same class of freeze anywhere else in the game, or in other mods, that decodes
a large map through DFU).

Fixes O(N²) behavior in Mojang's `BaseMapCodec` (part of `com.mojang:datafixerupper`)
when decoding large map-based Codecs. Since the fix lives in `BaseMapCodec` itself,
it applies to every codec built on top of it - `UnboundedMapCodec`,
`SimpleMapCodec`, and anything else sharing the same `decode`/`encode` logic -
not just player advancements.

Versions affected: 1.21 and higher.

Upstream PR: [Mojang/DataFixerUpper#110](https://github.com/Mojang/DataFixerUpper/pull/110)
Benchmarks: [nekotxs/BaseMapCodec-bench](https://github.com/nekotxs/BaseMapCodec-bench)

## Background

Since [e0b245e](https://github.com/Mojang/DataFixerUpper/commit/e0b245ea8e5a8e86b0ac7aedc0cb3f260ad6c6bc),
`BaseMapCodec.decode()` accumulates entries into an `Object2ObjectArrayMap`,
which does a linear scan on every insert - O(N²) total for N entries. Fine
for the small maps that make up most Codec usage in vanilla, but nothing
bounds N, and large player-controlled data (e.g. a heavily modded
advancement tree) can push it into the tens of thousands, costing seconds of
main-thread freeze on join.

In my case, it caused 10–15 seconds of server lag each time anyone joined a moderately modded NeoForge server.

The fix: try `ImmutableMap.Builder.buildOrThrow()` first (fastest at every N
in benchmarks); on failure (duplicate key - rare, ~0.004% of calls on a
modded server, unobserved on vanilla), fall back to
`Object2ObjectOpenHashMap` to recover partial results. Preserves the
original duplicate-key / partial-error-handling semantics. Full
methodology and comparison against alternatives in the bench repo linked above.


## Building

```
./gradlew neoforge:build   # neoforge/build/libs/
./gradlew fabric:build     # fabric/build/libs/
```

## License / attribution

The `decode()` implementation adapts code from
`com.mojang.serialization.codecs.BaseMapCodec` in
[Mojang/DataFixerUpper](https://github.com/Mojang/DataFixerUpper), MIT-licensed.

`neoforge/src/main/java/.../service/ModuleLayerMigrator.java`,
`FastMapCodecModLocator.java`, and `ConnectorUtil.java` adapt code from
[Sinytra/Connector](https://github.com/Sinytra/Connector), MIT-licensed.
