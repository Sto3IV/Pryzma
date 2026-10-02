package net.pryzma.dev;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.pryzma.PryzmaConfig;
import net.pryzma.detail.PrDetailClassifier;
import net.pryzma.gui.PrZoom;
import net.pryzma.shader.PryzmaShaders;
import net.pryzma.shader.gui.screen.ShaderPackScreen;
import net.pryzma.render.PrF3RenderCache;

/**
 * Scripted client self-test used only by the {@code runSelftest} Gradle run; never packaged.
 *
 * <p>One command per line, {@code #} starts a comment:
 * <pre>
 * world &lt;name&gt; [flat|normal]   open the save, or create a creative/peaceful one
 * loaded                        wait until the player is in the world and every visible section is built
 * wait &lt;ticks&gt;                  wait client ticks
 * cmd &lt;command&gt;                 run a command as the player (no leading slash)
 * look &lt;yaw&gt; &lt;pitch&gt;           set the camera rotation
 * set &lt;prField&gt; &lt;value&gt;        assign a PryzmaConfig field
 * pack &lt;id&gt;                     select a resource pack (e.g. file/pryzma-selftest)
 * unpack &lt;id&gt;                   deselect a resource pack
 * reload                        reload resource packs and wait for completion
 * gui on|off                    show or hide the HUD
 * allchanged                    rebuild every chunk section (after changing a render option)
 * shot &lt;name&gt;                   write screenshots/selftest/&lt;name&gt;.png at the end of the next frame
 * lightstats &lt;frames&gt;           log the largest frame-to-frame change of the lightmap texture
 * log &lt;text&gt;                    write a marker to the log
 * screen video                  open the vanilla video settings (the Pryzma screen replaces it)
 * press &lt;label&gt;                 left-click the widget whose text starts with label
 * rpress &lt;label&gt;                right-click it
 * mouse &lt;x&gt; &lt;y&gt;                move the cursor to GUI coordinates
 * close                         close the open screen
 * dump                          log every widget of the open screen: class, bounds, text
 * zoom on|off                   hold or release the zoom key
 * use on|off                    hold or release the use key (shield blocking, bow pulling)
 * slot &lt;0-8&gt;                    select a hotbar slot
 * view first|back|front         set the camera perspective
 * inventory                     open the player inventory
 * shaderpack &lt;name&gt;|OFF        select a shader pack from shaderpacks/ and reload the pipeline
 * screen shaders                open the shader pack screen
 * screen pause|options          open the pause menu or the vanilla options screen
 * leave                         save and quit to the title screen
 * shaderinfo                    log the pipeline state and its F3 lines (shadow sections, culling)
 * detailblocks                  log every registered block the decorator LOD (Detail Distance) would cull
 * fps &lt;frames&gt;                  log mean and worst frame time over the next frames
 * f3 on|off|charts              show or hide the debug screen, or toggle its FPS chart (F3+2)
 * f3cache &lt;frames&gt;              log how many of the next frames replayed the F3 text, and its builds per second
 * f3compare &lt;name&gt;              screenshot three frames, F3 cache off (vanilla), building, replaying; log their differences
 * guiscale &lt;n&gt;                  set the GUI scale (0 = auto) and resize
 * vsync on|off                  off also lifts the frame rate limit
 * nopause                      do not pause when the window loses focus, and resume if paused
 * quit                          stop the client
 * </pre>
 */
public final class PrSelfTest {
    private static final Logger LOG = LoggerFactory.getLogger("Pryzma-SelfTest");
    private static final int LOAD_TIMEOUT_TICKS = 20 * 120;

