package dev.neko.fastmapcodec;

import net.fabricmc.api.ModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FastMapCodecFabric implements ModInitializer {
    static String MOD_ID = "fastmapcodec";
    static Logger logger = LogManager.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        logger.info("FastMapCodec mod initialized");
    }
}