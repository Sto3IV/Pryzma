package net.pryzma.farlands.gen;

import net.pryzma.farlands.lod.LodTile;
import net.pryzma.farlands.lod.Span;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.attribute.EnvironmentAttributeReader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEvent.Context;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.BlackholeTickAccess;
import net.minecraft.world.ticks.LevelTickAccess;
import org.jspecify.annotations.Nullable;

final class LodGenLevel implements WorldGenLevel {
   private static final BlockState AIR = Blocks.AIR.defaultBlockState();
   private static final BlockState VOID_AIR = Blocks.VOID_AIR.defaultBlockState();
   private static final Types[] TYPES = Types.values();
   private static final Types[] LIVE = ChunkStatus.FEATURES.getParent().heightmapsAfter().toArray(new Types[0]);
   final Long2ObjectOpenHashMap<BlockState> placed = new Long2ObjectOpenHashMap();
   final LongArrayList journal = new LongArrayList();
   boolean journaling;
   private final Long2IntOpenHashMap heights = new Long2IntOpenHashMap();
   private final Long2IntOpenHashMap terrain = new Long2IntOpenHashMap();
   private final MutableBlockPos scan = new MutableBlockPos();
   private int[] columnHeights = new int[0];
   private int heightWidth;
   private int heightArea;
   private final GenWorld world;
   private final Level level;
   private LodTile tile;
   private int originX;
   private int originZ;
   private int step;
   private int minX;
   private int minZ;
   private int maxX;
   private int maxZ;
   private BiomeManager biomeManager;
   boolean hideSnow;
   private final PositionalRandomFactory regionRandoms;
   private RandomSource random;
   private final BlockState defaultBlock;
   private final Long2ObjectOpenHashMap<ProtoChunk> stubs = new Long2ObjectOpenHashMap();

   LodGenLevel(GenWorld world, BlockState defaultBlock) {
      this.world = world;
      this.level = world.level;
      this.defaultBlock = defaultBlock;
      this.heights.defaultReturnValue(Integer.MIN_VALUE);
      this.terrain.defaultReturnValue(Integer.MIN_VALUE);
      this.regionRandoms = world.randomState.getOrCreateRandomFactory(Identifier.withDefaultNamespace("worldgen_region_random"));
      this.random = this.regionRandoms.at(0, 0, 0);
   }

   void region(int chunkX, int chunkZ) {
      this.random = this.regionRandoms.at(chunkX << 4, 0, chunkZ << 4);
   }

   void reset(LodTile tile, int cellMinusOneX, int cellMinusOneZ, int step, BiomeManager biomeManager) {
      this.tile = tile;
      this.originX = cellMinusOneX + step;
      this.originZ = cellMinusOneZ + step;
      this.step = step;
      this.biomeManager = biomeManager;
      this.hideSnow = false;
      this.placed.clear();
      this.heights.clear();
      this.stubs.clear();
      int size = 64 * step;
      this.minX = this.originX - 16;
      this.minZ = this.originZ - 16;
      this.maxX = this.originX + size + 16;
      this.maxZ = this.originZ + size + 16;
      this.heightWidth = this.maxX - this.minX;
      this.heightArea = this.heightWidth * (this.maxZ - this.minZ);
      int needed = this.heightArea * TYPES.length;
      if (this.columnHeights.length < needed) {
         this.columnHeights = new int[needed];
      }

      Arrays.fill(this.columnHeights, 0, needed, Integer.MIN_VALUE);
   }

   private int heightSlot(Types type, int x, int z) {
      return !this.inside(x, z) ? -1 : type.ordinal() * this.heightArea + (z - this.minZ) * this.heightWidth + x - this.minX;
   }

   void shrink() {
      this.placed.clear();
      this.placed.trim(4096);
      this.heights.clear();
      this.heights.trim(4096);
   }

   private int cellX(int x) {
      return Math.max(-1, Math.min(64, Math.floorDiv(x - this.originX, this.step)));
   }

   private int cellZ(int z) {
      return Math.max(-1, Math.min(64, Math.floorDiv(z - this.originZ, this.step)));
   }

