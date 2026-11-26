package net.pryzma.farlands.client;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice.MappedView;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.jspecify.annotations.Nullable;

public final class SkyFog {
   private static @Nullable MappableRingBuffer ring;
   private static float environmentalStart;
   private static float environmentalEnd;
   private static float renderDistanceStart;
   private static float renderDistanceEnd;
   private static boolean pending;
   static volatile boolean legacy;
   private static volatile float finalEnvironmentalStart = Float.NaN;
   private static volatile float finalEnvironmentalEnd = Float.NaN;

   private SkyFog() {
   }

   static boolean environmentSetByOthers() {
      return !Float.isNaN(finalEnvironmentalEnd)
         && (Math.abs(finalEnvironmentalStart - environmentalStart) > 1.0F || Math.abs(finalEnvironmentalEnd - environmentalEnd) > 1.0F);
   }

   static void remember(FogData fog) {
      environmentalStart = fog.environmentalStart;
      environmentalEnd = fog.environmentalEnd;
      renderDistanceStart = fog.renderDistanceStart;
      renderDistanceEnd = fog.renderDistanceEnd;
      pending = true;
   }

   public static GpuBufferSlice swap(GpuBufferSlice fog, FogData last) {
      finalEnvironmentalStart = last.environmentalStart;
      finalEnvironmentalEnd = last.environmentalEnd;
      if (!pending) {
         return fog;
      }

      pending = false;
      if (ring == null) {
         ring = new MappableRingBuffer(() -> "Farlands sky fog", 130, FogRenderer.FOG_UBO_SIZE);
      }

      ring.rotate();
      MappedView view = ring.currentBuffer().map(false, true);

      try {
         Std140Builder.intoBuffer(view.data())
            .putVec4(last.color)
            .putFloat(environmentalStart)
            .putFloat(environmentalEnd)
            .putFloat(renderDistanceStart)
            .putFloat(renderDistanceEnd)
            .putFloat(last.skyEnd)
            .putFloat(last.cloudEnd);
      } catch (Throwable var6) {
         if (view != null) {
            try {
               view.close();
            } catch (Throwable var5) {
               var6.addSuppressed(var5);
            }
         }

         throw var6;
      }

      if (view != null) {
         view.close();
      }

      return ring.currentBuffer().slice(0L, FogRenderer.FOG_UBO_SIZE);
   }
}
