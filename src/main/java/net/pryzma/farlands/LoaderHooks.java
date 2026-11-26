package net.pryzma.farlands;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryDataLoader.RegistryData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;

public final class LoaderHooks {
   public static volatile BiFunction<Biome, DynamicOps<Tag>, DataResult<Tag>> biomeEncoder = (biome, ops) -> Biome.DIRECT_CODEC.encodeStart(ops, biome);
   public static volatile Supplier<List<RegistryData<?>>> worldRegistries = () -> RegistryDataLoader.WORLD_REGISTRIES;
   public static volatile Consumer<RegistryAccess> biomeModifiers = access -> {};
   public static volatile BiPredicate<ServerPlayer, Type<?>> canReceive = (player, type) -> false;
   public static volatile BiConsumer<ServerPlayer, CustomPacketPayload> sender = (player, payload) -> {};
   public static volatile Consumer<CustomPacketPayload> clientSender = payload -> {};

   private LoaderHooks() {
   }
}