    private final List<String> steps;
    private int index;
    private int waitTicks;
    private int loadTicks;
    private boolean titleSeen;
    private boolean reloading;
    private String pendingShot;
    private int lightFrames;
    private int[] lightPrevious;
    private int lightMaxDelta;
    private long lightSumDelta;
    private int lightSamples;
    private int fpsFrames;
    private int fpsSamples;
    private long fpsLast;
    private long fpsTotal;
    private long fpsWorst;
    private int f3Frames;
    private int f3Sampled;
    private int f3Replays;
    private int f3Builds;
    private long f3Start;
    private long f3Last;
    private long f3BuildNanos;
    private long f3ReplayNanos;
    private String compareName;
    private int compareStage;
    private final NativeImage[] compareShots = new NativeImage[3];

    private PrSelfTest(List<String> steps) {
        this.steps = steps;
    }

    public static void start(IEventBus modBus, IEventBus gameBus, String scriptPath) {
        List<String> lines;
        try {
            lines = Files.readAllLines(Path.of(scriptPath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.error("PRYZMA-SELFTEST cannot read script {}", scriptPath, e);
            return;
        }
        List<String> steps = new ArrayList<>();
        for (String line : lines) {
            String s = line.strip();
            if (!s.isEmpty() && !s.startsWith("#")) {
                steps.add(s);
            }
        }
        PrSelfTest test = new PrSelfTest(steps);
        gameBus.addListener(ClientTickEvent.Post.class, e -> test.tick());
        gameBus.addListener(RenderFrameEvent.Post.class, e -> test.frame());
        LOG.info("PRYZMA-SELFTEST loaded {} steps from {}", steps.size(), scriptPath);
    }

    private void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!titleSeen) {
            if (mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                titleSeen = true;
                LOG.info("PRYZMA-SELFTEST title screen reached");
            }
            return;
        }
        if (pendingShot != null || lightFrames > 0 || fpsFrames > 0 || f3Frames > 0 || compareStage > 0) {
            return;
        }
        if (reloading) {
            if (mc.getOverlay() == null) {
                reloading = false;
                LOG.info("PRYZMA-SELFTEST reload finished");
            }
            return;
        }
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        if (loadTicks > 0) {
            if (isLoaded(mc)) {
                LOG.info("PRYZMA-SELFTEST world loaded, {} sections rendered", mc.levelRenderer.countRenderedSections());
                loadTicks = 0;
            } else if (--loadTicks == 0) {
                fail("world did not finish loading in time");
            }
            return;
        }
        if (index >= steps.size()) {
            return;
        }
        String step = steps.get(index++);
        LOG.info("PRYZMA-SELFTEST step {}/{}: {}", index, steps.size(), step);
        try {
            run(mc, step);
        } catch (Exception e) {
            LOG.error("PRYZMA-SELFTEST step failed: {}", step, e);
            fail(e.toString());
        }
    }

