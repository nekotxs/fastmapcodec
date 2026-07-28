# fastmapcodec

Fixes O(n^2) behavior in Mojang's `BaseMapCodec` (part of `com.mojang:datafixerupper`,
used internally by `Codec.unboundedMap`) when decoding large map-based Codecs.

## Background

`BaseMapCodec.decode` accumulates decoded entries into an `Object2ObjectArrayMap`
(fastutil), which is backed by two parallel arrays and does a **linear scan**
on every `putIfAbsent`. Building a map of N entries this way costs O(N^2)
total comparisons. This is a reasonable choice for the small maps (a handful
of fields) that make up most Codec usage in vanilla -- but nothing bounds the
map size, and large player-controlled data can hit N in the tens of thousands.

Confirmed real-world trigger: `PlayerAdvancements.load`, on a modpack with
~220 mods generating ~23,700 advancement entries per player, this cost
several seconds of main-thread stall on every join
(`ServerboundFinishConfigurationPacket.handle -> PlayerList.getPlayerForLogin
-> ServerPlayer.<init> -> PlayerAdvancements.load -> ... -> BaseMapCodec.decode`).

This mod replaces the accumulator with `Object2ObjectOpenHashMap`
(O(1) amortized insertion), preserving the original duplicate-key /
partial-error-handling semantics verbatim.

## Structure

- `Common/` -- loader-independent code (the mixin itself), compiled against
  vanilla Minecraft via VanillaGradle. No loader API is used anywhere here.
- `NeoForge/` -- NeoForge loader glue (mod metadata, mixin registration,
  empty `@Mod` entry point). Pulls in Common's sources directly.

Additional loaders (Fabric, Forge) can be added as sibling modules under the
root, each pulling in `Common`'s sources the same way `NeoForge/` does --
`Common` itself never needs to change.

## Building

```
./gradlew :NeoForge:build
```

Output jar: `NeoForge/build/libs/`

First build will likely need a couple of iterations to pin exact working
versions of the `net.neoforged.moddev` and `org.spongepowered.gradle.vanilla`
plugins -- check their respective plugin portal pages for current versions
before Common/NeoForge Gradle sync succeeds.

## Testing

1. Drop the built jar into `mods/`.
2. Repeat the original repro: join with a large (multi-MB) `world/advancements/<uuid>.json`.
3. Profile with Spark using a TICKED aggregator to isolate the slow tick:
   ```
   /spark profiler start --thread * --only-ticks-over 200
   ```
4. Confirm the `Object2ObjectArrayMap.findKey -> Objects.equals` hotspot in
   `Server thread` self-time is gone (or reduced to a linear-cost sliver).

## Status

- [x] Core fix implemented (simple hash-map swap)
- [ ] Benchmarked against baseline (unpatched) and against a size-adaptive
      hybrid variant (array map below some threshold N, hash map above)
- [ ] Fabric / Forge modules
- [ ] Upstream PR to Mojang/DataFixerUpper
