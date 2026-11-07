package net.pryzma;

import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RegisterSpriteSourcesEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.pryzma.command.PrCommands;
import net.pryzma.world.PrPregenTask;
import net.pryzma.core.PrReloadListener;
import net.pryzma.core.expr.PrExprEnv;
import net.pryzma.entity.PrEntityInfos;
import net.pryzma.entity.model.PrCemContext;
import net.pryzma.ctm.PrCtmSpriteSource;
import net.pryzma.ctm.PryzmaCtm;
import net.pryzma.gui.PrQuickInfo;
import net.pryzma.gui.PrVideoSettingsScreen;
import net.pryzma.gui.PrZoom;
import net.pryzma.shader.platform.ShaderForgeHelpers;
import net.pryzma.sky.PrSkyBlend;
import net.pryzma.item.PrCitModels;
import net.pryzma.item.PrCitSpriteSource;
import net.pryzma.lod.PryzmaLod;
import net.pryzma.mixin.OptionsSubScreenAccessor;
import net.pryzma.perf.PrDebugTracker;
import net.pryzma.perf.PrServerPriority;
import net.pryzma.render.PrBetterGrass;
import net.pryzma.render.PrBetterSnow;
import net.pryzma.render.PrRenderHooks;
import net.pryzma.render.PrSmartLeaves;
import net.pryzma.world.PrWorldControl;

/** Registers the engines on the mod and game event buses. */
public final class PryzmaClient {
    private PryzmaClient() {
    }

    static void register(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(AddClientReloadListenersEvent.class,
                event -> event.addListener(Identifier.fromNamespaceAndPath("pryzma", "reload_listener"), new PrReloadListener()));
        modBus.addListener(RegisterSpriteSourcesEvent.class, event -> {
            event.register(PrCtmSpriteSource.ID, PrCtmSpriteSource.CODEC);
            event.register(PrCitSpriteSource.ID, PrCitSpriteSource.CODEC);
        });
        modBus.addListener(RegisterRenderPipelinesEvent.class, PrSkyBlend::registerPipelines);
        modBus.addListener(ModelEvent.ModifyBakingResult.class, PryzmaCtm::onModifyBakingResult);
        modBus.addListener(ModelEvent.ModifyBakingResult.class, PrSmartLeaves::onModifyBakingResult);
        modBus.addListener(ModelEvent.ModifyBakingResult.class, PrCitModels::onModifyBakingResult);
        modBus.addListener(ModelEvent.ModifyBakingResult.class, event -> {
            PrBetterGrass.clearCaches();
            PrBetterSnow.clearCaches();
            net.pryzma.render.PrNaturalTextures.clearCaches();
        });
        modBus.addListener(ModelEvent.BakingCompleted.class, PrCitModels::onBakingCompleted);
        modBus.addListener(RegisterKeyMappingsEvent.class, PrZoom::onRegisterKeys);
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            ShaderForgeHelpers.KEYLIST.forEach(event::register);
            ShaderForgeHelpers.KEYLIST.clear();
        });
        modBus.addListener(RegisterGuiLayersEvent.class, event -> event.registerAboveAll(
                Identifier.fromNamespaceAndPath(Pryzma.MODID, "quick_info"), PrQuickInfo::render));
        gameBus.addListener(ViewportEvent.ComputeFov.class, PrZoom::onComputeFov);
        gameBus.addListener(ScreenEvent.Opening.class, PryzmaClient::onScreenOpening);
        gameBus.addListener(ServerStartedEvent.class, event -> PrServerPriority.apply());
        gameBus.addListener(ServerTickEvent.Post.class, PrWorldControl::onServerTick);
        gameBus.addListener(ViewportEvent.RenderFog.class, PrRenderHooks::onRenderFog);
        gameBus.addListener(RenderLevelStageEvent.AfterSky.class, PrRenderHooks::onRenderStageAfterSky);
        gameBus.addListener(RenderFrameEvent.Pre.class, PrQuickInfo::onFrameStart);
        gameBus.addListener(RenderFrameEvent.Post.class, PrQuickInfo::onFrameEnd);
        gameBus.addListener(RenderFrameEvent.Pre.class, event -> {
            PrDebugTracker.tick();
            PrExprEnv.MinecraftEnv.onFrame();
            PrCemContext.onFrame();
        });
        gameBus.addListener(EntityJoinLevelEvent.class, event -> {
            if (event.getLevel().isClientSide()) {
                PrEntityInfos.recordSpawn(event.getEntity(), event.getLevel());
            }
        });
        PryzmaLod.register(modBus, gameBus);
        gameBus.addListener(RegisterCommandsEvent.class, PrCommands::onRegisterCommands);
        gameBus.addListener(ServerStoppingEvent.class, event -> PrPregenTask.stopAll());
    }

    /** The vanilla video settings are replaced by the Pryzma screen, which returns to the same parent. */
    private static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof VideoSettingsScreen vanilla) {
            event.setNewScreen(new PrVideoSettingsScreen(((OptionsSubScreenAccessor) vanilla).prGetLastScreen()));
        }
    }
}
