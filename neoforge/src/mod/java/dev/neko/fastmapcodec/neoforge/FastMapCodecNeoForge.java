package dev.neko.fastmapcodec.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import net.neoforged.fml.javafmlmod.FMLModContainer;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

@Mod("fastmapcodec")
public class FastMapCodecNeoForge {
    static String MOD_ID = "fastmapcodec";
    static Logger logger = LoggerFactory.getLogger(MOD_ID);

    public FastMapCodecNeoForge(FMLModContainer container, IEventBus modBus) {
        logger.info("FastMapCodec mod initialized");
    }
}
