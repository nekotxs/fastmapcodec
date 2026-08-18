package dev.neko.fastmapcodec.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import net.neoforged.fml.javafmlmod.FMLModContainer;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import dev.neko.fastmapcodec.tests.TestMixinApplied;
import static dev.neko.fastmapcodec.Constants.MOD_ID;

@Mod("fastmapcodec")
public class FastMapCodecNeoForge {
    static Logger logger = LoggerFactory.getLogger(MOD_ID);

    public FastMapCodecNeoForge(FMLModContainer container, IEventBus modBus) {
        TestMixinApplied.logIfApplied();
        logger.info("FastMapCodec mod initialized");
    }
}
