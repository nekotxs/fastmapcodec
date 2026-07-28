package dev.neko.fastmapcodec.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class SanityCheckMixin {
    @Inject(method = "<init>", at = @At("HEAD"))
    private static void fastmapcodec$sanity(CallbackInfo ci) {
        System.out.println("[fastmapcodec] SANITY: MinecraftServer mixin works");
    }
}
