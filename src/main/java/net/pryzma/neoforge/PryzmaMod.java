package net.pryzma.neoforge;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@Mod(value = PryzmaMod.MODID, dist = Dist.CLIENT)
public class PryzmaMod {
    public static final String MODID = "pryzma";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PryzmaMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Pryzma NeoForge adapter constructing (modId={})", MODID);
        modEventBus.addListener(this::onClientSetup);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        LOGGER.info("Pryzma client setup; transformer present={}",
                Boolean.valueOf(PryzmaTransformationService.getTransformer() != null));
    }
}
