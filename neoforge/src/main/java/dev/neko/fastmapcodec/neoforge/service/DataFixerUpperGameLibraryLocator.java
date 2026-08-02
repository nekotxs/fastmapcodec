package dev.neko.fastmapcodec.neoforge.service;

import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Makes com.mojang:datafixerupper transformable by Mixin on 1.21.8+
 *
 */
public class DataFixerUpperGameLibraryLocator implements IModFileCandidateLocator {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataFixerUpperGameLibraryLocator.class);

    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        Path ourJarRoot;
        try {
            ourJarRoot = Path.of(
                    DataFixerUpperGameLibraryLocator.class.getProtectionDomain().getCodeSource().getLocation().toURI()
            );
        } catch (Exception e) {
            LOGGER.error("fastmapcodec: failed to resolve own jar location", e);
            pipeline.addIssue(ModLoadingIssue.warning("fastmapcodec.dfu_gamelibrary_failed").withCause(e));
            return;
        }

        if (Files.isDirectory(ourJarRoot)) {
            // Old loader generation: ourJarRoot is a navigable union-style
            // Path -- ModuleLayerMigrator (via FastMapCodecService) already
            // makes datafixerupper transformable there. Do nothing here.
            return;
        }

        try {
            // Ask the JVM where a known datafixerupper class was actually
            // loaded from, rather than hardcoding a filename or version --
            // works regardless of the exact datafixerupper version in use.
            Path dfuJarPath = Path.of(
                    com.mojang.serialization.Codec.class.getProtectionDomain().getCodeSource().getLocation().toURI()
            );
            Path repackaged = repackageAsGameLibrary(dfuJarPath);
            pipeline.addPath(repackaged, ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.IGNORE);
        } catch (Exception e) {
            LOGGER.error("fastmapcodec: failed to register datafixerupper as a game library", e);
            pipeline.addIssue(
                    ModLoadingIssue.warning("fastmapcodec.dfu_gamelibrary_failed").withCause(e)
            );
        }
    }

    /**
     * Copies a jar verbatim into a new temp file, except for its manifest,
     * which gets an added "FMLModType: GAMELIBRARY" attribute.
     */
    private static Path repackageAsGameLibrary(Path originalJarPath) throws IOException {
        Path outPath = Files.createTempFile("fastmapcodec-datafixerupper-gamelib-", ".jar");
        outPath.toFile().deleteOnExit();

        try (JarInputStream in = new JarInputStream(Files.newInputStream(originalJarPath))) {
            Manifest manifest = in.getManifest();
            if (manifest == null) {
                manifest = new Manifest();
                manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            }
            manifest.getMainAttributes().put(new Attributes.Name("FMLModType"), "GAMELIBRARY");

            try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(outPath), manifest)) {
                byte[] buf = new byte[8192];
                JarEntry entry;
                while ((entry = in.getNextJarEntry()) != null) {
                    out.putNextEntry(new JarEntry(entry.getName()));
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                    out.closeEntry();
                }
            }
        }
        return outPath;
    }
}