package net.pryzma.farlands.neoforge;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.MapCodec;
import net.pryzma.farlands.client.SpriteTable;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSource.DiscardableLoader;
import net.minecraft.client.renderer.texture.atlas.SpriteSource.Output;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.metadata.MetadataSectionType.WithValue;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.Nullable;

public final class TiledWaterSource implements SpriteSource {
   public static final Identifier TYPE = Identifier.fromNamespaceAndPath("pryzma", "tiled_water");
   public static final MapCodec<TiledWaterSource> CODEC = MapCodec.unit(TiledWaterSource::new);
   private static final Identifier WATER = Identifier.withDefaultNamespace("block/water_still");
   private static final int REPEAT = 16;
   private static final int MAX_FRAME = 32;

   public void run(ResourceManager manager, Output output) {
      Optional<Resource> resource = manager.getResource(TEXTURE_ID_CONVERTER.idToFile(WATER));
      if (!resource.isEmpty()) {
         final Resource water = resource.get();
         output.add(SpriteTable.LOD_WATER_TILED, new DiscardableLoader() {
            public @Nullable SpriteContents get(SpriteResourceLoader loader) {
               return loader.loadSprite(SpriteTable.LOD_WATER_TILED, water, TiledWaterSource::tile);
            }
         });
      }
   }

   public MapCodec<? extends SpriteSource> codec() {
      return CODEC;
   }

   private static @Nullable SpriteContents tile(
      Identifier id,
      FrameSize size,
      NativeImage image,
      Optional<AnimationMetadataSection> animation,
      List<WithValue<?>> extra,
      Optional<TextureMetadataSection> texture
   ) {
      int width = size.width();
      int height = size.height();
      if (width <= 32 && height <= 32) {
         int fullWidth = image.getWidth() * 16;
         int fullHeight = image.getHeight() * 16;
         NativeImage tiled = new NativeImage(image.format(), fullWidth, fullHeight, false);

         for (int y = 0; y < fullHeight; y++) {
            int sourceY = y / (height * 16) * height + y % height;

            for (int x = 0; x < fullWidth; x++) {
               int sourceX = x / (width * 16) * width + x % width;
               tiled.setPixel(x, y, image.getPixel(sourceX, sourceY));
            }
         }

         image.close();
         return new SpriteContents(id, new FrameSize(width * 16, height * 16), tiled, animation, extra, texture);
      } else {
         image.close();
         return null;
      }
   }
}
