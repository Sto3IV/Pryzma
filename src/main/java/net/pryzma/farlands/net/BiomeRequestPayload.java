package net.pryzma.farlands.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;

public record BiomeRequestPayload(int id, String dimension, int level, int tx, int tz) implements CustomPacketPayload {
   public static final Type<BiomeRequestPayload> TYPE = new Type(Identifier.fromNamespaceAndPath("pryzma", "biome_request"));
   public static final StreamCodec<FriendlyByteBuf, BiomeRequestPayload> CODEC = CustomPacketPayload.codec(BiomeRequestPayload::write, BiomeRequestPayload::new);

   private BiomeRequestPayload(FriendlyByteBuf buf) {
      this(buf.readVarInt(), buf.readUtf(256), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
   }

   private void write(FriendlyByteBuf buf) {
      buf.writeVarInt(this.id);
      buf.writeUtf(this.dimension, 256);
      buf.writeVarInt(this.level);
      buf.writeVarInt(this.tx);
      buf.writeVarInt(this.tz);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
