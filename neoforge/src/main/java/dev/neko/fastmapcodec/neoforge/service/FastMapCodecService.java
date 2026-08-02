package dev.neko.fastmapcodec.neoforge.service;

import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import dev.neko.fastmapcodec.neoforge.service.ModuleLayerMigrator;
import org.slf4j.Logger;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Makes the com.mojang:authlib module transformable by Mixin on NeoForge.
 *
 * By default, authlib is loaded in ModLauncher's BOOT layer, which is never
 * seen by the GAME-layer TransformingClassLoader that Mixin operates through.
 * This means mixins targeting authlib classes (e.g. YggdrasilMinecraftSessionService)
 * silently never apply, with no error, on NeoForge - unlike on Fabric, whose
 * Knot loader has no such layer separation.
 *
 * Technique adapted from Sinytra/Connector (LGPL-3.0-only):
 * https://github.com/Sinytra/Connector
 *
 * Note: Sinytra Connector implements the same authlib BOOT-to-GAME layer
 * migration independently for its own purposes (running Fabric mods on
 * NeoForge). If both this service and Connector are present on the same
 * server, only one can perform the migration - whichever's
 * ITransformationService runs first "wins", and the other will observe an
 * already-emptied BOOT-layer module. We guard our own side of this (see
 * completeScan below): if authlib is already transformable when we run, we
 * skip our own migration entirely rather than overwriting an already-empty
 * provider. We cannot guarantee the reverse (Connector running after us
 * behaving safely), since ITransformationService has no defined load order.
 */
public class FastMapCodecService implements ITransformationService {
    private static final String NAME = "fastmapcodec_dfu_move";
    private static final String FASTMAPCODEC_MODULE = "datafixerupper";
    private static final String SINYTRA_CONNECTOR_SERVICE_NAME = "connector_loader";
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(IEnvironment environment) {

    }

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) {
        // no-op
    }

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
