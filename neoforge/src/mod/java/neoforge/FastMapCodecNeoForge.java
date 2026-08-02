package dev.neko.fastmapcodec.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Entry point required by NeoForge to recognize this as a mod.
 * All actual behavior comes from {@code fastmapcodec.mixins.json},
 * registered via neoforge.mods.toml -- this class intentionally
 * does nothing else.
 */
@Mod("fastmapcodec")
public class FastMapCodecNeoForge {
    static String MOD_ID = "fastmapcodec";
    static Logger logger = LogManager.getLogger(MOD_ID);

    public FastMapCodecNeoForge(IEventBus modBus) {
        logger.info("FastMapCodec mod initialized");
    }
}
