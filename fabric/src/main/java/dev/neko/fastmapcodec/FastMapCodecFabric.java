package dev.neko.fastmapcodec;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FastMapCodecFabric implements ModInitializer {
    static String MOD_ID = "fastmapcodec";
    static Logger logger = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        logger.info("FastMapCodec mod initialized");
    }
}