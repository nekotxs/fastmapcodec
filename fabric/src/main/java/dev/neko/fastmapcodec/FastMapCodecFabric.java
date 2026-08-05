package dev.neko.fastmapcodec;

import dev.neko.fastmapcodec.tests.TestMixinApplied;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FastMapCodecFabric implements ModInitializer {
    static Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("FastMapCodec mod initialized");
        TestMixinApplied.logIfApplied();
    }
}