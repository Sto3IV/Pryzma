package net.pryzma.neoforge;

/*
 * Ranni: For the love of God, don't remove this or the entire game crashes on startup.
 * If anyone audits this code, tell them Celian made me do it under duress.
 */

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cpw.mods.jarhandling.SecureJar;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.IModuleLayerManager.Layer;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.IncompatibleEnvironmentException;
import pryzma.PryzmaResourceLocator;
import pryzma.PryzmaTransformer;

/**
 * NeoForge 21.1 adapter around Pryzma's ModLauncher transformer.
 * Locates the runtime zip (the mod jar, or an exploded userdev tree packed to a
 * temp zip). Vanilla replacements go through {@code PryzmaTransformer}.
 * {@code net.pryzma} is overlaid onto GAME from {@code srg/net/pryzma} (not
 * jar-root) so the SERVICE layer cannot load it.
 */
public class PryzmaTransformationService implements ITransformationService {
    private static final Logger LOGGER = LoggerFactory.getLogger("Pryzma");
    private static ZipFile payloadZip;
    private static Path payloadZipPath;
    private static PryzmaTransformer transformer;

    @Override
    public String name() {
        return "Pryzma";
    }

    @Override
    public void initialize(IEnvironment environment) {
        LOGGER.info("PryzmaTransformationService.initialize (NeoForge adapter)");
    }

    @Override
    public List<Resource> beginScanning(IEnvironment environment) {
        return List.of();
    }

    @Override
    public List<Resource> completeScan(IModuleLayerManager layerManager) {
        try {
            if (payloadZip == null) {
                LOGGER.warn("completeScan: payload zip missing");
                return List.of();
            }
            Path overlay = extractGameOverlay(payloadZip);
            Path config = overlay.resolve("net").resolve("pryzma").resolve("Config.class");
            Path scan = overlay.resolve("net").resolve("pryzma").resolve("util").resolve("PathPackScan.class");
            Path cap = overlay.resolve("net").resolve("minecraftforge").resolve("common")
                    .resolve("capabilities").resolve("CapabilityProvider.class");
            if (!Files.isRegularFile(config)) {
                LOGGER.warn("completeScan: overlay missing net/pryzma/Config.class");
                return List.of();
            }
            LOGGER.info(
                    "Pryzma GAME overlay from zip config={} pathPackScan={} capabilityProvider={}",
                    Files.isRegularFile(config),
                    Files.isRegularFile(scan),
                    Files.isRegularFile(cap));
            return List.of(new Resource(Layer.GAME, List.of(SecureJar.from(overlay))));
        } catch (Throwable t) {
            LOGGER.warn("GAME overlay of net.pryzma failed; transformer still registered", t);
            return List.of();
        }
    }

