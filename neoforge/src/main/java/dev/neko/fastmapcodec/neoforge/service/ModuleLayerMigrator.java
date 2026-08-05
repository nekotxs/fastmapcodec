package dev.neko.fastmapcodec.neoforge.service;

import cpw.mods.jarhandling.SecureJar;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleReference;
import java.lang.module.ResolvedModule;
import java.net.URI;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static cpw.mods.modlauncher.api.LambdaExceptionUtils.uncheck;

/**
 * Adapted from Sinytra/Connector (MIT License):
 * https://github.com/Sinytra/Connector/blob/dev/1.21.x/src/main/java/org/sinytra/connector/service/hacks/ModuleLayerMigrator.java
 *
 * "Moves" a module from the BOOT layer to the GAME layer so that it becomes
 * eligible for Mixin transformation, which only operates on classes loaded
 * through the GAME-layer TransformingClassLoader.
 */
public class ModuleLayerMigrator {
    private static final Class<?> JAR_MODULE_REF_CLASS = uncheck(() -> Class.forName("cpw.mods.cl.JarModuleFinder$JarModuleReference"));
    private static final VarHandle REF_MODULE_PROVIDER_FIELD = uncheck(() -> dev.neko.fastmapcodec.neoforge.service.ConnectorUtil.TRUSTED_LOOKUP.findVarHandle(JAR_MODULE_REF_CLASS, "jar", SecureJar.ModuleDataProvider.class));
    private static final VarHandle DESCRIPTOR_PACKAGES_FIELD = uncheck(() -> dev.neko.fastmapcodec.neoforge.service.ConnectorUtil.TRUSTED_LOOKUP.findVarHandle(ModuleDescriptor.class, "packages", Set.class));
    private static final MethodHandle IMPL_ADD_READS = uncheck(() -> dev.neko.fastmapcodec.neoforge.service.ConnectorUtil.TRUSTED_LOOKUP.findVirtual(Module.class, "implAddReads", MethodType.methodType(void.class, Module.class)));
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);

    public static SecureJar moveModule(String moduleName) {
        try {
            LOGGER.debug("Attempting to make module {} transformable", moduleName);
            ModuleLayer layer = Launcher.INSTANCE.findLayerManager().orElseThrow().getLayer(IModuleLayerManager.Layer.BOOT).orElseThrow();
            ResolvedModule module = layer.configuration().findModule(moduleName).orElseThrow(() -> new RuntimeException("Module %s not found".formatted(moduleName)));
            Module actualModule = layer.findModule(moduleName).orElseThrow(() -> new RuntimeException("Module %s not found".formatted(moduleName)));

            ModuleReference reference = module.reference();
            if (!JAR_MODULE_REF_CLASS.isInstance(reference)) {
                throw new RuntimeException("Module %s does not contain a jar module reference".formatted(moduleName));
            }

            SecureJar.ModuleDataProvider originalProvider = (SecureJar.ModuleDataProvider) REF_MODULE_PROVIDER_FIELD.get(reference);
            SecureJar.ModuleDataProvider wrappedProvider = new EmptyModuleDataProvider(originalProvider.name());
            REF_MODULE_PROVIDER_FIELD.set(reference, wrappedProvider);

            SecureJar.ModuleDataProvider provider = new ModuleDataProviderWrapper(originalProvider, "fastmapcodec$" + moduleName);

            ModuleDescriptor desc = actualModule.getDescriptor();
            DESCRIPTOR_PACKAGES_FIELD.set(desc, Set.of());

            LOGGER.info("Successfully made module {} transformable", moduleName);
            return new SimpleSecureJar(provider);
        } catch (Throwable t) {
            LOGGER.error("Error making module {} transformable", moduleName, t);
            return null;
        }
    }

    public static void addReads(Set<Module> sources) {
        try {
            Module ourModule = ModuleLayerMigrator.class.getModule();
            for (Module source : sources) {
                IMPL_ADD_READS.invoke(source, ourModule);
            }
        } catch (Throwable t) {
            LOGGER.error("Error adding reads to modules", t);
        }
    }

    /**
     * Checks whether some module in the GAME layer already exports the given
     * module's known package - regardless of what that module is actually
     * named. Used to detect whether another mod
     * already performed the BOOT -> GAME migration for this module before we
     * did, so we don't redundantly (and destructively) repeat the operation
     * on an already-emptied BOOT-layer provider.
     */
    public static boolean isModuleAlreadyTransformable(IModuleLayerManager layerManager, String moduleName) {
        try {
            ModuleLayer bootLayer = layerManager.getLayer(IModuleLayerManager.Layer.BOOT).orElseThrow();
            Module module = bootLayer.findModule(moduleName).orElse(null);
            if (module == null) {
                return false;
            }
            var packages = module.getDescriptor().packages();
            return packages.isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    private static class EmptyModuleDataProvider implements SecureJar.ModuleDataProvider {
        private final String name;
        private ModuleDescriptor descriptor;

        public EmptyModuleDataProvider(String name) {
            this.name = name;
        }

        @Override
        public ModuleDescriptor descriptor() {
            if (descriptor == null) {
                descriptor = ModuleDescriptor.newAutomaticModule(name()).build();
            }
            return descriptor;
        }

        @Override public String name() { return this.name; }
        @Override public URI uri() { return uncheck(() -> new URI("file:///~nonexistent")); }
        @Override public Optional<URI> findFile(String name) { return Optional.empty(); }
        @Override public Optional<InputStream> open(String name) { return Optional.empty(); }
        @Override public Manifest getManifest() { return new Manifest(); }
        @Override public CodeSigner[] verifyAndGetSigners(String cname, byte[] bytes) { return new CodeSigner[0]; }
    }

    private record SimpleSecureJar(SecureJar.ModuleDataProvider moduleDataProvider) implements SecureJar {
        @Override public Path getPrimaryPath() { return Path.of(moduleDataProvider().uri()); }
        @Override public CodeSigner[] getManifestSigners() { return new CodeSigner[0]; }
        @Override public Status verifyPath(Path path) { return Status.NONE; }
        @Override public Status getFileStatus(String name) { return Status.NONE; }
        @Override public Attributes getTrustedManifestEntries(String name) { return new Attributes(); }
        @Override public boolean hasSecurityData() { return false; }
        @Override public void close() {}
        @Override public String name() { return moduleDataProvider().name(); }
        @Override public Path getPath(String first, String... rest) { return getPrimaryPath(); }
        @Override public Path getRootPath() { return getPrimaryPath(); }
    }

    private static class ModuleDataProviderWrapper implements SecureJar.ModuleDataProvider {
        private final SecureJar.ModuleDataProvider provider;
        private final String name;
        private final ModuleDescriptor descriptor;

        public ModuleDataProviderWrapper(SecureJar.ModuleDataProvider provider, String name) {
            this.provider = provider;
            this.name = name;

            ModuleDescriptor desc = this.provider.descriptor();
            var builder = ModuleDescriptor.newModule(this.name, desc.modifiers());
            builder.packages(desc.packages());
            if (!desc.isAutomatic()) {
                desc.version().ifPresent(builder::version);
                desc.requires().forEach(builder::requires);
                desc.exports().forEach(builder::exports);
                desc.opens().forEach(builder::opens);
                desc.uses().forEach(builder::uses);
                desc.provides().forEach(builder::provides);
            }
            this.descriptor = builder.build();
        }

        @Override public String name() { return name; }
        @Override public URI uri() { return provider.uri(); }
        @Override public ModuleDescriptor descriptor() { return descriptor; }
        @Override public Optional<URI> findFile(String name) { return provider.findFile(name); }
        @Override public Optional<InputStream> open(String name) { return provider.open(name); }
        @Override public Manifest getManifest() { return provider.getManifest(); }
        @Override public CodeSigner[] verifyAndGetSigners(String cname, byte[] bytes) { return provider.verifyAndGetSigners(cname, bytes); }
    }
}
