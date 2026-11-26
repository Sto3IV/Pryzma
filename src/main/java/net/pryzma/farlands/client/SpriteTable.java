package net.pryzma.farlands.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;

public final class SpriteTable {
   public static final int MAX = 16384;
   private static final Field MIPS = mipField();
   private static final Identifier WATER_STILL = Identifier.withDefaultNamespace("block/water_still");
   private static final Identifier LOD_WATER = Identifier.fromNamespaceAndPath("pryzma", "block/farlands_water");
   public static final Identifier LOD_WATER_TILED = Identifier.fromNamespaceAndPath("pryzma", "block/lod_water_tiled");
   private final Map<TextureAtlasSprite, Integer> indices = new ConcurrentHashMap<>();
   private final Map<Long, Integer> layers = new ConcurrentHashMap<>();
   private final float[] data = new float[131072];
   private final float[] coverage = new float[16384];
   private final float[] flats = new float[65536];
   private final boolean[] water = new boolean[16384];
   private final float[] tiledRect = new float[4];
   private boolean tiledReady;
   private volatile int count;
   private volatile int version;

   public int index(TextureAtlasSprite sprite) {
      Integer known = this.indices.get(sprite);
      if (known != null) {
         return known;
      }

      synchronized (this) {
         Integer again = this.indices.get(sprite);
         if (again != null) {
            return again;
         }

         if (this.count >= 16384) {
            return 0;
         }

         int index = this.count;
         this.fill(index, sprite);
         this.indices.put(sprite, index);
         this.count = index + 1;
         this.version++;
         return index;
      }
   }

   public int layered(int base, int overlay) {
      long key = (long)base << 32 | overlay & 4294967295L;
      Integer known = this.layers.get(key);
      if (known != null) {
         return known;
      }

      synchronized (this) {
         Integer again = this.layers.get(key);
         if (again != null) {
            return again;
         }

         if (this.count >= 16384) {
            return base;
         }

         int index = this.count;
         System.arraycopy(this.data, base * 8, this.data, index * 8, 7);
         this.data[index * 8 + 7] = 4.0F + overlay + Math.min(0.995F, this.coverage[overlay]) * 0.5F;
         this.coverage[index] = 1.0F;
         this.layers.put(key, index);
         this.count = index + 1;
         this.version++;
         return index;
      }
   }

   private void fillFlat(int index, TextureAtlasSprite sprite) {
      TextureAtlasSprite source = sprite;
      if (WATER_STILL.equals(sprite.contents().name())) {
         try {
            TextureAtlas atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
            TextureAtlasSprite flat = atlas.getSprite(LOD_WATER);
            if (flat != atlas.missingSprite()) {
               source = flat;
            }

            TextureAtlasSprite tiled = atlas.getSprite(LOD_WATER_TILED);
            if (tiled != atlas.missingSprite()) {
               this.tiledRect[0] = tiled.getU0();
               this.tiledRect[1] = tiled.getV0();
               this.tiledRect[2] = tiled.getU1();
               this.tiledRect[3] = tiled.getV1();
               this.tiledReady = true;
               this.water[index] = true;
            }
         } catch (RuntimeException ignored) {
            source = sprite;
         }
      }

      int width = Math.max(1, source.contents().width());
      int height = Math.max(1, source.contents().height());
      float texelU = (source.getU1() - source.getU0()) / width;
      float texelV = (source.getV1() - source.getV0()) / height;
      int base = index * 4;
      this.flats[base] = source.getU0() + (width / 2 + 0.25F) * texelU;
      this.flats[base + 1] = source.getV0() + (height / 2 + 0.25F) * texelV;
      this.flats[base + 2] = source.getU0() + (width / 2 + 0.75F) * texelU;
      this.flats[base + 3] = source.getV0() + (height / 2 + 0.75F) * texelV;
   }