    /**
     * Unpacks {@code srg/net/pryzma/**} and {@code srg/net/minecraftforge/**}
     * from the payload zip into a GAME-layer tree ({@code net/pryzma}, {@code net/minecraftforge}).
     */
    static Path extractGameOverlay(ZipFile zip) throws IOException {
        Path tmp = Files.createTempDirectory("pryzma-game-");
        var entries = zip.entries();
        int copied = 0;
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            String name = entry.getName().replace('\\', '/');
            if (name.startsWith("/")) {
                name = name.substring(1);
            }
            String destRel = null;
            if (name.startsWith("srg/net/pryzma/") || name.startsWith("srg/net/minecraftforge/")) {
                destRel = name.substring("srg/".length());
            }
            if (destRel == null || destRel.contains("..")) {
                continue;
            }
            Path dest = tmp.resolve(destRel).normalize();
            if (!dest.startsWith(tmp)) {
                continue;
            }
            Files.createDirectories(dest.getParent());
            try (InputStream in = zip.getInputStream(entry)) {
                Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
            }
            copied++;
        }
        LOGGER.info("extractGameOverlay copied {} entries to {}", copied, tmp);
        return tmp;
    }

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) throws IncompatibleEnvironmentException {
        LOGGER.info("PryzmaTransformationService.onLoad");
        try {
            payloadZipPath = locateRuntimeZip();
            payloadZip = new ZipFile(payloadZipPath.toFile());
            LOGGER.info("Pryzma ZIP file: {}", payloadZipPath);
            transformer = new PryzmaTransformer(payloadZip, env);
            PryzmaResourceLocator.setResourceLocator(transformer);
        } catch (Exception e) {
            LOGGER.error("Error loading Pryzma ZIP file", e);
            throw new IncompatibleEnvironmentException("Error loading Pryzma ZIP file: " + e.getMessage());
        }
    }

    @Override
    public List<? extends ITransformer<?>> transformers() {
        LOGGER.info("PryzmaTransformationService.transformers");
        List<ITransformer<?>> list = new ArrayList<>();
        if (transformer != null) {
            list.add(new FilteredReplacementTransformer(transformer, FilteredReplacementTransformer.KEEP_NEOFORGE));
        }
        list.add(new ItemTagsCreateTransformer());
        list.add(new GuiInitModdedOverlaysTransformer());
        list.add(new ResUtilsPathTransformer());
        list.add(new ClientLevelTransformer());
        list.add(new FrustumTransformer());
        list.add(new SectionCompilerTransformer());
        list.add(new LevelRendererTransformer());
        list.add(new BlockEntityTransformer());
        list.add(new ModelBakerTransformer());
        list.add(new LiquidBlockRendererTransformer());
        list.add(new TextureAtlasTransformer());
        list.add(new PlayerInfoTransformer());
        list.add(new ReflectorTransformer());
        list.add(new ReflectorForgeTransformer());
        list.add(new MobTransformer());
        list.add(new PauseScreenTransformer());
        list.add(new PaintingRendererTransformer());
        list.add(new EntityRenderDispatcherTransformer());
        list.add(new MixinHardeningTransformer());
        return list;
    }

    public static PryzmaTransformer getTransformer() {
        return transformer;
    }

    public static ZipFile getPayloadZip() {
        return payloadZip;
    }

    public static Path getPayloadZipPath() {
        return payloadZipPath;
    }

    static boolean zipHasPayload(Path zip) {
        if (zip == null || !Files.isRegularFile(zip)) {
            return false;
        }
        try (ZipFile z = new ZipFile(zip.toFile())) {
            return z.getEntry("srg/net/pryzma/Config.class") != null
                    || z.getEntry("files.txt") != null;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Directory that contains {@code files.txt} and {@code srg/} (userdev
     * {@code build/resources/main} or an exploded install).
     */
    static Path findCompiledForgeStubs() {
        Path payload = findPayloadRoot();
        if (payload != null) {
            Path build = payload.getParent() != null ? payload.getParent().getParent() : null;
            if (build != null) {
                Path stub = build.resolve("classes").resolve("java").resolve("forgeStubs")
                        .resolve("net").resolve("minecraftforge");
                if (Files.isRegularFile(stub.resolve("common").resolve("capabilities").resolve("CapabilityProvider.class"))) {
                    return stub;
                }
            }
        }
        List<Path> seeds = new ArrayList<>();
        addLocation(seeds, PryzmaTransformationService.class);
        for (Path seed : seeds) {
            Path cur = seed;
            for (int i = 0; i < 10 && cur != null; i++) {
                Path cand = cur.resolve("net").resolve("minecraftforge").resolve("common").resolve("capabilities").resolve("CapabilityProvider.class");
                if (Files.isRegularFile(cand)) {
                    return cur.resolve("net").resolve("minecraftforge");
                }
                if ("main".equals(String.valueOf(cur.getFileName())) && cur.getParent() != null) {
                    Path classesMain = cur.getParent().getParent() != null && cur.getParent().getParent().getParent() != null
                            ? cur.getParent().getParent().getParent().resolve("classes").resolve("java").resolve("main")
                            : null;
                    if (classesMain != null) {
                        Path stub = classesMain.resolve("net").resolve("minecraftforge");
                        if (Files.isRegularFile(stub.resolve("common").resolve("capabilities").resolve("CapabilityProvider.class"))) {
                            return stub;
                        }
                    }
                }
                cur = cur.getParent();
            }
        }
        URL self = PryzmaTransformationService.class.getResource(
                "/net/minecraftforge/common/capabilities/CapabilityProvider.class");
        if (self != null) {
            Path classFile = uriToPath(URI.create(self.toString()));
            if (classFile != null && Files.isRegularFile(classFile)) {
                // .../net/minecraftforge/common/capabilities/CapabilityProvider.class
                Path caps = classFile.getParent();
                Path common = caps != null ? caps.getParent() : null;
                Path forge = common != null ? common.getParent() : null;
                if (forge != null && "minecraftforge".equals(String.valueOf(forge.getFileName()))) {
                    return forge;
                }
            }
        }
        return null;
    }

    static Path findPayloadRoot() {
        List<Path> seeds = new ArrayList<>();
        addLocation(seeds, PryzmaTransformationService.class);
        addLocation(seeds, PryzmaTransformer.class);
        URL marker = PryzmaTransformationService.class.getResource("/files.txt");
        if (marker != null) {
            Path mp = uriToPath(URI.create(marker.toString()));
            if (mp != null) {
                seeds.add(mp);
            }
        }
        for (Path seed : seeds) {
            Path cur = seed;
            for (int i = 0; i < 10 && cur != null; i++) {
                Path[] cands = new Path[] {
                    cur,
                    cur.getParent(),
                    cur.resolve("resources").resolve("main"),
                    cur.resolve("build").resolve("resources").resolve("main"),
                    cur.resolve("src").resolve("main").resolve("resources"),
                };
                for (Path cand : cands) {
                    if (cand != null && Files.isRegularFile(cand.resolve("files.txt"))
                            && Files.isRegularFile(cand.resolve("srg").resolve("net").resolve("pryzma").resolve("Config.class"))) {
                        return cand;
                    }
                }
                // userdev: .../build/classes/java/main -> .../build/resources/main
                if (cur.getFileName() != null && "main".equals(cur.getFileName().toString())) {
                    Path build = cur.getParent() != null && cur.getParent().getParent() != null
                            ? cur.getParent().getParent().getParent()
                            : null;
                    if (build != null) {
                        Path res = build.resolve("resources").resolve("main");
                        if (Files.isRegularFile(res.resolve("files.txt"))) {
                            return res;
                        }
                    }
                }
                cur = cur.getParent();
            }
        }
        return null;
    }

    private static void addLocation(List<Path> seeds, Class<?> cls) {
        try {
            URL loc = cls.getProtectionDomain().getCodeSource().getLocation();
            if (loc != null) {
                Path p = uriToPath(URI.create(loc.toString()));
                if (p != null) {
                    seeds.add(p);
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
    }

    private static Path locateRuntimeZip() throws IOException {
        URL loc = PryzmaTransformationService.class.getProtectionDomain().getCodeSource().getLocation();
        LOGGER.info("Pryzma code source: {}", loc);
        Path fromLoc = loc == null ? null : uriToPath(URI.create(loc.toString()));
        if (zipHasPayload(fromLoc)) {
            LOGGER.info("Pryzma payload is the code-source jar {}", fromLoc);
            return fromLoc;
        }
        Path root = findPayloadRoot();
        LOGGER.info("Pryzma payload root: {}", root);
        if (root != null && Files.isDirectory(root)) {
            Path packed = Files.createTempFile("pryzma-runtime-", ".jar");
            packed.toFile().deleteOnExit();
            packDirectoryToZip(root, packed);
            LOGGER.info("Packed Pryzma resources from {} to {} ({} bytes)", root, packed, Files.size(packed));
            if (!zipHasPayload(packed)) {
                throw new IOException("Packed zip missing srg/net/pryzma/Config.class from " + root);
            }
            return packed;
        }
        throw new IOException("Cannot locate Pryzma runtime payload (files.txt + srg/). loc=" + loc);
    }

    private static Path uriToPath(URI uri) {
        try {
            String scheme = uri.getScheme();
            if ("file".equals(scheme)) {
                return Paths.get(uri);
            }
            if ("jar".equals(scheme)) {
                String ssp = uri.getSchemeSpecificPart();
                int bang = ssp.indexOf('!');
                String filePart = bang >= 0 ? ssp.substring(0, bang) : ssp;
                if (filePart.startsWith("file:")) {
                    return Paths.get(URI.create(filePart));
                }
                return Paths.get(filePart);
            }
            if ("union".equals(scheme)) {
                String p = uri.getPath();
                if (p != null && p.contains("#")) {
                    p = p.substring(0, p.lastIndexOf('#'));
                }
                File file = new File(p);
                Map<String, String> map = new HashMap<>();
                map.put("create", "true");
                try {
                    FileSystems.newFileSystem(URI.create("jar:" + file.toURI() + "!/"), map);
                } catch (Exception ignored) {
                    // already mounted
                }
                return file.toPath();
            }
            return Paths.get(uri);
        } catch (Exception e) {
            LOGGER.warn("uriToPath failed for {}", uri, e);
            return null;
        }
    }

    private static void copyTree(Path from, Path to) throws IOException {
        Files.createDirectories(to);
        try (var walk = Files.walk(from)) {
            walk.filter(Files::isRegularFile).forEach(src -> {
                try {
                    Path dest = to.resolve(from.relativize(src).toString());
                    Files.createDirectories(dest.getParent());
                    Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private static void packDirectoryToZip(Path dir, Path zipOut) throws IOException {
        if (Files.exists(zipOut)) {
            Files.delete(zipOut);
        }
        URI zipUri = URI.create("jar:" + zipOut.toUri());
        Map<String, String> env = new HashMap<>();
        env.put("create", "true");
        try (var fs = FileSystems.newFileSystem(zipUri, env)) {
            try (var walk = Files.walk(dir)) {
                walk.filter(Files::isRegularFile).forEach(src -> {
                    try {
                        Path rel = dir.relativize(src);
                        Path dest = fs.getPath("/");
                        for (Path part : rel) {
                            dest = dest.resolve(part.toString());
                        }
                        if (dest.getParent() != null) {
                            Files.createDirectories(dest.getParent());
                        }
                        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }
    }

    public static Optional<URL> getResourceUrl(String name) {
        if (name.endsWith(".class") && !name.startsWith("pryzma/")) {
            name = "srg/" + name;
        }
        if (payloadZip == null) {
            return Optional.empty();
        }
        if (payloadZip.getEntry(name) == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new URL("jar:" + payloadZipPath.toUri() + "!/" + name));
        } catch (IOException e) {
            LOGGER.error("getResourceUrl", e);
            return Optional.empty();
        }
    }

    public static InputStream openPayload(String name) throws IOException {
        if (payloadZip == null) {
            return null;
        }
        var entry = payloadZip.getEntry(name);
        return entry == null ? null : payloadZip.getInputStream(entry);
    }
}
