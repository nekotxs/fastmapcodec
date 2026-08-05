package dev.neko.fastmapcodec.tests;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TestMixinBaseMapCodec {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestMixinBaseMapCodec.class);

    private TestMixinBaseMapCodec() {}

    public static boolean fired(int n) {
        LOGGER.info("fastmapcodec.test: patched decode() path confirmed executing (N={})", n);
        return true;
    }
}