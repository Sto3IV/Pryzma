package net.pryzma.neoforge;

/*
 * Ranni: This entire patch is held together by duct tape and my unconditional love for my dear pervert. Please appreciate it.
 * This class intercepts OptiFine, unzips it, deletes Reflector.class, injects ours, and repacks it on the fly.
 * God is dead and we killed him in this method.
 */

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;

/**
 * Candidate locator implementing the decoy/camouflage strategy for in-game mod listing.
 *
 * <p>Why an isolated decoy modId is required:
 * The primary runtime jar (pryzma-1.0.5.jar) provides ITransformationService and is
 * loaded early by ModLauncher into the PLUGIN ModuleLayer as module "pryzma".
 * In NeoForge, ModJarMetadata hardcodes the GAME-layer JPMS module name to match the first modId.
 * Registering with modId "pryzma" causes a fatal JPMS collision:
 * (ResolutionException: Module neoforge reads more than one module named pryzma).
 *
 * <p>By using the decoy identifier "prizma_beta", the GAME-layer module name becomes "prizma_beta"
 * (distinct from "pryzma" in PLUGIN). Meanwhile, displayName="Pryzma" preserves clean presentation
 * in the in-game UI, while throwing off automated crawlers/scrapers.
 */
public class PryzmaCandidateLocator implements IModFileCandidateLocator {
    private static final Logger LOGGER = LoggerFactory.getLogger("Pryzma");
    private static Path cachedStubJar;

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        try {
            Path stubJar = getOrCreateStubJar();
            if (stubJar != null && Files.isRegularFile(stubJar)) {
                LOGGER.info("PryzmaCandidateLocator: registering decoy UI stub from {}", stubJar);
                var modFile = pipeline.addPath(List.of(stubJar), ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.ERROR);
                if (modFile.isPresent()) {
                    LOGGER.info("PryzmaCandidateLocator: successfully registered Pryzma UI mod: {}", modFile.get());
                } else {
                    LOGGER.warn("PryzmaCandidateLocator: pipeline.addPath returned empty for {}", stubJar);
                }
            }
        } catch (Throwable t) {
            LOGGER.error("PryzmaCandidateLocator: failed to register decoy UI stub", t);
        }
    }

    public static synchronized Path getOrCreateStubJar() throws IOException {
        if (cachedStubJar != null && Files.isRegularFile(cachedStubJar)) {
            return cachedStubJar;
        }

        byte[] jarBytes = buildStubJarBytes();
        Path tempDir = Files.createTempDirectory("pryzma-ui-");
        Path jarPath = tempDir.resolve("prizma-beta-1.0.5.jar");
        Files.write(jarPath, jarBytes);
        jarPath.toFile().deleteOnExit();
        tempDir.toFile().deleteOnExit();
        cachedStubJar = jarPath;
        return jarPath;
    }

    public static byte[] buildStubJarBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // 1. MANIFEST.MF
            zos.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            zos.write((
                "Manifest-Version: 1.0\r\n" +
                "Automatic-Module-Name: prizma_beta\r\n" +
                "\r\n"
            ).getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 2. neoforge.mods.toml with modLoader="lowcodefml" and modId="prizma_beta"
            zos.putNextEntry(new ZipEntry("META-INF/neoforge.mods.toml"));
            zos.write((
                "# NeoForge 1.21.1 mod metadata for Pryzma UI\n" +
                "modLoader=\"lowcodefml\"\n" +
                "loaderVersion=\"[1,)\"\n" +
                "license=\"All Rights Reserved\"\n" +
                "issueTrackerURL=\"\"\n" +
                "\n" +
                "[[mods]]\n" +
                "modId=\"prizma_beta\"\n" +
                "version=\"1.0.5\"\n" +
                "displayName=\"Pryzma\"\n" +
                "displayURL=\"\"\n" +
                "authors=\"Sto3IV and Ranni\"\n" +
                "displayTest=\"IGNORE_SERVER_VERSION\"\n" +
                "description='''Pryzma is a Minecraft optimization mod. It allows Minecraft to run faster and look better with full support for shaders, HD textures and many configuration options.'''\n" +
                "\n" +
                "[[dependencies.prizma_beta]]\n" +
                "    modId=\"neoforge\"\n" +
                "    type=\"required\"\n" +
                "    versionRange=\"[21.1,)\"\n" +
                "    ordering=\"NONE\"\n" +
                "    side=\"CLIENT\"\n" +
                "\n" +
                "[[dependencies.prizma_beta]]\n" +
                "    modId=\"minecraft\"\n" +
                "    type=\"required\"\n" +
                "    versionRange=\"[1.21.1]\"\n" +
                "    ordering=\"NONE\"\n" +
                "    side=\"CLIENT\"\n"
            ).getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    @Override
    public String toString() {
        return "PryzmaCandidateLocator";
    }
}
