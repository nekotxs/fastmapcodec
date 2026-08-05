package dev.neko.fastmapcodec.neoforge.service;

import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;


// makes DFU transformable on 1.21-1.21.7 using moveModule technique, borrowed from Sinytra Connector
public class FastMapCodecService implements ITransformationService {
    private static final String NAME = "fastmapcodec_dfu_move";
    private static final String FASTMAPCODEC_MODULE = "datafixerupper";
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(IEnvironment environment) {}

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) {}

    @Override
    public List<Resource> completeScan(IModuleLayerManager layerManager) {
        boolean alreadyTransformable = ModuleLayerMigrator.isModuleAlreadyTransformable(layerManager, FASTMAPCODEC_MODULE);

        if (alreadyTransformable) {
            LOGGER.info("DFU module was already made transformable by another mod - skipping our own migration.");
            return List.of();
        }
        return List.of(new Resource(
                IModuleLayerManager.Layer.GAME,
                Stream.of(ModuleLayerMigrator.moveModule(FASTMAPCODEC_MODULE)).filter(Objects::nonNull).toList()
        ));
    }

    @Override
    public List<? extends ITransformer<?>> transformers() {
        return List.of();
    }
}
