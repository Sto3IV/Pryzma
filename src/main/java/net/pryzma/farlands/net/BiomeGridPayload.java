package net.pryzma.farlands.net;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;

public record BiomeGridPayload(int id, int minQuart, List<String> palette, byte[] packed) implements CustomPacketPayload {
   public static final int MAX_PACKED = 1048576;
   public static final Type<BiomeGridPayload> TYPE = new Type(Identifier.fromNamespaceAndPath("pryzma", "biome_grid"));
   public static final StreamCodec<FriendlyByteBuf, BiomeGridPayload> CODEC = CustomPacketPayload.codec(BiomeGridPayload::write, BiomeGridPayload::new);

   private BiomeGridPayload(FriendlyByteBuf buf) {
      this(buf.readVarInt(), buf.readVarInt(), readPalette(buf), buf.readByteArray(1048576));
   }

   private static List<String> readPalette(FriendlyByteBuf buf) {
      int count = buf.readVarInt();
      if (count >= 0 && count <= 256) {
         List<String> palette = new ArrayList<>(count);

         for (int i = 0; i < count; i++) {
            palette.add(buf.readUtf(256));
         }

         return palette;
      } else {
         throw new IllegalArgumentException("Biome palette too large: " + count);
      }
   }

   public static BiomeGridPayload empty(int id) {
      return new BiomeGridPayload(id, 0, List.of(), new byte[0]);
   }

   public BiomeGridPayload withId(int requestId) {
      return new BiomeGridPayload(requestId, this.minQuart, this.palette, this.packed);
   }

   private void write(FriendlyByteBuf buf) {
      buf.writeVarInt(this.id);
      buf.writeVarInt(this.minQuart);
      buf.writeVarInt(this.palette.size());

      for (String entry : this.palette) {
         buf.writeUtf(entry, 256);
      }

      buf.writeByteArray(this.packed);
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
