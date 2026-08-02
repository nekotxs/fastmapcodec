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
 *
 * Needed because this outer jar is consumed early as an early-service
 * source, which makes FML's normal ModsFolderLocator skip it during the
 * ordinary mods-folder scan -- so without this, the real mod content
 * (neoforge.mods.toml, mixins, @Mod class) is never discovered.
 *
 * Works across two structurally different NeoForge loader generations,
 * discovered the hard way:
 *
 *  - Pre-consolidation loaders (ModLauncher + SecureJarHandler, e.g.
 *    NeoForge 21.1.243 / loader-4.0.43): getCodeSource().getLocation()
 *    resolves to a virtual "union:" Path that is ALREADY navigable as a
 *    directory representing this jar's own contents -- .resolve() works
 *    directly, no need to open anything. This is the shape
 *    AlternativeAuthModLocator (and earlier versions of this class) assumed
 *    unconditionally.
 *  - Post-consolidation loaders (ModLauncher/SecureJarHandler/
 *    BootstrapLauncher folded into FML itself, e.g. NeoForge 21.11.x /
 *    loader-10.0.36 and the 26.x line): getCodeSource().getLocation()
 *    resolves to a perfectly ordinary, disk-backed Path pointing at the jar
 *    FILE itself -- .resolve() on it fails with NotDirectoryException,
 *    since a file isn't a directory. It has to be opened as a zip
 *    filesystem first.
 *
 * Files.isDirectory(ourJarRoot) reliably tells the two apart: true only for
 * the already-navigable union-style Path.
 *
 * The nested jar is then extracted to a real temp file rather than opened
 * via a nested/nested-in-nested FileSystem, for two reasons found through
 * trial and error: (1) NeoForge's old loader-specific "jij:"
 * FileSystemProvider used for this purpose does not exist on the new
 * loader (ProviderNotFoundException: Provider "jij" not found); (2) the
 * generic JDK nested-zip approach (FileSystems.newFileSystem(nestedEntry))
 * works mechanically on both, but handing its rootless Path ("/", no
 * filename) straight to JarContents.of() throws inside securejarhandler's
 * internal UnionFileSystemProvider.makeKey(), which derives its key from
 * the Path's filename. A plain, named, disk-backed file sidesteps both
 * failure modes and is the same shape JarContents.of() already handles for
 * every ordinary mod jar in mods/.
 *
 * Adapted from Sinytra/Connector (LGPL-3.0-only) and this project's own
 * alternative_auth mod's AlternativeAuthModLocator (MIT).
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