   BlockState baseState(int x, int y, int z) {
      if (this.isOutsideBuildHeight(y)) {
         return VOID_AIR;
      }

      int cx = this.cellX(x);
      int cz = this.cellZ(z);
      int from = this.tile.first(cx, cz);
      int count = this.tile.count(cx, cz);
      if (count == 0) {
         return y < this.getMinY() + 1 ? this.defaultBlock : AIR;
      }

      for (int i = 0; i < count; i++) {
         long span = this.tile.spans[from + i];
         if (y >= Span.top(span)) {
            return AIR;
         }

         if (y >= Span.bottom(span)) {
            BlockState state = Block.stateById(Span.state(span));
            return this.hideSnow && state.is(Blocks.SNOW) ? AIR : state;
         }
      }

      return Span.bottom(this.tile.spans[from + count - 1]) > this.getMinY() ? AIR : this.defaultBlock;
   }

   int baseHeight(Types type, int x, int z) {
      int cx = this.cellX(x);
      int cz = this.cellZ(z);
      int from = this.tile.first(cx, cz);
      int count = this.tile.count(cx, cz);
      Predicate<BlockState> test = type.isOpaque();

      for (int i = 0; i < count; i++) {
         long span = this.tile.spans[from + i];
         BlockState state = Block.stateById(Span.state(span));
         if ((!this.hideSnow || !state.is(Blocks.SNOW)) && test.test(state)) {
            return Span.top(span);
         }
      }

      return this.getMinY();
   }

   boolean inside(int x, int z) {
      return x >= this.minX && x < this.maxX && z >= this.minZ && z < this.maxZ;
   }

   public BlockState getBlockState(BlockPos pos) {
      BlockState state = (BlockState)this.placed.get(pos.asLong());
      return state != null ? state : this.baseState(pos.getX(), pos.getY(), pos.getZ());
   }

   public FluidState getFluidState(BlockPos pos) {
      return this.getBlockState(pos).getFluidState();
   }

   public int getHeight(Types type, int x, int z) {
      int slot = this.heightSlot(type, x, z);
      if (slot >= 0) {
         int cached = this.columnHeights[slot];
         if (cached != Integer.MIN_VALUE) {
            return cached;
         }

         int base = this.baseHeight(type, x, z);
         this.columnHeights[slot] = base;
         return base;
      } else {
         long key = (long)type.ordinal() << 58 ^ ChunkPos.pack(x, z);
         int known = this.heights.get(key);
         if (known != Integer.MIN_VALUE) {
            return known;
         }

         int base = this.baseHeight(type, x, z);
         this.heights.put(key, base);
         return base;
      }
   }

