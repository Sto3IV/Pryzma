package net.pryzma;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.MixinEnvironment;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Pryzma 2.0 entry point. Every vanilla touch point is a Sponge Mixin declared in
 * {@code pryzma.mixins.json}; this class only loads the settings and wires the engines to the
 * NeoForge event buses.
 */
@Mod(value = Pryzma.MODID, dist = Dist.CLIENT)
public final class Pryzma {
    public static final String MODID = "pryzma";
    public static final Logger LOGGER = LoggerFactory.getLogger("Pryzma");

    public Pryzma(IEventBus modBus, ModContainer container) {
        PryzmaConfig.load(FMLPaths.GAMEDIR.get());
        modBus.addListener(Pryzma::prOnClientSetup);
        PryzmaClient.register(modBus, NeoForge.EVENT_BUS);
        prStartSelfTest(modBus);
        LOGGER.info("Pryzma {} initialised", container.getModInfo().getVersion());
    }

    /** Development runs only: the harness lives in the unpackaged {@code dev} source set. */
    private static void prStartSelfTest(IEventBus modBus) {
        String script = System.getProperty("pryzma.selftest");
        if (script == null) {
            return;
        }
        try {
            Class.forName("net.pryzma.dev.PrSelfTest")
                    .getMethod("start", IEventBus.class, IEventBus.class, String.class)
                    .invoke(null, modBus, NeoForge.EVENT_BUS, script);
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Pryzma self-test requested but unavailable", e);
        }
    }

    private static void prOnClientSetup(FMLClientSetupEvent event) {
        // Development switch: load every mixin target now so that a broken injection point
        // fails at startup instead of the first time the class happens to be used.
        if (Boolean.getBoolean("pryzma.audit")) {
            event.enqueueWork(() -> {
                LOGGER.info("Pryzma mixin audit: loading all mixin targets");
                MixinEnvironment.getCurrentEnvironment().audit();
            });
        }
    }
}
