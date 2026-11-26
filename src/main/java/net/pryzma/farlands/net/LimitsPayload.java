package net.pryzma.farlands.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;

public record LimitsPayload(int maxLodDistance, int tilesPerSecond) implements CustomPacketPayload {
   public static final Type<LimitsPayload> TYPE = new Type(Identifier.fromNamespaceAndPath("pryzma", "limits"));
   public static final StreamCodec<RegistryFriendlyByteBuf, LimitsPayload> CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_INT, LimitsPayload::maxLodDistance, ByteBufCodecs.VAR_INT, LimitsPayload::tilesPerSecond, LimitsPayload::new
   );

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
