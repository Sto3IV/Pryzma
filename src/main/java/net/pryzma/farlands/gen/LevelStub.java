package net.pryzma.farlands.gen;

import net.pryzma.farlands.Farlands;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.Proxy;
import java.util.Optional;
import net.minecraft.SystemReport;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistryAccess.Frozen;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.debugchart.SampleLogger;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jspecify.annotations.Nullable;

final class LevelStub {
   private LevelStub() {
   }

   static @Nullable ServerLevel create(GenWorld world) {
      try {
         Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
         field.setAccessible(true);
         Object unsafe = field.get(null);
         Method allocate = unsafe.getClass().getMethod("allocateInstance", Class.class);
         LevelStub.ServerStub server = (LevelStub.ServerStub)allocate.invoke(unsafe, LevelStub.ServerStub.class);
         server.world = world;
         LevelStub.WorldLevel level = (LevelStub.WorldLevel)allocate.invoke(unsafe, LevelStub.WorldLevel.class);
         LevelStub.ChunkStub chunks = (LevelStub.ChunkStub)allocate.invoke(unsafe, LevelStub.ChunkStub.class);
         chunks.world = world;
         level.world = world;
         level.chunks = chunks;
         level.server = server;
         return level;
      } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
         Farlands.LOG.warn("Distant structures that need the server level are unavailable", e);
         return null;
      }
   }

   static final class ChunkStub extends ServerChunkCache {
      GenWorld world;

      private ChunkStub() {
         super(null, null, null, null, null, null, 0, 0, false, null);
      }

      public ChunkGenerator getGenerator() {
         return this.world.generator;
      }

      public RandomState randomState() {
         return this.world.randomState;
      }

      public ChunkGeneratorStructureState getGeneratorState() {
         return this.world.structureState;
      }
   }

   static final class ServerStub extends MinecraftServer {
      GenWorld world;

      private ServerStub() {
         super(null, null, null, null, Optional.empty(), Proxy.NO_PROXY, null, null, null, false, null);
      }

      public StructureTemplateManager getStructureTemplateManager() {
         return this.world.templates;
      }

      public Frozen registryAccess() {
         return this.world.registries instanceof Frozen frozen ? frozen : this.world.registries.freeze();
      }

      protected boolean initServer() throws IOException {
         return false;
      }

      public LevelBasedPermissionSet operatorUserPermissions() {
         return null;
      }

      public PermissionSet getFunctionCompilationPermissions() {
         return null;
      }

      public boolean shouldRconBroadcast() {
         return false;
      }

      protected SampleLogger getTickTimeLogger() {
         return null;
      }

      public boolean isTickTimeLoggingEnabled() {
         return false;
      }

      public SystemReport fillServerSystemReport(SystemReport systemReport) {
         return systemReport;
      }

      public boolean isDedicatedServer() {
         return false;
      }

      public int getRateLimitPacketsPerSecond() {
         return 0;
      }

      public int getCommandSpamThresholdSeconds() {
         return 0;
      }

      public int getChatSpamThresholdSeconds() {
         return 0;
      }

      public boolean useNativeTransport() {
         return false;
      }

      public boolean isPublished() {
         return false;
      }

      public boolean shouldInformAdmins() {
         return false;
      }

      public boolean isSingleplayerOwner(NameAndId nameAndId) {
         return false;
      }

      public int getMaxPlayers() {
         return 0;
      }
   }

   static final class WorldLevel extends ServerLevel {
      GenWorld world;
      LevelStub.ChunkStub chunks;
      LevelStub.ServerStub server;

      private WorldLevel() {
         super(null, null, null, null, null, null, false, 0L, null, false);
      }

      public long getSeed() {
         return this.world.seed;
      }

      public FeatureFlagSet enabledFeatures() {
         return FeatureFlagSet.of();
      }

      public RegistryAccess registryAccess() {
         return this.world.registries;
      }

      public ServerChunkCache getChunkSource() {
         return this.chunks;
      }

      public MinecraftServer getServer() {
         return this.server;
      }

      public ResourceKey<Level> dimension() {
         return this.world.dimension;
      }

      public DimensionType dimensionType() {
         return this.world.level.dimensionType();
      }

      public int getSeaLevel() {
         return this.world.level.getSeaLevel();
      }

      public StructureManager structureManager() {
         return this.world.structures;
      }
   }
}