   public boolean setBlock(BlockPos pos, BlockState state, int flags, int updateLimit) {
      if (this.inside(pos.getX(), pos.getZ()) && pos.getY() >= this.getMinY() && pos.getY() < this.getMaxY()) {
         this.placed.put(pos.asLong(), state);
         if (this.journaling) {
            this.journal.add(pos.asLong());
         }

         for (Types type : LIVE) {
            int current = this.getHeight(type, pos.getX(), pos.getZ());
            int slot = this.heightSlot(type, pos.getX(), pos.getZ());
            if (type.isOpaque().test(state)) {
               if (pos.getY() + 1 > current) {
                  this.columnHeights[slot] = pos.getY() + 1;
               }
            } else if (pos.getY() + 1 == current) {
               this.columnHeights[slot] = this.below(type, pos.getX(), pos.getY(), pos.getZ());
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private int below(Types type, int x, int y, int z) {
      Predicate<BlockState> test = type.isOpaque();
      MutableBlockPos pos = this.scan;

      for (int below = y - 1; below >= this.getMinY(); below--) {
         if (test.test(this.getBlockState(pos.set(x, below, z)))) {
            return below + 1;
         }
      }

      return this.getMinY();
   }

   public boolean ensureCanWrite(BlockPos pos) {
      return this.inside(pos.getX(), pos.getZ());
   }

   public boolean removeBlock(BlockPos pos, boolean movedByPiston) {
      return this.setBlock(pos, AIR, 3, 512);
   }

   public boolean destroyBlock(BlockPos pos, boolean dropResources, @Nullable Entity breaker, int updateLimit) {
      return !this.getBlockState(pos).isAir() && this.setBlock(pos, AIR, 3, updateLimit);
   }

   public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> predicate) {
      return predicate.test(this.getBlockState(pos));
   }

   public boolean isFluidAtPosition(BlockPos pos, Predicate<FluidState> predicate) {
      return predicate.test(this.getFluidState(pos));
   }

   public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
      return null;
   }

   public long getSeed() {
      return this.world.seed;
   }

   public ServerLevel getLevel() {
      return this.world.serverLevel;
   }

   public long nextSubTickCount() {
      return 0L;
   }

   public LevelTickAccess<Block> getBlockTicks() {
      return BlackholeTickAccess.emptyLevelList();
   }

   public LevelTickAccess<Fluid> getFluidTicks() {
      return BlackholeTickAccess.emptyLevelList();
   }

   public LevelData getLevelData() {
      return this.level.getLevelData();
   }

   public DifficultyInstance getCurrentDifficultyAt(BlockPos pos) {
      return new DifficultyInstance(this.level.getDifficulty(), 0L, 0L, 0.0F);
   }

   public @Nullable MinecraftServer getServer() {
      return this.level.getServer();
   }

   public ChunkSource getChunkSource() {
      return this.level.getChunkSource();
   }

   public RandomSource getRandom() {
      return this.random;
   }

   public void playSound(@Nullable Entity except, BlockPos pos, SoundEvent sound, SoundSource source, float volume, float pitch) {
   }

   public void addParticle(ParticleOptions particle, double x, double y, double z, double xd, double yd, double zd) {
   }

   public void levelEvent(@Nullable Entity source, int type, BlockPos pos, int data) {
   }

   public void gameEvent(Holder<GameEvent> gameEvent, Vec3 position, Context context) {
   }

   public RegistryAccess registryAccess() {
      return this.world.registries;
   }

   public FeatureFlagSet enabledFeatures() {
      return this.level.enabledFeatures();
   }

   public <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> type, AABB bb, Predicate<? super T> selector) {
      return Collections.emptyList();
   }

   public List<Entity> getEntities(@Nullable Entity except, AABB bb, @Nullable Predicate<? super Entity> selector) {
      return Collections.emptyList();
   }

   public List<? extends Player> players() {
      return Collections.emptyList();
   }

   public @Nullable ChunkAccess getChunk(int chunkX, int chunkZ, ChunkStatus status, boolean loadOrGenerate) {
      if (!loadOrGenerate) {
         return null;
      }

      long key = ChunkPos.pack(chunkX, chunkZ);
      ProtoChunk stub = (ProtoChunk)this.stubs.get(key);
      if (stub == null) {
         if (this.stubs.size() >= 64) {
            this.stubs.clear();
         }

         stub = new LodGenLevel.StubChunk(this, new ChunkPos(chunkX, chunkZ));
         this.stubs.put(key, stub);
      }

      return stub;
   }

   int terrainHeight(Types type, int x, int z) {
      long key = (long)type.ordinal() << 58 ^ ChunkPos.pack(x, z);
      int known = this.terrain.get(key);
      if (known != Integer.MIN_VALUE) {
         return known;
      }

      int height = this.world.generator.getBaseHeight(x, z, type, this.level, this.world.randomState);
      if (this.terrain.size() >= 4096) {
         this.terrain.clear();
      }

      this.terrain.put(key, height);
      return height;
   }

   void resetStubs() {
      this.stubs.clear();
   }

   public boolean hasChunk(int chunkX, int chunkZ) {
      return true;
   }

   public int getSkyDarken() {
      return 0;
   }

   public BiomeManager getBiomeManager() {
      return this.biomeManager;
   }

   public Holder<Biome> getUncachedNoiseBiome(int quartX, int quartY, int quartZ) {
      return this.biomeManager.getNoiseBiomeAtQuart(quartX, quartY, quartZ);
   }

   public boolean isClientSide() {
      return false;
   }

   public int getSeaLevel() {
      return this.level.getSeaLevel();
   }

   public DimensionType dimensionType() {
      return this.level.dimensionType();
   }

   public LevelLightEngine getLightEngine() {
      return LevelLightEngine.EMPTY;
   }

   public WorldBorder getWorldBorder() {
      return this.level.getWorldBorder();
   }

   public int getMinY() {
      return this.level.getMinY();
   }

   public int getHeight() {
      return this.level.getHeight();
   }

   public EnvironmentAttributeReader environmentAttributes() {
      return this.level.environmentAttributes();
   }

   private static final class StubChunk extends ProtoChunk {
      private final LodGenLevel owner;

      StubChunk(LodGenLevel owner, ChunkPos pos) {
         super(pos, UpgradeData.EMPTY, owner.level, owner.world.palettes, null);
         this.owner = owner;
      }

      public int getHeight(Types type, int x, int z) {
         return this.owner.terrainHeight(type, this.getPos().getMinBlockX() + (x & 15), this.getPos().getMinBlockZ() + (z & 15)) - 1;
      }
   }
}
