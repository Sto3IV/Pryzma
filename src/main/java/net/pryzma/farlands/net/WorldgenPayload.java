package net.pryzma.farlands.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;

public record WorldgenPayload(int session, int index, int total, byte[] data) implements CustomPacketPayload {
   public static final int PART = 262144;
   public static final Type<WorldgenPayload> TYPE = new Type(Identifier.fromNamespaceAndPath("pryzma", "worldgen"));
   public static final StreamCodec<RegistryFriendlyByteBuf, WorldgenPayload> CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_INT,
      WorldgenPayload::session,
      ByteBufCodecs.VAR_INT,
      WorldgenPayload::index,
      ByteBufCodecs.VAR_INT,
      WorldgenPayload::total,
      ByteBufCodecs.byteArray(262144),
      WorldgenPayload::data,
      WorldgenPayload::new
   );

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
