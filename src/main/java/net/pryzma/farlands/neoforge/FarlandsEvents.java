package net.pryzma.farlands.neoforge;

import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.PrepareRenderBuffersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RegisterSpriteSourcesEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DataPackRegistriesHooks;
import net.pryzma.farlands.LoaderHooks;
import net.pryzma.farlands.client.FarlandsClient;
import net.pryzma.farlands.client.FarlandsPipelines;
import net.pryzma.farlands.client.RemoteBiomes;
import net.pryzma.farlands.client.RemoteWorldgen;
import net.pryzma.farlands.net.BiomeGridPayload;
import net.pryzma.farlands.net.BiomeRequestPayload;
import net.pryzma.farlands.net.BiomeService;
import net.pryzma.farlands.net.LimitsPayload;
import net.pryzma.farlands.net.WorldgenExport;
import net.pryzma.farlands.net.WorldgenPayload;

public final class FarlandsEvents {
    private FarlandsEvents() {
    }

    public static void registerClient(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(FarlandsEvents::onPipelines);
        modBus.addListener(FarlandsEvents::onAtlas);
        modBus.addListener(FarlandsEvents::onSpriteSources);
        modBus.addListener(FarlandsEvents::onClientPayloads);
        LoaderHooks.clientSender = payload -> ClientPacketDistributor.sendToServer(payload, new CustomPacketPayload[0]);

        gameBus.addListener(FarlandsEvents::onTick);
        gameBus.addListener(FarlandsEvents::onChunkLoad);
        gameBus.addListener(FarlandsEvents::onChunkUnload);
        gameBus.addListener(FarlandsEvents::onPrepare);
        gameBus.addListener(FarlandsEvents::onOpaque);
        gameBus.addListener(FarlandsEvents::onTranslucent);
        gameBus.addListener(EventPriority.LOWEST, FarlandsEvents::onFog);
    }

    public static void registerCommon(IEventBus modBus, IEventBus gameBus) {
        LoaderHooks.biomeEncoder = NeoForgeBiomes::encode;
        LoaderHooks.worldRegistries = () -> DataPackRegistriesHooks.getWorldRegistriesWithDimensions()
                .filter(data -> !RegistryDataLoader.DIMENSION_REGISTRIES.contains(data))
                .toList();
        LoaderHooks.biomeModifiers = NeoForgeBiomes::applyModifiers;
        LoaderHooks.canReceive = (player, type) -> player.connection.hasChannel(type);
        LoaderHooks.sender = (player, payload) -> PacketDistributor.sendToPlayer(player, payload, new CustomPacketPayload[0]);

        modBus.addListener(FarlandsEvents::onCommonPayloads);
        gameBus.addListener(FarlandsEvents::onLogin);
        gameBus.addListener(FarlandsEvents::onStopped);
    }

    private static void onClientPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(WorldgenPayload.TYPE, (payload, context) -> RemoteWorldgen.receive(payload));
        event.register(LimitsPayload.TYPE, (payload, context) -> RemoteWorldgen.receiveLimits(payload));
        event.register(BiomeGridPayload.TYPE, HandlerThread.NETWORK, (payload, context) -> RemoteBiomes.receive(payload));
    }

    private static void onCommonPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(WorldgenPayload.TYPE, WorldgenPayload.CODEC);
        registrar.playToClient(LimitsPayload.TYPE, LimitsPayload.CODEC);
        registrar.playToClient(BiomeGridPayload.TYPE, BiomeGridPayload.CODEC);
        registrar.playToServer(BiomeRequestPayload.TYPE, BiomeRequestPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                BiomeService.onRequest(player, payload);
            }
        });
    }

    private static void onPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(FarlandsPipelines.TERRAIN);
        event.registerPipeline(FarlandsPipelines.TERRAIN_CLIP);
        event.registerPipeline(FarlandsPipelines.FLUID);
        event.registerPipeline(FarlandsPipelines.FLUID_CLIP);
        event.registerPipeline(FarlandsPipelines.WATER);
        event.registerPipeline(FarlandsPipelines.WATER_MULTIDRAW);
        event.registerPipeline(FarlandsPipelines.TERRAIN_COMPACT);
        event.registerPipeline(FarlandsPipelines.TERRAIN_COMPACT_CLIP);
    }

    private static void onSpriteSources(RegisterSpriteSourcesEvent event) {
        event.register(TiledWaterSource.TYPE, TiledWaterSource.CODEC);
    }

    private static void onAtlas(TextureAtlasStitchedEvent event) {
        if (event.getAtlas().location().equals(TextureAtlas.LOCATION_BLOCKS)) {
            FarlandsClient client = FarlandsClient.peek();
            if (client != null) {
                client.onResourcesReloaded();
            }
        }
    }

    private static void onTick(ClientTickEvent.Post event) {
        FarlandsClient.get().tick();
    }

    private static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide() && event.getChunk() instanceof LevelChunk chunk) {
            FarlandsClient.get().onChunkLoad(chunk);
        }
    }

    private static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel().isClientSide() && event.getChunk() instanceof LevelChunk chunk) {
            FarlandsClient.get().onChunkUnload(chunk);
        }
    }

    private static void onPrepare(PrepareRenderBuffersEvent event) {
        FarlandsClient.get().prepare(event.getLevelRenderState().cameraRenderState);
    }

    private static void onOpaque(RenderLevelStageEvent.AfterOpaqueBlocks event) {
        FarlandsClient.get().render(event.getRenderPass());
    }

    private static void onTranslucent(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        FarlandsClient.get().renderFluid(event.getRenderPass());
    }

    private static void onFog(ViewportEvent.RenderFog event) {
        FarlandsClient client = FarlandsClient.peek();
        if (client != null) {
            client.adjustFog(event.getFogData(), event.getType(), event.getEnvironment() instanceof AtmosphericFogEnvironment);
        }
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WorldgenExport.onJoin(player);
        }
    }

    private static void onStopped(ServerStoppedEvent event) {
        WorldgenExport.onStopped(event.getServer());
        BiomeService.onStopped(event.getServer());
    }
}
