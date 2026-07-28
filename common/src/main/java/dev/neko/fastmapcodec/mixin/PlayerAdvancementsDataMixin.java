package dev.neko.fastmapcodec.mixin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.UnboundedMapCodec;
import dev.neko.fastmapcodec.FastUnboundedMapCodec;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;
import java.util.function.Function;

@Mixin(targets = "net/minecraft/server/PlayerAdvancements$Data")
public class PlayerAdvancementsDataMixin {

    @Redirect(
        method = "<clinit>",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/serialization/codecs/UnboundedMapCodec;xmap(Ljava/util/function/Function;Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;"
        )
    )
    private static Codec<?> fastmapcodec$replaceMapCodec(
            UnboundedMapCodec<?, ?> brokenInstance, Function to, Function from) {
        Codec<Map<ResourceLocation, AdvancementProgress>> fast =
            new FastUnboundedMapCodec<>(ResourceLocation.CODEC, AdvancementProgress.CODEC);
        return fast.xmap(to, from);
    }
}