   private void fill(int index, TextureAtlasSprite sprite) {
      this.fillFlat(index, sprite);
      int base = index * 8;
      this.data[base] = sprite.getU0();
      this.data[base + 1] = sprite.getV0();
      this.data[base + 2] = sprite.getU1();
      this.data[base + 3] = sprite.getV1();
      double r = 0.0;
      double g = 0.0;
      double b = 0.0;
      double weight = 0.0;
      double alpha = 0.0;
      int opaque = 0;
      int transparent = 0;
      int width = 16;
      int height = 16;

      try {
         width = sprite.contents().width();
         height = sprite.contents().height();
         NativeImage image = firstMip(sprite);

         for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
               int argb = image.getPixel(x, y);
               int a = argb >>> 24;
               if (a < 128) {
                  transparent++;
               } else {
                  double w = a / 255.0;
                  alpha += w;
                  opaque++;
                  r += srgbToLinear(argb >> 16 & 0xFF) * w;
                  g += srgbToLinear(argb >> 8 & 0xFF) * w;
                  b += srgbToLinear(argb & 0xFF) * w;
                  weight += w;
               }
            }
         }
      } catch (RuntimeException | IllegalAccessException var25) {
      }

      if (weight <= 0.0) {
         this.data[base + 4] = 0.5F;
         this.data[base + 5] = 0.5F;
         this.data[base + 6] = 0.5F;
      } else {
         this.data[base + 4] = (float)linearToSrgb(r / weight);
         this.data[base + 5] = (float)linearToSrgb(g / weight);
         this.data[base + 6] = (float)linearToSrgb(b / weight);
      }

      this.data[base + 7] = transparent > 0 ? 2.0F : (opaque > 0 ? (float)(alpha / opaque) : 1.0F);
      this.coverage[index] = opaque + transparent > 0 ? (float)opaque / (opaque + transparent) : 1.0F;
   }

   private static double srgbToLinear(int value) {
      double c = value / 255.0;
      return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
   }

   private static double linearToSrgb(double c) {
      return c <= 0.0031308 ? c * 12.92 : 1.055 * Math.pow(c, 0.4166666666666667) - 0.055;
   }

   private static Field mipField() {
      try {
         Field field = SpriteContents.class.getDeclaredField("byMipLevel");
         field.setAccessible(true);
         return field;
      } catch (ReflectiveOperationException | RuntimeException e) {
         return null;
      }
   }

   private static NativeImage firstMip(TextureAtlasSprite sprite) throws IllegalAccessException {
      if (MIPS == null) {
         throw new IllegalStateException("No sprite pixels");
      } else {
         return ((NativeImage[])MIPS.get(sprite.contents()))[0];
      }
   }

   public boolean hasTransparency(int index) {
      float marker = this.data[index * 8 + 7];
      return marker > 1.5F && marker < 3.5F;
   }

   public synchronized boolean tiled(int index, float[] out) {
      if (this.tiledReady && this.water[index]) {
         System.arraycopy(this.tiledRect, 0, out, 0, 4);
         return true;
      } else {
         return false;
      }
   }

   public synchronized boolean waterData(FloatBuffer out, int tiledSlot) {
      int count = this.count;
      out.put(this.flats, 0, count * 4);
      out.position(tiledSlot * 4);
      out.put(this.tiledRect, 0, 4);
      return this.tiledReady;
   }

   public synchronized void rect(int index, float[] out) {
      int base = index * 4;
      out[0] = this.flats[base];
      out[1] = this.flats[base + 1];
      out[2] = this.flats[base + 2];
      out[3] = this.flats[base + 3];
   }

   public int count() {
      return this.count;
   }

   public int version() {
      return this.version;
   }

   public float[] data() {
      return this.data;
   }

   public synchronized void clear() {
      Arrays.fill(this.water, false);
      this.tiledReady = false;
      this.indices.clear();
      this.layers.clear();
      this.count = 0;
      this.version++;
   }
}
