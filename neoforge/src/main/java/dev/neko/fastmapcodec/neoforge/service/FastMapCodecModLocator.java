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
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Extracts the actual mod jar from META-INF/jarjar/ inside our own outer
 * (service) jar and re-registers it as an independent mod candidate.
 */
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

            Path extracted;
            if (Files.isDirectory(ourJarRoot)) {
                // Old loader: ourJarRoot is already a navigable union-style
                // Path representing this jar's own contents.
                Path jarjarDir = ourJarRoot.resolve(JARJAR_DIR);
                Path nestedEntry = findNestedModJar(jarjarDir);
                extracted = extractToTempFile(nestedEntry);
            } else {
                // New loader: ourJarRoot is a plain disk-backed Path to the
                // jar file itself -- open it as a zip first.
                try (FileSystem outerFs = FileSystems.newFileSystem(ourJarRoot)) {
                    Path jarjarDir = outerFs.getPath("/" + JARJAR_DIR);
                    Path nestedEntry = findNestedModJar(jarjarDir);
                    extracted = extractToTempFile(nestedEntry);
                }
            }

            pipeline.addPath(extracted, ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.IGNORE);
        } catch (Exception e) {
            LOGGER.error("fastmapcodec locator failed", e);
            pipeline.addIssue(
                    ModLoadingIssue.warning("fastmapcodec.locator_failed").withCause(e)
            );
        }
    }

    private static Path extractToTempFile(Path nestedEntry) throws IOException {
        Path extracted = Files.createTempFile("fastmapcodec-neoforge-mod-", ".jar");
        extracted.toFile().deleteOnExit();
        Files.copy(nestedEntry, extracted, StandardCopyOption.REPLACE_EXISTING);
        return extracted;
    }

    /**
     * Finds the nested mod jar by prefix/suffix rather than an exact,
     * version-hardcoded filename, so this locator doesn't silently stop
     * working on every version bump.
     */
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