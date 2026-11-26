package net.pryzma.farlands.gen;

import net.pryzma.farlands.Farlands;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.BlockColumn;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.densityfunction.DensitySamplerSet;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.MaterialSystem;

final class MaterialAccess {
   private static final MethodHandle NEW_CONTEXT;
   private static final MethodHandle UPDATE_XZ;
   private static final MethodHandle UPDATE_Y;
   static final boolean AVAILABLE;
   private static final MethodHandle ERODED_BADLANDS;
   private static final MethodHandle FROZEN_OCEAN;
   static final boolean EXTENSIONS;
   private static final MethodHandle SURFACE_DEPTH;
   private static final MethodHandle LAST_XZ;
   private static final MethodHandle MIN_SURFACE;
   private static final MethodHandle MIN_SURFACE_STAMP;
   static final boolean PRESET;

   private static Field open(Field field, Class<?> type) throws NoSuchFieldException {
      if (field.getType() == type && !Modifier.isStatic(field.getModifiers())) {
         field.setAccessible(true);
         return field;
      } else {
         throw new NoSuchFieldException(field.getName());
      }
   }

   static void presetMinSurface(MaterialRuleContext context, float surface) {
      try {
         int depth = (int)SURFACE_DEPTH.invokeExact((MaterialRuleContext)context);
         long stamp = (long)LAST_XZ.invokeExact((MaterialRuleContext)context);
         MIN_SURFACE.invokeExact((MaterialRuleContext)context, (int)(Mth.floor(surface) + depth - 8));
         MIN_SURFACE_STAMP.invokeExact((MaterialRuleContext)context, (long)stamp);
      } catch (Throwable e) {
         throw new IllegalStateException(e);
      }
   }

   static void erodedBadlands(MaterialSystem system, BlockColumn column, int x, int z, int height, LevelHeightAccessor heights) {
      try {
         ERODED_BADLANDS.invoke((MaterialSystem)system, (BlockColumn)column, (int)x, (int)z, (int)height, (LevelHeightAccessor)heights);
      } catch (Throwable e) {
         throw new IllegalStateException(e);
      }
   }

   static void frozenOcean(MaterialSystem system, MaterialRuleContext context, Biome biome, BlockColumn column, MutableBlockPos pos, int x, int z, int height) {
      try {
         FROZEN_OCEAN.invoke(
            (MaterialSystem)system, (int)context.getMinSurfaceLevel(), (Biome)biome, (BlockColumn)column, (MutableBlockPos)pos, (int)x, (int)z, (int)height
         );
      } catch (Throwable e) {
         throw new IllegalStateException(e);
      }
   }

   private MaterialAccess() {
   }

   static MaterialRuleContext create(
      MaterialSystem system,
      RandomState randomState,
      DensityVolume volume,
      DensitySamplerSet samplers,
      Function<BlockPos, Holder<Biome>> biomes,
      WorldGenerationContext context,
      Set<Holder<Biome>> possibleBiomes
   ) {
      try {
         return (MaterialRuleContext)NEW_CONTEXT.invoke(
            (MaterialSystem)system,
            (RandomState)randomState,
            (DensityVolume)volume,
            (DensitySamplerSet)samplers,
            (Function)biomes,
            (WorldGenerationContext)context,
            (Set)possibleBiomes
         );
      } catch (Throwable e) {
         throw new IllegalStateException(e);
      }
   }

   static void updateXZ(MaterialRuleContext context, int x, int z, int gradientX, int gradientZ) {
      try {
         UPDATE_XZ.invokeExact((MaterialRuleContext)context, (int)x, (int)z, (int)gradientX, (int)gradientZ);
      } catch (Throwable e) {
         throw new IllegalStateException(e);
      }
   }

   static void updateY(MaterialRuleContext context, int stoneAbove, int stoneBelow, int waterHeight, int y) {
      try {
         UPDATE_Y.invokeExact((MaterialRuleContext)context, (int)stoneAbove, (int)stoneBelow, (int)waterHeight, (int)y);
      } catch (Throwable e) {
         throw new IllegalStateException(e);
      }
   }

   static {
      MethodHandle create = null;
      MethodHandle xz = null;
      MethodHandle y = null;

      try {
         Lookup lookup = MethodHandles.lookup();
         Constructor<MaterialRuleContext> constructor = MaterialRuleContext.class
            .getDeclaredConstructor(
               MaterialSystem.class, RandomState.class, DensityVolume.class, DensitySamplerSet.class, Function.class, WorldGenerationContext.class, Set.class
            );
         constructor.setAccessible(true);
         create = lookup.unreflectConstructor(constructor);
         Method updateXZ = MaterialRuleContext.class.getDeclaredMethod("updateXZ", int.class, int.class, int.class, int.class);
         updateXZ.setAccessible(true);
         xz = lookup.unreflect(updateXZ);
         Method updateY = MaterialRuleContext.class.getDeclaredMethod("updateY", int.class, int.class, int.class, int.class);
         updateY.setAccessible(true);
         y = lookup.unreflect(updateY);
      } catch (ReflectiveOperationException | RuntimeException e) {
         Farlands.LOG.warn("Surface materials unavailable, using stone and fluids only", e);
      }

      NEW_CONTEXT = create;
      UPDATE_XZ = xz;
      UPDATE_Y = y;
      AVAILABLE = create != null && xz != null && y != null;
      MethodHandle eroded = null;
      MethodHandle frozen = null;

      try {
         Lookup lookup = MethodHandles.lookup();
         Method erodedMethod = MaterialSystem.class
            .getDeclaredMethod("erodedBadlandsExtension", BlockColumn.class, int.class, int.class, int.class, LevelHeightAccessor.class);
         erodedMethod.setAccessible(true);
         eroded = lookup.unreflect(erodedMethod);
         Method frozenMethod = MaterialSystem.class
            .getDeclaredMethod("frozenOceanExtension", int.class, Biome.class, BlockColumn.class, MutableBlockPos.class, int.class, int.class, int.class);
         frozenMethod.setAccessible(true);
         frozen = lookup.unreflect(frozenMethod);
      } catch (ReflectiveOperationException | RuntimeException e) {
         Farlands.LOG.warn("Icebergs and badlands pillars unavailable in distant terrain", e);
      }

      ERODED_BADLANDS = eroded;
      FROZEN_OCEAN = frozen;
      EXTENSIONS = eroded != null && frozen != null;
      MethodHandle depth = null;
      MethodHandle stamp = null;
      MethodHandle level = null;
      MethodHandle levelStamp = null;

      try {
         Lookup lookup = MethodHandles.lookup();
         depth = lookup.unreflectGetter(open(MaterialRuleContext.class.getDeclaredField("surfaceDepth"), int.class));
         stamp = lookup.unreflectGetter(open(MaterialRuleContext.class.getDeclaredField("lastUpdateXZ"), long.class));
         level = lookup.unreflectSetter(open(MaterialRuleContext.class.getDeclaredField("minSurfaceLevel"), int.class));
         levelStamp = lookup.unreflectSetter(open(MaterialRuleContext.class.getDeclaredField("lastMinSurfaceLevelUpdate"), long.class));
      } catch (ReflectiveOperationException | RuntimeException e) {
         depth = null;
      }

      SURFACE_DEPTH = depth;
      LAST_XZ = stamp;
      MIN_SURFACE = level;
      MIN_SURFACE_STAMP = levelStamp;
      PRESET = AVAILABLE && depth != null && stamp != null && level != null && levelStamp != null;
   }
}