    private void run(Minecraft mc, String step) throws Exception {
        String[] a = step.split("\\s+", 3);
        String arg = a.length > 1 ? step.substring(a[0].length()).strip() : "";
        switch (a[0]) {
            case "world" -> openWorld(mc, a[1], a.length < 3 || !"normal".equals(a[2]));
            case "loaded" -> loadTicks = LOAD_TIMEOUT_TICKS;
            case "wait" -> waitTicks = Integer.parseInt(a[1]);
            case "cmd" -> mc.player.connection.sendCommand(arg);
            case "look" -> {
                mc.player.setYRot(Float.parseFloat(a[1]));
                mc.player.setXRot(Float.parseFloat(a[2]));
                mc.player.yRotO = mc.player.getYRot();
                mc.player.xRotO = mc.player.getXRot();
            }
            case "set" -> setField(a[1], a[2]);
            case "pack" -> {
                selectPack(mc, arg, true);
                reloading = mc.getOverlay() != null;
            }
            case "unpack" -> {
                selectPack(mc, arg, false);
                reloading = mc.getOverlay() != null;
            }
            case "reload" -> {
                mc.reloadResourcePacks();
                reloading = true;
            }
            case "gui" -> mc.options.hideGui = "off".equals(a[1]);
            case "allchanged" -> mc.levelRenderer.allChanged();
            case "shot" -> pendingShot = a[1];
            case "lightstats" -> {
                lightFrames = Integer.parseInt(a[1]);
                lightPrevious = null;
                lightMaxDelta = 0;
                lightSumDelta = 0;
                lightSamples = 0;
            }
            case "log" -> LOG.info("PRYZMA-SELFTEST {}", arg);
            case "screen" -> mc.setScreen(switch (arg) {
                case "shaders" -> new ShaderPackScreen(mc.screen);
                case "pause" -> new PauseScreen(true);
                case "options" -> new net.minecraft.client.gui.screens.options.OptionsScreen(mc.screen, mc.options);
                default -> new VideoSettingsScreen(mc.screen, mc, mc.options);
            });
            case "press" -> click(mc, arg, 0);
            case "rpress" -> click(mc, arg, 1);
            case "mouse" -> {
                double scale = mc.getWindow().getGuiScale();
                GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), Double.parseDouble(a[1]) * scale, Double.parseDouble(a[2]) * scale);
            }
            case "close" -> mc.screen.onClose();
            case "dump" -> dump(mc);
            case "zoom" -> PrZoom.KEY.setDown("on".equals(a[1]));
            case "use" -> mc.options.keyUse.setDown("on".equals(a[1]));
            case "slot" -> mc.player.getInventory().selected = Integer.parseInt(a[1]);
            case "view" -> mc.options.setCameraType(switch (a[1]) {
                case "back" -> CameraType.THIRD_PERSON_BACK;
                case "front" -> CameraType.THIRD_PERSON_FRONT;
                default -> CameraType.FIRST_PERSON;
            });
            case "inventory" -> mc.setScreen(new InventoryScreen(mc.player));
            case "shaderpack" -> {
                PryzmaShaders.getShaderConfig().setShaderPackName(arg.equals("OFF") ? null : arg);
                PryzmaShaders.getShaderConfig().setShadersEnabled(!arg.equals("OFF"));
                PryzmaShaders.getShaderConfig().save();
                PryzmaShaders.reload();
                if (!arg.equals("OFF") && !PryzmaShaders.isPackInUseQuick()) {
                    throw new IllegalStateException("shader pack " + arg + " did not load");
                }
            }
            case "shaderinfo" -> {
                List<String> lines = new ArrayList<>();
                PryzmaShaders.getPipelineManager().getPipeline().ifPresent(p -> p.addDebugText(lines));
                LOG.info("PRYZMA-SELFTEST shader pack={} inUse={} fallback={} sections={}", PryzmaShaders.getCurrentPackName(),
                        PryzmaShaders.isPackInUseQuick(), PryzmaShaders.isFallback(), mc.levelRenderer.getSectionStatistics());
                lines.forEach(l -> LOG.info("PRYZMA-SELFTEST shader {}", l));
            }
            case "detailblocks" -> {
                List<String> detail = new ArrayList<>();
                for (Block block : BuiltInRegistries.BLOCK) {
                    int states = 0, culled = 0;
                    for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                        states++;
                        if (PrDetailClassifier.isDetail(state, mc.level, BlockPos.ZERO)) {
                            culled++;
                        }
                    }
                    if (culled > 0) {
                        detail.add(BuiltInRegistries.BLOCK.getKey(block) + (culled < states ? "[" + culled + "/" + states + "]" : ""));
                    }
                }
                LOG.info("PRYZMA-SELFTEST detail blocks {}: {}", detail.size(), String.join(" ", detail));
            }
            case "fps" -> {
                fpsFrames = Integer.parseInt(a[1]);
                fpsSamples = 0;
                fpsTotal = 0;
                fpsWorst = 0;
                fpsLast = 0;
            }
            case "f3" -> {
                var debug = mc.getDebugOverlay();
                if ("charts".equals(a[1])) {
                    debug.toggleFpsCharts();
                } else if (debug.showDebugScreen() != "on".equals(a[1])) {
                    debug.toggleOverlay();
                }
            }
            case "f3cache" -> {
                f3Frames = Integer.parseInt(a[1]);
                f3Sampled = 0;
                f3Replays = 0;
                f3Builds = PrF3RenderCache.builds();
                f3Start = System.nanoTime();
                f3Last = 0;
                f3BuildNanos = 0;
                f3ReplayNanos = 0;
            }
            case "f3compare" -> {
                // The next frame draws the text through vanilla renderLines; frame() takes it from there.
                compareName = a[1];
                PrF3RenderCache.enabled = false;
                compareStage = 1;
            }
            case "guiscale" -> {
                mc.options.guiScale().set(Integer.parseInt(a[1]));
                mc.resizeDisplay();
            }
            case "vsync" -> {
                boolean on = "on".equals(a[1]);
                mc.options.enableVsync().set(on);
                if (!on) {
                    mc.options.framerateLimit().set(260);
                }
            }
            case "leave" -> {
                // As the pause menu's Save and Quit: back to the title screen.
                mc.level.disconnect();
                mc.disconnect(new TitleScreen());
            }
            case "nopause" -> {
                // A background window loses focus; keep the game running for screenshots.
                mc.options.pauseOnLostFocus = false;
                if (mc.screen instanceof PauseScreen) {
                    mc.setScreen(null);
                }
            }
            case "quit" -> {
                LOG.info("PRYZMA-SELFTEST finished");
                mc.stop();
            }
            default -> throw new IllegalArgumentException("unknown step " + a[0]);
        }
    }

    private void frame() {
        if (lightFrames > 0) {
            sampleLightmap();
        }
        if (fpsFrames > 0) {
            sampleFrameTime();
        }
        if (f3Frames > 0) {
            sampleF3Cache();
        }
        if (compareStage > 0) {
            compareF3();
        }
        if (pendingShot == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Path dir = mc.gameDirectory.toPath().resolve("screenshots").resolve("selftest");
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Files.createDirectories(dir);
            Path file = dir.resolve(pendingShot + ".png");
            image.writeToFile(file);
            LOG.info("PRYZMA-SELFTEST screenshot {}", file);
        } catch (IOException e) {
            LOG.error("PRYZMA-SELFTEST screenshot failed", e);
        }
        pendingShot = null;
    }

    private void sampleFrameTime() {
        long now = System.nanoTime();
        if (fpsLast != 0) {
            long dt = now - fpsLast;
            fpsTotal += dt;
            fpsWorst = Math.max(fpsWorst, dt);
            fpsSamples++;
        }
        fpsLast = now;
        if (--fpsFrames == 0 && fpsSamples > 0) {
            double mean = fpsTotal / 1e6 / fpsSamples;
            LOG.info("PRYZMA-SELFTEST fps frames={} mean={} ms ({} fps) worst={} ms", fpsSamples, String.format("%.3f", mean),
                    String.format("%.1f", 1000.0 / mean), String.format("%.3f", fpsWorst / 1e6));
        }
    }

    /** Each frame's decision, read after it was drawn, and its frame time (the first frame has none). */
    private void sampleF3Cache() {
        long now = System.nanoTime();
        boolean replayed = PrF3RenderCache.replaying();
        if (f3Last != 0) {
            f3Sampled++;
            if (replayed) {
                f3Replays++;
                f3ReplayNanos += now - f3Last;
            } else {
                f3BuildNanos += now - f3Last;
            }
        }
        f3Last = now;
        if (--f3Frames == 0) {
            double seconds = (now - f3Start) / 1e9;
            int builds = PrF3RenderCache.builds() - f3Builds;
            int built = f3Sampled - f3Replays;
            LOG.info("PRYZMA-SELFTEST f3cache frames={} replayed={} builds={} ({}/s over {} s) mean frame: build {} ms, replay {} ms",
                    f3Sampled, f3Replays, builds, String.format("%.1f", builds / seconds), String.format("%.2f", seconds),
                    built == 0 ? "-" : String.format("%.3f", f3BuildNanos / 1e6 / built),
                    f3Replays == 0 ? "-" : String.format("%.3f", f3ReplayNanos / 1e6 / f3Replays));
        }
    }

    /**
     * Three consecutive frames: the cache off (vanilla renderLines), a build (drawn from the new vertex
     * buffers), a replay. Build and replay must match; vanilla and build differ only where the text changed.
     */
    private void compareF3() {
        Minecraft mc = Minecraft.getInstance();
        int stage = compareStage - 1;
        compareShots[stage] = Screenshot.takeScreenshot(mc.getMainRenderTarget());
        LOG.info("PRYZMA-SELFTEST f3compare {} frame {} enabled={} replaying={}", compareName, stage, PrF3RenderCache.enabled,
                PrF3RenderCache.replaying());
        if (stage == 0) {
            PrF3RenderCache.enabled = true;
            PrF3RenderCache.invalidate();
        }
        if (++compareStage <= 3) {
            return;
        }
        compareStage = 0;
        LOG.info("PRYZMA-SELFTEST f3compare {} vanilla-vs-build {} build-vs-replay {}", compareName,
                difference(compareShots[0], compareShots[1]), difference(compareShots[1], compareShots[2]));
        Path dir = mc.gameDirectory.toPath().resolve("screenshots").resolve("selftest");
        String[] names = {"vanilla", "build", "replay"};
        for (int i = 0; i < 3; i++) {
            try (NativeImage image = compareShots[i]) {
                Files.createDirectories(dir);
                image.writeToFile(dir.resolve(compareName + "_" + names[i] + ".png"));
            } catch (IOException e) {
                LOG.error("PRYZMA-SELFTEST f3compare screenshot failed", e);
            }
            compareShots[i] = null;
        }
    }

    /** Differing pixels and their bounding box. */
    private static String difference(NativeImage a, NativeImage b) {
        int count = 0;
        int x0 = Integer.MAX_VALUE;
        int y0 = Integer.MAX_VALUE;
        int x1 = -1;
        int y1 = -1;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (a.getPixelRGBA(x, y) != b.getPixelRGBA(x, y)) {
                    count++;
                    x0 = Math.min(x0, x);
                    y0 = Math.min(y0, y);
                    x1 = Math.max(x1, x);
                    y1 = Math.max(y1, y);
                }
            }
        }
        return count == 0 ? "0 px" : count + " px in x " + x0 + ".." + x1 + " y " + y0 + ".." + y1;
    }

    /** Frame-to-frame change of the uploaded lightmap: a strobing lightmap shows large maxima. */
    private void sampleLightmap() {
        try {
            Field f = LightTexture.class.getDeclaredField("lightPixels");
            f.setAccessible(true);
            NativeImage pixels = (NativeImage) f.get(Minecraft.getInstance().gameRenderer.lightTexture());
            int[] now = pixels.getPixelsRGBA();
            if (lightPrevious != null) {
                int frameMax = 0;
                for (int i = 0; i < now.length; i++) {
                    for (int shift = 0; shift < 24; shift += 8) {
                        frameMax = Math.max(frameMax, Math.abs((now[i] >> shift & 0xFF) - (lightPrevious[i] >> shift & 0xFF)));
                    }
                }
                lightMaxDelta = Math.max(lightMaxDelta, frameMax);
                lightSumDelta += frameMax;
                lightSamples++;
            }
            lightPrevious = now;
        } catch (ReflectiveOperationException e) {
            LOG.error("PRYZMA-SELFTEST lightstats unavailable", e);
            lightFrames = 0;
            return;
        }
        if (--lightFrames == 0) {
            LOG.info("PRYZMA-SELFTEST lightstats frames={} maxDelta={} meanDelta={}", lightSamples, lightMaxDelta,
                    lightSamples == 0 ? 0 : String.format("%.2f", lightSumDelta / (double) lightSamples));
        }
    }

    private static boolean isLoaded(Minecraft mc) {
        return mc.level != null && mc.player != null && mc.screen == null && mc.getOverlay() == null
                && mc.levelRenderer.hasRenderedAllSections();
    }

    /** Clicks through the screen's own mouse handling, so the whole press path runs. */
    private static void click(Minecraft mc, String label, int button) {
        AbstractWidget target = null;
        for (GuiEventListener child : mc.screen.children()) {
            if (child instanceof AbstractWidget w && w.getMessage().getString().startsWith(label)) {
                target = w;
                break;
            }
        }
        if (target == null) {
            throw new IllegalArgumentException("no widget starting with '" + label + "' on " + mc.screen.getClass().getSimpleName());
        }
        double x = target.getX() + target.getWidth() / 2.0;
        double y = target.getY() + target.getHeight() / 2.0;
        mc.screen.mouseClicked(x, y, button);
        if (mc.screen != null) {
            mc.screen.mouseReleased(x, y, button);
        }
    }

    private static void dump(Minecraft mc) {
        Screen screen = mc.screen;
        LOG.info("PRYZMA-SELFTEST dump {} {}x{} title='{}'", screen.getClass().getSimpleName(), screen.width, screen.height,
                screen.getTitle().getString());
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget w) {
                String id = "";
                try {
                    Field f = w.getClass().getDeclaredField("id");
                    f.setAccessible(true);
                    id = String.valueOf(f.get(w));
                } catch (ReflectiveOperationException ignored) {
                    // not an id-carrying button
                }
                LOG.info("PRYZMA-SELFTEST widget {} id={} x={} y={} w={} h={} active={} text='{}'", w.getClass().getSimpleName(),
                        id, w.getX(), w.getY(), w.getWidth(), w.getHeight(), w.active, w.getMessage().getString());
            }
        }
    }

    private static void openWorld(Minecraft mc, String name, boolean flat) {
        if (mc.getLevelSource().levelExists(name)) {
            mc.createWorldOpenFlows().openWorld(name, () -> fail("could not open " + name));
            return;
        }
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules,
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(20260924L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(flat ? WorldPresets.FLAT : WorldPresets.NORMAL).value().createWorldDimensions(),
                mc.screen);
    }

    private static void selectPack(Minecraft mc, String id, boolean enable) {
        PackRepository repo = mc.getResourcePackRepository();
        repo.reload();
        if (repo.getPack(id) == null) {
            throw new IllegalArgumentException("no resource pack " + id + ", available: " + repo.getAvailableIds());
        }
        List<String> selected = new ArrayList<>(repo.getSelectedIds());
        if (selected.contains(id) != enable) {
            if (enable) {
                selected.add(id);
            } else {
                selected.remove(id);
            }
            repo.setSelected(selected);
            mc.options.updateResourcePacks(repo);
        }
    }

    private static void setField(String name, String value) throws ReflectiveOperationException {
        Field f = PryzmaConfig.class.getField(name);
        Class<?> t = f.getType();
        if (t == boolean.class) {
            f.setBoolean(null, Boolean.parseBoolean(value));
        } else if (t == int.class) {
            f.setInt(null, Integer.parseInt(value));
        } else if (t == float.class) {
            f.setFloat(null, Float.parseFloat(value));
        } else if (t == double.class) {
            f.setDouble(null, Double.parseDouble(value));
        } else {
            throw new IllegalArgumentException("unsupported field type " + t);
        }
    }

    private static void fail(String reason) {
        LOG.error("PRYZMA-SELFTEST FAILED: {}", reason);
        Minecraft.getInstance().stop();
    }
}
