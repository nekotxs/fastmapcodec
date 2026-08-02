package dev.neko.fastmapcodec.neoforge.service;

import cpw.mods.jarhandling.JarContents;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class FastMapCodecModLocator implements IModFileCandidateLocator {
    private static final String JARJAR_DIR = "META-INF/jarjar";
    private static final String MOD_JAR_PREFIX = "fastmapcodec-";
    private static final String MOD_JAR_SUFFIX = "-mod.jar";

    private static final Logger LOGGER = LoggerFactory.getLogger(FastMapCodecModLocator.class);

    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        try {
            Path ourJarRoot = Path.of(
                    FastMapCodecModLocator.class.getProtectionDomain().getCodeSource().getLocation().toURI()
            );

            Path jarjarDir = ourJarRoot.resolve(JARJAR_DIR);
            Path nestedEntry = findNestedModJar(jarjarDir);

            URI filePathUri = new URI("jij:" + nestedEntry.toAbsolutePath().toUri().getRawSchemeSpecificPart()).normalize();
            Map<String, Object> outerFsArgs = Map.of("packagePath", nestedEntry);

            // Намеренно не закрываем -- JarContents/IModFile читают
            // содержимое лениво в течение всего времени жизни мода
            // (например, fastmapcodec.mixins.json читается позже, во время
            // Mixin bootstrap).
            FileSystem nestedFs = FileSystems.newFileSystem(filePathUri, outerFsArgs);
            JarContents jar = JarContents.of(nestedFs.getPath("/"));
            pipeline.addJarContent(jar, ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.IGNORE);
        } catch (Exception e) {
            LOGGER.error("fastmapcodec locator failed", e);
            pipeline.addIssue(
                    ModLoadingIssue.warning("fastmapcodec.locator_failed").withCause(e)
            );
        }
    }

    private static Path findNestedModJar(Path jarjarDir) throws IOException {
        Path found = null;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(jarjarDir)) {
            for (Path candidate : stream) {
                String fileName = candidate.getFileName().toString();
                if (fileName.startsWith(MOD_JAR_PREFIX) && fileName.endsWith(MOD_JAR_SUFFIX)) {
                    if (found != null) {
                        LOGGER.warn("Found multiple candidates matching {}*{} in {}, using the first one: {}",
                                MOD_JAR_PREFIX, MOD_JAR_SUFFIX, jarjarDir, found.getFileName());
                        continue;
                    }
                    found = candidate;
                }
            }
        }
        if (found == null) {
            throw new IOException(
                    "Could not find a nested mod jar matching " + MOD_JAR_PREFIX + "*" + MOD_JAR_SUFFIX
                            + " inside " + jarjarDir
            );
        }
        return found;
    }
}