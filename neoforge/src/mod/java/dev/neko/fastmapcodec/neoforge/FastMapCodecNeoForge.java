package dev.neko.fastmapcodec.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import net.neoforged.fml.javafmlmod.FMLModContainer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("fastmapcodec")
public class FastMapCodecNeoForge {
    static String MOD_ID = "fastmapcodec";
    static Logger logger = LogManager.getLogger(MOD_ID);

    public FastMapCodecNeoForge(FMLModContainer container, IEventBus modBus) {
        logger.info("FastMapCodec mod initialized");
    }
}
