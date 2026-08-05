package dev.neko.fastmapcodec.tests;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.UnboundedMapCodec;
import dev.neko.fastmapcodec.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TestMixinApplied {
    final private static Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);

    private TestMixinApplied() {}

    public static void logIfApplied() {
        Object codec = new UnboundedMapCodec<>(Codec.STRING, Codec.STRING);
        boolean applied = codec instanceof FastMapCodecApplied;

        if (applied) {
            LOGGER.info("fastmapcodec.test: mixin applied");
        } else {
            LOGGER.error("fastmapcodec.test: mixin failed to apply");
        }
    }
}