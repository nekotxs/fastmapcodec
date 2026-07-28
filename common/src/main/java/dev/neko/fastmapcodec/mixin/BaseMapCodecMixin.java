package dev.neko.fastmapcodec.mixin;

import com.mojang.serialization.codecs.BaseMapCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BaseMapCodec.class, remap = false)
public interface BaseMapCodecMixin<K, V> {

    @Inject(method = "decode", at = @At("HEAD"))
    default void fastmapcodec$probe(Object ops, Object input, CallbackInfoReturnable<?> cir) {
        System.out.println("[fastmapcodec] PROBE: decode() called, injection works");
    }
}
