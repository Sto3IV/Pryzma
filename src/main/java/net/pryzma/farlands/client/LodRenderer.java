package net.pryzma.farlands.client;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderSystem.AutoStorageIndexBuffer;
import com.mojang.blaze3d.vertex.TlsfAllocator;
import com.mojang.blaze3d.vertex.TlsfAllocator.Allocation;
import com.mojang.blaze3d.vertex.TlsfAllocator.Heap;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice.MappedView;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.DeviceFeatures;
import com.mojang.renderpearl.api.device.DeviceInfo;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import net.pryzma.farlands.FarlandsSettings;
import net.pryzma.farlands.lod.LodTile;
import net.pryzma.farlands.lod.LodWorld;
import net.pryzma.farlands.lod.LongKeySet;
import net.pryzma.farlands.work.WorkerPool;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DynamicGpuData;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;

public final class LodRenderer {
   private static final long ARENA_BYTES = 33554432L;
   private static final int MAX_ARENAS = 64;
   private static final float MAX_DETAIL_SCALE = 4.0F;
   private static final long RETRY_FRAMES = 240L;
   private static final int MAX_SLOTS = 4096;
   private static final long UPLOAD_BUDGET = 4194304L;
   private static final long UPLOAD_NANOS = 2000000L;
   private static final int MASK = 128;
   private static final int INFO_BYTES = 128;
   private static final long WATER_ARENA_BYTES = 16777216L;
   private static final int MAX_WATER_ARENAS = 32;
   private static final int WATER_VERTEX_BYTES = 4;
   private static final int PIPELINE_VOTES = 3;
   private static final int COMMAND_BYTES = 20;
   private final WorkerPool pool;
   private final BlockLooks looks;
   private final SpriteTable sprites;
   private final ThreadLocal<LodMesher> meshers;
   private final Long2ObjectOpenHashMap<LodRenderer.Mesh> meshes = new Long2ObjectOpenHashMap();
   private final LodRenderer.Mesh[] bySlot = new LodRenderer.Mesh[4096];
   private final ArrayDeque<Integer> freeSlots = new ArrayDeque<>();
   private final LongKeySet meshing = new LongKeySet();
   private final ConcurrentLinkedQueue<LodRenderer.Built> built = new ConcurrentLinkedQueue<>();
   private final ArrayDeque<LodRenderer.PendingFree> pendingFrees = new ArrayDeque<>();
   private final ArrayDeque<LodRenderer.PendingWaterFree> pendingWaterFrees = new ArrayDeque<>();
   private final AtomicBoolean compactMeshes = new AtomicBoolean();
   private @Nullable GpuBuffer compactInstances;
   private @Nullable GpuBuffer compactCommands;
   private int compactInstanceCapacity;
   private int compactCommandCapacity;
   private @Nullable ByteBuffer compactInstanceData;
   private @Nullable ByteBuffer compactCommandData;
   private final List<LodRenderer.Arena> compactArenas = new ArrayList<>();
   private final IntArrayList compactRanges = new IntArrayList();
   private int compactSplit;
   private final List<LodRenderer.WaterArena> waterArenas = new ArrayList<>();
   private @Nullable GpuBuffer waterInstances;
   private @Nullable GpuBuffer waterCommands;
   private int waterInstanceCapacity;
   private int waterCommandCapacity;
   private @Nullable ByteBuffer waterInstanceData;
   private @Nullable ByteBuffer waterCommandData;
   private final List<Object> waterSegments = new ArrayList<>();
   private final IntArrayList waterSegmentCommands = new IntArrayList();
   private boolean waterBatchedFrame;
   private final List<LodRenderer.Arena> arenas = new ArrayList<>();
   private final List<LodRenderer.Mesh> draws = new ArrayList<>();
   private final List<LodRenderer.Mesh> clipped = new ArrayList<>();
   private final List<LodRenderer.Mesh> all = new ArrayList<>();
   private final int[][] visitStarts = new int[8][5];
   private final List<LodRenderer.Mesh> waterDraws = new ArrayList<>();
   private boolean waterLayer;
   private boolean vanillaWater;
   private final AtomicBoolean vanillaWaterFlag = new AtomicBoolean();
   private @Nullable CompiledRenderPipeline plainTranslucent;
   private boolean pipelineProbed;
   private boolean pipelineModified;
   private boolean pipelineObserved;
   private boolean pipelineSeen;
   private int pipelineVotes;
   private int waterRadius;
   private @Nullable GpuBufferSlice waterTerrain;
   private @Nullable GpuBuffer waterSections;
   private int waterSectionCapacity;
   private @Nullable ByteBuffer waterSectionData;
   private final List<LodRenderer.Mesh> selected = new ArrayList<>();
   private float detailScale = 1.0F;
   private boolean memoryFull;
   private long pressureMillis;
   private final LongOpenHashSet requested = new LongOpenHashSet();
   private LongOpenHashSet leaves = new LongOpenHashSet();
   private LongOpenHashSet previousLeaves = new LongOpenHashSet();
   private final int[] mask = new int[32768];
   private @Nullable VanillaCoverage coverage;
   private int maskVersion = -1;
   private int meshGeneration;
   private boolean reclaim;
   private @Nullable MappableRingBuffer tileRing;
   private @Nullable MappableRingBuffer infoRing;
   private @Nullable GpuBuffer spriteBuffer;
   private @Nullable GpuBuffer waterSpriteBuffer;
   private @Nullable GpuBuffer maskBuffer;
   private @Nullable GpuSampler atlasSampler;
   private int uploadedSpriteVersion = -1;
   private int uploadedSprites;
   private boolean maskDirty = true;
   private long frame;
   private int rootLevel;
   private long rebuilds;
   private long selectionChanges;
   private long fallbacks;
   private static final int SOLID = 0;
   private static final int UNDER = 1;
   private static final int SURFACE = 2;
   private final Long2LongOpenHashMap bounds = new Long2LongOpenHashMap();
   private int maxQuads;
   private @Nullable LodWorld world;
   private @Nullable Registry<Biome> biomes;
   private volatile int floor = 2047;
   private final int[] rangeBase = new int[224];
   private final int[] rangeQuads = new int[224];
   private PointerBuffer drawOffsets = MemoryUtil.memAllocPointer(1024);
   private IntBuffer drawCounts = MemoryUtil.memAllocInt(1024);
   private IntBuffer drawBases = MemoryUtil.memAllocInt(1024);
   private int cameraBlockX;
   private int cameraBlockY;
   private int cameraBlockZ;
   private long drawnQuads;
   private @Nullable ShaderPath shaderPath;
   private boolean shaderMode;
   private boolean lateFluid;
   private Vec3 camera = Vec3.ZERO;
   private final LodOcclusion occlusion = new LodOcclusion();
   private boolean occlusionEnabled = true;
   private boolean canopyEnabled = true;
   private boolean selectionComplete;
   private long hiddenQuads;
   private int uploads;
   private long lastHash;
   private int stableFrames;
   private Vec3 lastCamera = Vec3.ZERO;
   private static final int COVERED = 2;
   private static final int PARTIAL = 1;
   private static final int EMPTY = 0;

   public LodRenderer(WorkerPool pool, BlockLooks looks, SpriteTable sprites) {
      this.pool = pool;
      this.looks = looks;
      this.sprites = sprites;
      this.meshers = ThreadLocal.withInitial(() -> new LodMesher(looks, sprites, this.compactMeshes, this.vanillaWaterFlag));

      for (int i = 4095; i >= 0; i--) {
         this.freeSlots.push(i);
      }
   }

   public void setWorld(@Nullable LodWorld world, @Nullable Registry<Biome> biomes) {
      this.setWorld(world, biomes, 2047);
   }

   public void setWorld(@Nullable LodWorld world, @Nullable Registry<Biome> biomes, int floor) {
      this.floor = floor;
      this.bounds.clear();
      if (this.world != world) {
         this.clearMeshes();
         this.world = world;
         this.biomes = biomes;
         this.maskDirty = true;
      }
   }

   public void setShaderPath(@Nullable ShaderPath shaderPath) {
      this.shaderPath = shaderPath;
   }

   public boolean shaderMode() {
      return this.shaderMode;
   }

   public void markMaskDirty() {
      this.maskDirty = true;
   }

   public void clearMeshes() {
      ObjectIterator b = this.meshes.values().iterator();

      while (b.hasNext()) {
         LodRenderer.Mesh mesh = (LodRenderer.Mesh)b.next();
         if (mesh.allocation != null && mesh.arena != null) {
            mesh.arena.allocator.free(mesh.allocation);
         }

         this.releaseShaderBuffers(mesh);
         this.releaseWater(mesh);
         this.bySlot[mesh.slot] = null;
         this.freeSlots.push(mesh.slot);
      }

      this.meshes.clear();
      this.draws.clear();
      this.occlusion.clear();
      this.meshGeneration++;

      LodRenderer.Built builtMesh;
      while ((builtMesh = this.built.poll()) != null) {
         builtMesh.mesh.free();
      }

      this.meshing.clear();
   }

   private void releaseWater(LodRenderer.Mesh mesh) {
      if (mesh.waterAllocation != null && mesh.waterArena != null) {
         this.pendingWaterFrees.add(new LodRenderer.PendingWaterFree(this.frame, mesh.waterArena, mesh.waterAllocation));
      } else if (mesh.water != null) {
         mesh.water.close();
      }

      mesh.water = null;
      mesh.waterArena = null;
      mesh.waterAllocation = null;
      mesh.waterBase = 0;
      mesh.waterQuads = 0;
      mesh.waterPatches = new int[0];
   }

   private void releaseShaderBuffers(LodRenderer.Mesh mesh) {
      if ((mesh.vbo != 0 || mesh.vao != 0) && this.shaderPath != null) {
         this.shaderPath.delete(mesh.vbo, mesh.vao);
      }

      mesh.vbo = 0;
      mesh.vao = 0;
   }

   public long frame() {
      return this.frame;
   }

   public long rebuilds() {
      return this.rebuilds;
   }

   public long selectionChanges() {
      return this.selectionChanges;
   }

   public long fallbacks() {
      return this.fallbacks;
   }

   public long frames() {
      return this.frame;
   }

   public int meshCount() {
      return this.meshes.size();
   }

   public int drawCount() {
      return this.draws.size() + this.clipped.size();
   }

   private Iterable<LodRenderer.Mesh> allDraws() {
      this.all.clear();
      this.all.addAll(this.draws);
      this.all.addAll(this.clipped);
      return this.all;
   }

   public long drawnQuads() {
      return this.drawnQuads;
   }

   public long gpuBytes() {
      return this.arenas.size() * 33554432L;
   }

   public void prepare(Vec3 camera, Frustum frustum, Matrix4fc view, int vanillaChunks, VanillaCoverage coverage, int dimensionMinY) {
      this.coverage = coverage;
      this.frame++;
      LodWorld world = this.world;
      this.draws.clear();
      this.clipped.clear();
      this.waterDraws.clear();
      if (world != null && this.biomes != null && FarlandsSettings.enabled) {
         world.tick(this.frame);
         boolean shader = this.shaderPath != null && this.shaderPath.active();
         if (shader != this.shaderMode) {
            this.clearMeshes();
            this.shaderMode = shader;
         }

         boolean layer = !shader && !Minecraft.getInstance().gameRenderer.useImprovedTransparency();
         if (layer != this.waterLayer) {
            this.clearMeshes();
            this.waterLayer = layer;
         }

         boolean vanilla = layer && this.pipelineModified;
         if (vanilla != this.vanillaWater) {
            this.vanillaWaterFlag.set(vanilla);
            this.clearMeshes();
            this.vanillaWater = vanilla;
         }

         this.camera = camera;
         GpuDevice device = RenderSystem.getDevice();
         this.compactMeshes.set(!shader && layer && batchSupported(device));
         this.ensureBuffers(device);
         CommandEncoder encoder = device.createCommandEncoder();
         this.releaseFrees(false);
         this.drainChanged(world);
         this.upload(encoder, device);
         this.cameraBlockX = (int)Math.floor(camera.x);
         this.cameraBlockY = (int)Math.floor(camera.y);
         this.cameraBlockZ = (int)Math.floor(camera.z);
         this.select(world, camera, vanillaChunks);
         this.cull(frustum, dimensionMinY, vanillaChunks);
         if (!this.shaderMode) {
            this.writeTiles(camera);
            this.writeSprites(encoder);
            this.writeMask(encoder, coverage, vanillaChunks, camera);
            this.writeInfo(view, vanillaChunks, camera);
            this.occlude(camera, vanillaChunks);
            this.writeWater(encoder, device, view, vanillaChunks);
            this.writeCompact(encoder, device, camera);
         }

         if (this.maxQuads > 0 && !this.shaderMode) {
            RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).requestIndexCount(this.maxQuads * 6);
         }

         if ((this.frame & 63L) == 0L || this.reclaim) {
            this.evict();
            this.reclaim = false;
            world.evict(this.frame);
         }
      }
   }

   private void ensureBuffers(GpuDevice device) {
      if (this.tileRing == null) {
         this.tileRing = new MappableRingBuffer(() -> "Farlands tiles", 258, 65536);
      }

      if (this.infoRing == null) {
         this.infoRing = new MappableRingBuffer(() -> "Farlands info", 130, 128);
      }

      if (this.spriteBuffer == null) {
         this.spriteBuffer = device.createBuffer(() -> "Farlands sprites", 264, 524288L);
      }

      if (this.waterSpriteBuffer == null) {
         this.waterSpriteBuffer = device.createBuffer(() -> "Farlands water sprites", 264, 262160L);
      }

      if (this.maskBuffer == null) {
         this.maskBuffer = device.createBuffer(() -> "Farlands mask", 264, 131072L);
      }

      if (this.atlasSampler == null) {
         this.atlasSampler = device.createSampler(
            AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.NEAREST, 1, OptionalDouble.empty()
         );
      }
   }

   private void drainChanged(LodWorld world) {
      int budget = 4096;

      Long key;
      while (budget-- > 0 && (key = world.pollChanged()) != null) {
         LodRenderer.Mesh mesh = (LodRenderer.Mesh)this.meshes.get(key);
         if (mesh != null) {
            mesh.dirty = true;
         }
      }
   }

   private void upload(CommandEncoder encoder, GpuDevice device) {
      long bytes = 0L;
      long start = System.nanoTime();

      LodRenderer.Built b;
      while (bytes < 4194304L && System.nanoTime() - start < 2000000L && (b = this.built.poll()) != null) {
         this.meshing.remove(b.key);
         LodRenderer.Mesh mesh = (LodRenderer.Mesh)this.meshes.get(b.key);
         if (mesh != null && mesh.slot == b.slot && b.generation == this.meshGeneration) {
            if (b.mesh.format() == LodMesher.Format.SHADER) {
               if (this.shaderMode && this.shaderPath != null) {
                  int[] ids = b.mesh.quads() == 0 ? new int[]{0, 0} : this.shaderPath.upload(b.mesh.data(), b.mesh.quads());
                  bytes += b.mesh.quads() * 64L;
                  b.mesh.free();
                  this.releaseShaderBuffers(mesh);
                  this.releaseWater(mesh);
                  if (mesh.allocation != null && mesh.arena != null) {
                     this.pendingFrees.add(new LodRenderer.PendingFree(this.frame, mesh.arena, mesh.allocation));
                  }

                  mesh.allocation = null;
                  mesh.arena = null;
                  mesh.vbo = ids[0];
                  mesh.vao = ids[1];
                  mesh.quads = b.mesh.quads();
                  mesh.fluidQuads = b.mesh.fluidQuads();
                  mesh.surfaceQuads = b.mesh.surfaceQuads();
                  mesh.format = LodMesher.Format.SHADER;
                  mesh.minY = b.minY;
                  mesh.maxY = b.maxY;
                  this.remember(b.key, b.surfaceY, b.maxY);
                  if (mesh.ready) {
                     this.rebuilds++;
                  }

                  mesh.ready = true;
               } else {
                  b.mesh.free();
               }
            } else if (this.shaderMode) {
               b.mesh.free();
            } else {
               long size = b.mesh.quads() == 0 ? 0L : b.mesh.bytes();
               LodRenderer.Arena arena = null;
               Allocation allocation = null;
               if (size > 0L) {
                  for (LodRenderer.Arena a : this.arenas) {
                     allocation = a.allocator.allocate(size, 16);
                     if (allocation != null) {
                        arena = a;
                        break;
                     }
                  }

                  if (allocation == null && this.arenas.size() < 64 && size <= 33554432L) {
                     LodRenderer.Arena a = new LodRenderer.Arena(device, this.arenas.size());
                     this.arenas.add(a);
                     allocation = a.allocator.allocate(size, 16);
                     arena = allocation == null ? null : a;
                  }

                  if (allocation == null) {
                     b.mesh.free();
                     mesh.retryFrame = this.frame + 240L;
                     this.memoryFull = true;
                     this.reclaim = true;
                     continue;
                  }

                  encoder.writeToBuffer(arena.buffer.slice(allocation.getOffsetFromHeap(), size), b.mesh.data());
                  bytes += size;
               }

               this.releaseWater(mesh);
               if (b.mesh.waterQuads() > 0 && this.vanillaWater) {
                  long waterBytes = b.mesh.waterQuads() * 112L;
                  mesh.water = device.createBuffer(() -> "Farlands water", 40, waterBytes);
                  encoder.writeToBuffer(mesh.water.slice(), b.mesh.water());
                  mesh.waterQuads = b.mesh.waterQuads();
                  mesh.waterPatches = b.mesh.waterPatches();
                  bytes += waterBytes;
               } else if (b.mesh.waterQuads() > 0) {
                  long waterBytes = b.mesh.waterQuads() * 16L;
                  if (!this.placeWater(device, encoder, mesh, b.mesh.water(), waterBytes)) {
                     mesh.water = device.createBuffer(() -> "Farlands water", 264, waterBytes);
                     encoder.writeToBuffer(mesh.water.slice(), b.mesh.water());
                  }

                  mesh.waterQuads = b.mesh.waterQuads();
                  mesh.waterPatches = b.mesh.waterPatches();
                  bytes += waterBytes;
               }

               b.mesh.free();
               if (mesh.allocation != null && mesh.arena != null) {
                  this.pendingFrees.add(new LodRenderer.PendingFree(this.frame, mesh.arena, mesh.allocation));
               }

               mesh.arena = arena;
               mesh.allocation = allocation;
               this.releaseShaderBuffers(mesh);
               mesh.quads = b.mesh.quads();
               mesh.fluidQuads = b.mesh.fluidQuads();
               mesh.surfaceQuads = b.mesh.surfaceQuads();
               mesh.buckets = b.mesh.solidBuckets();
               mesh.groups = b.mesh.solidGroups();
               mesh.subBounds = b.mesh.subBounds();
               mesh.occluders = b.mesh.occluders();
               mesh.version = ++this.uploads;
               mesh.format = LodMesher.Format.FARLANDS;
               mesh.materials = b.mesh.materials();
               mesh.yBase = b.mesh.yBase();
               mesh.bytes = size;
               mesh.minY = b.minY;
               mesh.maxY = b.maxY;
               this.remember(b.key, b.surfaceY, b.maxY);
               if (mesh.ready) {
                  this.rebuilds++;
               }

               mesh.ready = true;
            }
         } else {
            b.mesh.free();
         }
      }
   }

   private static boolean batchSupported(GpuDevice device) {
      DeviceInfo info = device.getDeviceInfo();
      return info.features().drawIndirect()
         && info.features().multiDrawIndirect()
         && info.features().nonZeroFirstInstance()
         && info.limits().maxDrawIndirectDrawCount() > 0
         && !info.hintsAndWorkarounds().multiDrawIndirectHasKnownIssues();
   }

   private boolean placeWater(GpuDevice device, CommandEncoder encoder, LodRenderer.Mesh mesh, ByteBuffer data, long bytes) {
      if (batchSupported(device) && bytes <= 16777216L) {
         LodRenderer.WaterArena arena = null;
         Allocation allocation = null;

         for (LodRenderer.WaterArena a : this.waterArenas) {
            allocation = a.allocator.allocate(bytes, 16);
            if (allocation != null) {
               arena = a;
               break;
            }
         }

         if (allocation == null && this.waterArenas.size() < 32) {
            LodRenderer.WaterArena a = new LodRenderer.WaterArena(device, this.waterArenas.size());
            this.waterArenas.add(a);
            allocation = a.allocator.allocate(bytes, 16);
            arena = allocation == null ? null : a;
         }

         if (allocation == null) {
            return false;
         }

         encoder.writeToBuffer(arena.buffer.slice(allocation.getOffsetFromHeap(), bytes), data);
         mesh.water = arena.buffer;
         mesh.waterArena = arena;
         mesh.waterAllocation = allocation;
         mesh.waterBase = (int)(allocation.getOffsetFromHeap() / 4L);
         return true;
      } else {
         return false;
      }
   }

   private void releaseFrees(boolean all) {
      while (!this.pendingWaterFrees.isEmpty() && (all || this.frame - this.pendingWaterFrees.peekFirst().frame > 4L)) {
         LodRenderer.PendingWaterFree free = this.pendingWaterFrees.pollFirst();
         free.arena.allocator.free(free.allocation);
      }

      while (!this.pendingFrees.isEmpty() && (all || this.frame - this.pendingFrees.peekFirst().frame > 4L)) {
         LodRenderer.PendingFree free = this.pendingFrees.pollFirst();
         free.arena.allocator.free(free.allocation);
      }
   }

   private void select(LodWorld world, Vec3 camera, int vanillaChunks) {
      this.selected.clear();
      this.requested.clear();
      LongOpenHashSet swap = this.previousLeaves;
      this.previousLeaves = this.leaves;
      this.leaves = swap;
      this.leaves.clear();
      Minecraft mc = Minecraft.getInstance();
      this.adaptDetail();
      int cells = Math.max(
         32, Math.round(FarlandsSettings.quality.cellsPerLevel(mc.getWindow().getHeight(), ((Integer)mc.options.fov().get()).intValue()) / this.detailScale)
      );
      double total = (vanillaChunks + FarlandsSettings.lodChunks) * 16.0;
      int rootLevel = Math.min(7, levelFor(total, cells) + 1);
      this.rootLevel = rootLevel;
      int rootSize = 64 << rootLevel;
      int minTx = (int)Math.floor((camera.x - total) / rootSize);
      int maxTx = (int)Math.floor((camera.x + total) / rootSize);
      int minTz = (int)Math.floor((camera.z - total) / rootSize);
      int maxTz = (int)Math.floor((camera.z + total) / rootSize);
      boolean complete = true;

      for (int tz = minTz; tz <= maxTz; tz++) {
         for (int tx = minTx; tx <= maxTx; tx++) {
            complete &= this.visit(world, rootLevel, tx, tz, camera, total, cells, vanillaChunks) == 2;
         }
      }

      this.selectionComplete = complete;
      LongIterator it = this.leaves.iterator();

      while (it.hasNext()) {
         if (!this.previousLeaves.contains(it.nextLong())) {
            this.selectionChanges++;
         }
      }
   }

   private void adaptDetail() {
      long now = Util.getMillis();
      if (this.memoryFull) {
         this.memoryFull = false;
         if (now - this.pressureMillis >= 1000L) {
            this.detailScale = Math.min(4.0F, this.detailScale * 1.15F);
            this.pressureMillis = now;
         }
      } else if (!(this.detailScale <= 1.0F) && now - this.pressureMillis >= 15000L) {
         long used = 0L;
         ObjectIterator var5 = this.meshes.values().iterator();

         while (var5.hasNext()) {
            LodRenderer.Mesh mesh = (LodRenderer.Mesh)var5.next();
            if (mesh.allocation != null) {
               used += mesh.bytes;
            }
         }

         if (used < 1181116006L) {
            this.detailScale = Math.max(1.0F, this.detailScale / 1.05F);
            this.pressureMillis = now - 14000L;
         }
      }
   }

   private void remember(long key, int minY, int maxY) {
      if (this.bounds.size() > 300000) {
         this.bounds.clear();
      }

      this.bounds.put(key, (long)minY << 32 | maxY & 4294967295L);
   }

   private static int levelFor(double distance, int cells) {
      if (distance < cells * 2.0) {
         return 0;
      }

      int level = 31 - Integer.numberOfLeadingZeros((int)Math.min(2.147483647E9, distance / cells));
      return Math.max(0, Math.min(7, level));
   }

   private int visit(LodWorld world, int level, int tx, int tz, Vec3 camera, double total, int cells, int vanillaChunks) {
      int size = 64 << level;
      double x0 = (double)tx * size;
      double z0 = (double)tz * size;
      double dx = Math.max(0.0, Math.max(x0 - camera.x, camera.x - (x0 + size)));
      double dz = Math.max(0.0, Math.max(z0 - camera.z, camera.z - (z0 + size)));
      double near = Math.sqrt(dx * dx + dz * dz);
      if (near > total) {
         return 2;
      }

      if (this.shaderMode) {
         double fx = Math.max(Math.abs(x0 - camera.x), Math.abs(x0 + size - camera.x));
         double fz = Math.max(Math.abs(z0 - camera.z), Math.abs(z0 + size - camera.z));
         if (Math.sqrt(fx * fx + fz * fz) < vanillaChunks * 16.0 * 0.35) {
            return 2;
         }
      } else if (this.coveredByVanilla(level, tx, tz, size, camera, vanillaChunks)) {
         return 2;
      }

      long key = LodTile.key(level, tx, tz);
      double priority = near / ((double)cells * (1 << level));
      if (level != 0 && levelFor(near, cells) < level && (!this.previousLeaves.contains(key) || levelFor(near * 1.2, cells) < level)) {
         int mark = this.selected.size();
         int[] starts = this.visitStarts[level];
         int missingMask = 0;

         for (int i = 0; i < 4; i++) {
            starts[i] = this.selected.size();
            if (this.visit(world, level - 1, tx * 2 + (i & 1), tz * 2 + (i >> 1), camera, total, cells, vanillaChunks) != 2) {
               missingMask |= 1 << i;
            }
         }

         starts[4] = this.selected.size();
         if (missingMask == 0) {
            return 2;
         }

         boolean standIn = level == this.rootLevel;
         LodRenderer.Mesh own = standIn ? this.meshFor(world, key, priority * 0.5, false) : this.keepMesh(key);
         if (standIn && (own == null || !own.ready) && (own == null || this.frame >= own.retryFrame)) {
            world.request(key, priority * 0.5, FarlandsSettings.generate, false);
         }

         if (own != null && own.ready) {
            if (!this.shaderMode) {
               for (int i = 3; i >= 0; i--) {
                  if ((missingMask >> i & 1) != 0) {
                     this.selected.subList(starts[i], starts[i + 1]).clear();
                  }
               }

               own.quadMask = missingMask;
            } else {
               while (this.selected.size() > mark) {
                  this.selected.removeLast();
               }

               own.quadMask = 15;
            }

            this.selected.add(own);
            this.fallbacks++;
            return 2;
         } else {
            return this.selected.size() > mark ? 1 : 0;
         }
      } else if (this.useTile(world, key, priority)) {
         return 2;
      } else {
         return level > 0 ? this.standIn(level - 1, tx, tz, 2) : 0;
      }
   }

   private int standIn(int level, int parentX, int parentZ, int depth) {
      int covered = 0;
      int any = 0;

      for (int i = 0; i < 4; i++) {
         int tx = parentX * 2 + (i & 1);
         int tz = parentZ * 2 + (i >> 1);
         LodRenderer.Mesh mesh = (LodRenderer.Mesh)this.meshes.get(LodTile.key(level, tx, tz));
         if (mesh != null && mesh.ready) {
            mesh.lastFrame = this.frame;
            mesh.quadMask = 15;
            this.selected.add(mesh);
            covered++;
            any++;
         } else if (level > 0 && depth > 1) {
            int below = this.standIn(level - 1, tx, tz, depth - 1);
            if (below == 2) {
               covered++;
            }

            if (below != 0) {
               any++;
            }
         }
      }

      return covered == 4 ? 2 : (any > 0 ? 1 : 0);
   }

   private boolean coveredByVanilla(int level, int tx, int tz, int size, Vec3 camera, int vanillaChunks) {
      VanillaCoverage cover = this.coverage;
      if (size <= 256 && cover != null) {
         int cx0 = Math.floorDiv(tx * size, 16);
         int cz0 = Math.floorDiv(tz * size, 16);
         int n = size / 16;
         int camX = this.cameraBlockX >> 4;
         int camZ = this.cameraBlockZ >> 4;
         if (inside(cx0 - camX, cz0 - camZ, vanillaChunks)
            && inside(cx0 + n - 1 - camX, cz0 - camZ, vanillaChunks)
            && inside(cx0 - camX, cz0 + n - 1 - camZ, vanillaChunks)
            && inside(cx0 + n - 1 - camX, cz0 + n - 1 - camZ, vanillaChunks)) {
            long packed = this.bounds.getOrDefault(LodTile.key(level, tx, tz), Long.MIN_VALUE);
            if (packed == Long.MIN_VALUE) {
               return false;
            }

            int reach = Math.max(1, vanillaChunks - 1);
            int cameraSection = this.cameraBlockY >> 4;
            int low = (int)(packed >> 32) >> 4;
            int high = (int)packed >> 4;
            if (Math.abs(low - cameraSection) <= reach && Math.abs(high - cameraSection) <= reach) {
               int first = low - cover.minSection();
               int last = high - cover.minSection();
               if (first >= 0 && last <= 63 && last >= first) {
                  long need = (last == 63 ? -1L : (1L << last + 1) - 1L) & -(1L << first);

                  for (int z = 0; z < n; z++) {
                     for (int x = 0; x < n; x++) {
                        int cx = cx0 + x;
                        int cz = cz0 + z;
                        if (!inside(cx - camX, cz - camZ, vanillaChunks)) {
                           return false;
                        }

                        if ((cover.bits(ChunkPos.pack(cx, cz)) & need) != need) {
                           return false;
                        }
                     }
                  }

                  return true;
               } else {
                  return false;
               }
            } else {
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean inside(int dx, int dz, int vanillaChunks) {
      int r = Math.max(1, vanillaChunks - 1);
      return dx * dx + dz * dz <= r * r;
   }

   private LodRenderer.@Nullable Mesh keepMesh(long key) {
      LodRenderer.Mesh mesh = (LodRenderer.Mesh)this.meshes.get(key);
      if (mesh != null) {
         mesh.lastFrame = this.frame;
      }

      return mesh;
   }

   private boolean useTile(LodWorld world, long key, double priority) {
      LodRenderer.Mesh mesh = this.meshFor(world, key, priority, true);
      if ((mesh == null || !mesh.ready) && (mesh == null || this.frame >= mesh.retryFrame)) {
         world.request(key, priority, FarlandsSettings.generate);
      }

      if (mesh != null && mesh.ready) {
         if (mesh.preview) {
            world.upgrade(key, priority);
         }

         mesh.quadMask = 15;
         this.selected.add(mesh);
         this.leaves.add(key);
         return true;
      } else {
         return false;
      }
   }

   private LodRenderer.@Nullable Mesh meshFor(LodWorld world, long key, double priority, boolean leaf) {
      LodRenderer.Mesh mesh = (LodRenderer.Mesh)this.meshes.get(key);
      LodMesher.Format wanted = this.shaderMode ? LodMesher.Format.SHADER : LodMesher.Format.FARLANDS;
      if (mesh != null) {
         mesh.lastFrame = this.frame;
         if (mesh.ready && !mesh.dirty && mesh.format == wanted) {
            return mesh;
         }
      }

      if (this.meshing.contains(key) || this.meshing.size() + this.built.size() > 96) {
         return mesh;
      }

      if (mesh != null && this.frame < mesh.retryFrame) {
         return mesh;
      }

      LodTile tile = world.get(key);
      if (tile == null) {
         if (mesh != null && mesh.dirty) {
            world.request(key, priority, FarlandsSettings.generate, leaf);
         }

         return mesh;
      } else {
         if (mesh == null) {
            Integer slot = this.freeSlots.poll();
            if (slot == null) {
               this.reclaim = true;
               return null;
            }

            mesh = new LodRenderer.Mesh(key, slot);
            this.meshes.put(key, mesh);
            this.bySlot[slot] = mesh;
         }

         mesh.lastFrame = this.frame;
         LodMesher.Format format = wanted;
         if (this.biomes != null) {
            mesh.dirty = false;
            mesh.preview = !tile.complete && world.isPreview(key);
            this.meshing.add(key);
            int minY = tile.minY;
            int maxY = tile.maxY;
            int surfaceY = tile.surfaceY;
            int slot = mesh.slot;
            Registry<Biome> biomes = this.biomes;
            boolean layer = this.waterLayer;
            int generation = this.meshGeneration;
            int floor = this.floor;
            this.pool.submit(priority - 1000.0, () -> {
               try {
                  LodMesher mesher = this.meshers.get();
                  LodMesher.Mesh built = mesher.build(tile, slot, biomes, format, layer, floor);
                  this.built.add(new LodRenderer.Built(key, Math.min(minY, mesher.lowest()), maxY, surfaceY, slot, generation, built));
               } catch (RuntimeException | Error e) {
                  this.meshing.remove(key);
                  throw e;
               }
            }, () -> this.meshing.remove(key));
         }

         return mesh;
      }
   }

   private void cull(Frustum frustum, int minY, int vanillaChunks) {
      int camChunkX = this.cameraBlockX >> 4;
      int camChunkZ = this.cameraBlockZ >> 4;
      this.maxQuads = 0;
      long quads = 0L;

      for (LodRenderer.Mesh mesh : this.selected) {
         mesh.lastFrame = this.frame;
         if ((mesh.quads != 0 || mesh.waterQuads != 0) && (this.shaderMode ? mesh.vao != 0 : mesh.allocation != null || mesh.water != null)) {
            int level = LodTile.keyLevel(mesh.key);
            int size = 64 << level;
            double x0 = (double)LodTile.keyX(mesh.key) * size;
            double z0 = (double)LodTile.keyZ(mesh.key) * size;
            double y0 = mesh.minY - 64;
            double y1 = mesh.maxY + 32;
            int inside = frustum.cubeInFrustum(new BoundingBox((int)x0, (int)y0, (int)z0, (int)x0 + size - 1, (int)y1 - 1, (int)z0 + size - 1));
            if (inside == -2 || inside == -1) {
               mesh.subMask = inside != -2 && !this.shaderMode ? subMask(frustum, mesh, (int)x0, (int)z0, size >> 6) : -1;
               int cx0 = Math.floorDiv((int)x0, 16);
               int cz0 = Math.floorDiv((int)z0, 16);
               int cx1 = Math.floorDiv((int)x0 + size - 1, 16);
               int cz1 = Math.floorDiv((int)z0 + size - 1, 16);
               boolean near = cx1 >= camChunkX - vanillaChunks
                  && cx0 <= camChunkX + vanillaChunks
                  && cz1 >= camChunkZ - vanillaChunks
                  && cz0 <= camChunkZ + vanillaChunks;
               (near && !this.shaderMode ? this.clipped : this.draws).add(mesh);
               this.maxQuads = Math.max(this.maxQuads, Math.max(mesh.quads, mesh.waterQuads));
               quads += (!this.shaderMode && mesh.allocation != null ? this.visibleQuads(mesh) : mesh.quads) + mesh.waterQuads;
            }
         }
      }

      this.drawnQuads = quads;
   }

   public long hiddenQuads() {
      return this.hiddenQuads;
   }

   private void occlude(Vec3 camera, int vanillaChunks) {
      this.hiddenQuads = 0L;
      if (!this.shaderMode && this.occlusionEnabled) {
         long hidden = 0L;

         for (LodRenderer.Mesh mesh : this.clipped) {
            hidden += this.hide(mesh, this.coveredSubs(mesh, vanillaChunks));
         }

         for (LodRenderer.Mesh mesh : this.draws) {
            if (mesh.quadMask != 15) {
               hidden += this.hide(mesh, this.maskedSubs(mesh));
            }
         }

         for (LodRenderer.Mesh mesh : this.clipped) {
            if (mesh.quadMask != 15) {
               hidden += this.hide(mesh, this.maskedSubs(mesh));
            }
         }

         if (this.selectionComplete && !insideBlock(camera)) {
            long hash = this.selectionHash(vanillaChunks) ^ (this.canopyEnabled ? -7046029254386353131L : 0L);
            this.stableFrames = hash == this.lastHash && camera.equals(this.lastCamera) ? this.stableFrames + 1 : 0;
            this.lastHash = hash;
            this.lastCamera = camera;
            LodOcclusion.Result result = this.occlusion.result();
            if (result != null && result.covers(camera.x, camera.y, camera.z, hash)) {
               hidden += this.applyHorizon(result, this.draws) + this.applyHorizon(result, this.clipped);
            } else if (this.stableFrames >= 8 && !this.occlusion.busy()) {
               this.occlusion.submit(this.occlusionJob(camera, vanillaChunks, hash));
            }
         }

         this.hiddenQuads = hidden;
         this.drawnQuads -= hidden;
      }
   }

   private static boolean insideBlock(Vec3 camera) {
      ClientLevel level = Minecraft.getInstance().level;
      return level == null || level.getBlockState(BlockPos.containing(camera)).isSolidRender();
   }

   private long selectionHash(int vanillaChunks) {
      long hash = vanillaChunks;

      for (LodRenderer.Mesh mesh : this.selected) {
         hash += HashCommon.mix(mesh.key * 31L + mesh.version * -7046029254386353131L + mesh.quadMask);
      }

      return hash;
   }

   private LodOcclusion.Job occlusionJob(Vec3 camera, int vanillaChunks, long hash) {
      LodOcclusion.Job job = new LodOcclusion.Job();
      job.x = camera.x;
      job.y = camera.y;
      job.z = camera.z;
      job.tolerance = 0.001;
      job.margin = 0.5;
      job.leafRadius = (vanillaChunks + 2) * 24.0;
      job.canopy = this.canopyEnabled;
      job.hash = hash;

      for (LodRenderer.Mesh mesh : this.selected) {
         if (mesh.format == LodMesher.Format.FARLANDS && mesh.allocation != null) {
            job.add(mesh.key, mesh.version, mesh.quadMask, mesh.subBounds, mesh.occluders);
         }
      }

      return job;
   }

   private long applyHorizon(LodOcclusion.Result result, List<LodRenderer.Mesh> list) {
      long hidden = 0L;

      for (LodRenderer.Mesh mesh : list) {
         long value = result.visible.get(mesh.key);
         if (value != -1L && (int)(value >>> 32) == mesh.version) {
            hidden += this.hide(mesh, ~((int)value));
         }
      }

      return hidden;
   }

   private long hide(LodRenderer.Mesh mesh, int subs) {
      int clear = mesh.subMask & subs;
      if (clear == 0) {
         return 0L;
      }

      mesh.subMask &= ~clear;
      if (mesh.allocation == null) {
         return 0L;
      }

      int size = 64 << LodTile.keyLevel(mesh.key);
      double x0 = (double)LodTile.keyX(mesh.key) * size;
      double z0 = (double)LodTile.keyZ(mesh.key) * size;
      long quads = 0L;

      for (int i = 0; i < 7; i++) {
         if (mesh.buckets[i] != 0 && facing(LodMesher.BUCKET_FACES[i], this.camera, x0, z0, size, mesh.minY, mesh.maxY)) {
            for (int part = 0; part < 32; part++) {
               if ((clear >>> part & 1) != 0) {
                  quads += mesh.groups[i * 32 + part];
               }
            }
         }
      }

      return quads;
   }

   private int maskedSubs(LodRenderer.Mesh mesh) {
      int cell = 1 << LodTile.keyLevel(mesh.key);
      int half = 32 * cell;
      int subs = 0;

      for (int sub = 0; sub < 32; sub++) {
         int xz = mesh.subBounds[sub * 2];
         if (xz != 0 || mesh.subBounds[sub * 2 + 1] != 0) {
            int minX = (xz & 0xFF) * cell - 1;
            int maxX = (xz >>> 8 & 0xFF) * cell + 1;
            int minZ = (xz >>> 16 & 0xFF) * cell - 1;
            int maxZ = (xz >>> 24 & 0xFF) * cell + 1;
            boolean shown = false;

            for (int q = 0; q < 4 && !shown; q++) {
               boolean overlapX = (q & 1) != 0 ? maxX >= half : minX < half;
               boolean overlapZ = (q & 2) != 0 ? maxZ >= half : minZ < half;
               shown = overlapX && overlapZ && (mesh.quadMask >> q & 1) != 0;
            }

            if (!shown) {
               subs |= 1 << sub;
            }
         }
      }

      return subs;
   }

   private int coveredSubs(LodRenderer.Mesh mesh, int vanillaChunks) {
      VanillaCoverage cover = this.coverage;
      if (cover != null && this.maskVersion >= 0) {
         int level = LodTile.keyLevel(mesh.key);
         int size = 64 << level;
         int cell = 1 << level;
         int x0 = LodTile.keyX(mesh.key) * size;
         int z0 = LodTile.keyZ(mesh.key) * size;
         int reach = Math.max(1, vanillaChunks - 1);
         int camX = this.cameraBlockX >> 4;
         int camZ = this.cameraBlockZ >> 4;
         int camSection = this.cameraBlockY >> 4;
         int minSection = cover.minSection();
         int subs = 0;

         for (int sub = 0; sub < 32; sub++) {
            if ((mesh.subMask >>> sub & 1) != 0) {
               int xz = mesh.subBounds[sub * 2];
               int y = mesh.subBounds[sub * 2 + 1];
               if (xz != 0 || y != 0) {
                  int lowSection = Math.floorDiv(Math.floorDiv(y & 65535, 16) - 2048 - 1, 16);
                  int highSection = Math.floorDiv(Math.floorDiv((y >>> 16) + 15, 16) - 2048 + 1, 16);
                  if (lowSection >= camSection - reach && highSection <= camSection + reach) {
                     int first = lowSection - minSection;
                     int last = highSection - minSection;
                     if (first >= 0 && last <= 63) {
                        long need = (last == 63 ? -1L : (1L << last + 1) - 1L) & -(1L << first);
                        int cx0 = Math.floorDiv(x0 + (xz & 0xFF) * cell - 1, 16);
                        int cx1 = Math.floorDiv(x0 + (xz >>> 8 & 0xFF) * cell + 1, 16);
                        int cz0 = Math.floorDiv(z0 + (xz >>> 16 & 0xFF) * cell - 1, 16);
                        int cz1 = Math.floorDiv(z0 + (xz >>> 24 & 0xFF) * cell + 1, 16);
                        boolean all = true;

                        for (int cz = cz0; cz <= cz1 && all; cz++) {
                           for (int cx = cx0; cx <= cx1 && all; cx++) {
                              if (!inside(cx - camX, cz - camZ, vanillaChunks)) {
                                 all = false;
                              } else {
                                 int index = ((cz & 127) * 128 + (cx & 127)) * 2;
                                 long bits = this.mask[index] & 4294967295L | (long)this.mask[index + 1] << 32;
                                 all = (bits & need) == need;
                              }
                           }
                        }

                        if (all) {
                           subs |= 1 << sub;
                        }
                     }
                  }
               }
            }
         }

         return subs;
      } else {
         return 0;
      }
   }

   private void writeTiles(Vec3 camera) {
      if (!this.draws.isEmpty() || !this.clipped.isEmpty()) {
         this.tileRing.rotate();
         MappedView view = this.tileRing.currentBuffer().map(false, true);

         try {
            ByteBuffer data = view.data().order(ByteOrder.nativeOrder());

            for (LodRenderer.Mesh mesh : this.allDraws()) {
               int level = LodTile.keyLevel(mesh.key);
               int size = 64 << level;
               int base = mesh.slot * 16;
               data.putFloat(base, (float)((double)LodTile.keyX(mesh.key) * size - camera.x));
               data.putFloat(base + 4, (float)camera.y);
               data.putFloat(base + 8, (float)((double)LodTile.keyZ(mesh.key) * size - camera.z));
               data.putFloat(base + 12, mesh.quadMask + 16 * size);
            }
         } catch (Throwable var10) {
            if (view != null) {
               try {
                  view.close();
               } catch (Throwable var9) {
                  var10.addSuppressed(var9);
               }
            }

            throw var10;
         }

         if (view != null) {
            view.close();
         }
      }
   }

   private void writeSprites(CommandEncoder encoder) {
      int version = this.sprites.version();
      if (version != this.uploadedSpriteVersion) {
         int count = this.sprites.count();
         if (count != 0) {
            ByteBuffer data = MemoryUtil.memAlloc(count * 32);

            try {
               float[] values = this.sprites.data();

               for (int i = 0; i < count * 8; i++) {
                  data.putFloat(values[i]);
               }

               data.flip();
               encoder.writeToBuffer(this.spriteBuffer.slice(0L, count * 32L), data);
            } finally {
               MemoryUtil.memFree(data);
            }

            ByteBuffer var15 = MemoryUtil.memCalloc(262160);

            try {
               this.sprites.waterData(var15.asFloatBuffer(), 16384);
               encoder.writeToBuffer(this.waterSpriteBuffer.slice(), var15);
            } finally {
               MemoryUtil.memFree(var15);
            }

            this.uploadedSpriteVersion = version;
            this.uploadedSprites = count;
         }
      }
   }

   private void writeMask(CommandEncoder encoder, VanillaCoverage cover, int vanillaChunks, Vec3 camera) {
      if (this.maskDirty || cover.version() != this.maskVersion) {
         this.maskDirty = false;
         this.maskVersion = cover.version();
         Arrays.fill(this.mask, 0);
         int camX = (int)Math.floor(camera.x) >> 4;
         int camZ = (int)Math.floor(camera.z) >> 4;
         int radius = Math.min(vanillaChunks, 63);

         for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
               if (inside(dx, dz, vanillaChunks)) {
                  int cx = camX + dx;
                  int cz = camZ + dz;
                  long bits = cover.bits(ChunkPos.pack(cx, cz));
                  if (bits != 0L) {
                     int index = ((cz & 127) * 128 + (cx & 127)) * 2;
                     this.mask[index] = (int)bits;
                     this.mask[index + 1] = (int)(bits >>> 32);
                  }
               }
            }
         }

         ByteBuffer data = MemoryUtil.memAlloc(this.mask.length * 4);

         try {
            data.asIntBuffer().put(this.mask);
            encoder.writeToBuffer(this.maskBuffer.slice(), data);
         } finally {
            MemoryUtil.memFree(data);
         }
      }
   }

   private void writeInfo(Matrix4fc view, int vanillaChunks, Vec3 camera) {
      this.infoRing.rotate();
      AbstractTexture atlas = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
      int mips = atlas.getTexture().getMipLevels();
      MappedView view2 = this.infoRing.currentBuffer().map(false, true);

      try {
         Std140Builder.intoBuffer(view2.data())
            .putMat4f(view)
            .putVec4(mips - 1, FarlandsSettings.textures ? 1.0F : 0.0F, vanillaChunks, this.coverage == null ? 0.0F : this.coverage.minSection())
            .putIVec4(this.cameraBlockX, this.cameraBlockY, this.cameraBlockZ, SkyFog.environmentSetByOthers() ? 1 : 0)
            .putVec4(
               (float)(camera.x - this.cameraBlockX), (float)(camera.y - this.cameraBlockY), (float)(camera.z - this.cameraBlockZ), SkyFog.legacy ? 1.0F : 0.0F
            );
      } catch (Throwable var10) {
         if (view2 != null) {
            try {
               view2.close();
            } catch (Throwable var9) {
               var10.addSuppressed(var9);
            }
         }

         throw var10;
      }

      if (view2 != null) {
         view2.close();
      }
   }

   public void renderShader(boolean fluid, float near) {
      if (this.shaderMode && this.shaderPath != null) {
         boolean any = false;
         if (this.world != null) {
            for (LodRenderer.Mesh mesh : this.draws) {
               if (fluid ? mesh.fluidQuads > 0 : mesh.quads - mesh.fluidQuads > 0) {
                  any = true;
                  break;
               }
            }
         }

         if (!any) {
            if (!fluid) {
               this.shaderPath.clear();
            }
         } else {
            this.shaderPath.begin(fluid, near);

            try {
               for (LodRenderer.Mesh mesh : this.draws) {
                  int level = LodTile.keyLevel(mesh.key);
                  int size = 64 << level;
                  float ox = (float)((double)LodTile.keyX(mesh.key) * size - this.camera.x);
                  float oy = (float)(-2048.0 - this.camera.y);
                  float oz = (float)((double)LodTile.keyZ(mesh.key) * size - this.camera.z);
                  int solid = mesh.quads - mesh.fluidQuads;
                  if (fluid) {
                     this.shaderPath.draw(true, mesh.vao, ox, oy, oz, solid, mesh.fluidQuads);
                  } else {
                     this.shaderPath.draw(false, mesh.vao, ox, oy, oz, 0, solid);
                  }
               }
            } finally {
               this.shaderPath.end(fluid);
            }
         }
      }
   }

   private void writeWater(CommandEncoder encoder, GpuDevice device, Matrix4fc view, int vanillaChunks) {
      this.waterSegments.clear();
      this.waterSegmentCommands.clear();
      this.waterBatchedFrame = false;
      if (this.waterLayer) {
         this.waterRadius = vanillaChunks;

         for (LodRenderer.Mesh mesh : this.draws) {
            if (mesh.water != null) {
               mesh.waterClip = false;
               this.waterDraws.add(mesh);
            }
         }

         for (LodRenderer.Mesh mesh : this.clipped) {
            if (mesh.water != null) {
               mesh.waterClip = true;
               this.waterDraws.add(mesh);
            }
         }

         int count = this.waterDraws.size();
         if (count != 0) {
            GpuTexture atlas = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTexture();
            this.waterTerrain = RenderSystem.getDynamicUniforms().writeTerrainTransform(view, atlas.getWidth(0), atlas.getHeight(0));
            boolean batch = !this.vanillaWater && batchSupported(device);
            boolean singles = !batch;

            for (int i = 0; i < count && !singles; i++) {
               singles = this.waterDraws.get(i).waterAllocation == null;
            }

            if (singles) {
               this.writeWaterSections(encoder, device, count);
            }

            if (batch) {
               this.writeWaterCommands(encoder, device);
            }
         }
      }
   }

   private void writeWaterSections(CommandEncoder encoder, GpuDevice device, int count) {
      int align = device.getDeviceInfo().limits().minUniformOffsetAlignment();
      int stride = (DynamicGpuData.CHUNK_SECTION_UBO_SIZE + align - 1) / align * align;
      if (this.waterSections == null || this.waterSectionCapacity < count) {
         if (this.waterSections != null) {
            this.waterSections.close();
         }

         this.waterSectionCapacity = Math.max(64, Integer.highestOneBit(count) << 1);
         this.waterSections = device.createBuffer(() -> "Farlands water sections", 136, (long)this.waterSectionCapacity * stride);
         this.waterSectionData = ByteBuffer.allocateDirect(this.waterSectionCapacity * stride).order(ByteOrder.nativeOrder());
      }

      ByteBuffer data = this.waterSectionData;
      data.clear();

      for (int i = 0; i < count; i++) {
         LodRenderer.Mesh mesh = this.waterDraws.get(i);
         int size = 64 << LodTile.keyLevel(mesh.key);
         int offset = i * stride;
         mesh.waterOffset = offset;
         data.putInt(offset, LodTile.keyX(mesh.key) * size);
         data.putInt(offset + 4, 0);
         data.putInt(offset + 8, LodTile.keyZ(mesh.key) * size);
         data.putFloat(offset + 12, 1.0F);
      }

      data.position(0).limit(count * stride);
      encoder.writeToBuffer(this.waterSections.slice(0L, (long)count * stride), data);
   }

   private void writeWaterCommands(CommandEncoder encoder, GpuDevice device) {
      int bound = 0;
      int instances = 0;

      for (LodRenderer.Mesh mesh : this.waterDraws) {
         if (mesh.waterAllocation != null) {
            instances++;
            bound += mesh.quadMask == 15 && !mesh.waterClip ? 1 : Math.max(1, mesh.waterPatches.length / 5);
         }
      }

      if (instances == 0) {
         for (LodRenderer.Mesh mesh : this.waterDraws) {
            this.waterSegments.add(mesh);
            this.waterSegmentCommands.add(-1);
            this.waterSegmentCommands.add(-1);
         }

         this.waterBatchedFrame = true;
      } else {
         if (this.waterInstances == null || this.waterInstanceCapacity < instances) {
            if (this.waterInstances != null) {
               this.waterInstances.close();
            }

            this.waterInstanceCapacity = Math.max(256, Integer.highestOneBit(instances) << 1);
            this.waterInstances = device.createBuffer(() -> "Farlands water instances", 40, this.waterInstanceCapacity * 16L);
            this.waterInstanceData = ByteBuffer.allocateDirect(this.waterInstanceCapacity * 16).order(ByteOrder.nativeOrder());
         }

         if (this.waterCommands == null || this.waterCommandCapacity < bound) {
            if (this.waterCommands != null) {
               this.waterCommands.close();
            }

            this.waterCommandCapacity = Math.max(1024, Integer.highestOneBit(bound) << 1);
            this.waterCommands = device.createBuffer(() -> "Farlands water commands", 520, this.waterCommandCapacity * 20L);
            this.waterCommandData = ByteBuffer.allocateDirect(this.waterCommandCapacity * 20).order(ByteOrder.nativeOrder());
         }

         ByteBuffer inst = this.waterInstanceData;
         ByteBuffer cmd = this.waterCommandData;
         inst.clear();
         cmd.clear();
         int instance = 0;
         int commands = 0;
         int segmentStart = 0;
         LodRenderer.WaterArena current = null;

         for (LodRenderer.Mesh mesh : this.waterDraws) {
            if (mesh.waterAllocation == null) {
               if (current != null && commands > segmentStart) {
                  this.addWaterSegment(current, segmentStart, commands - segmentStart);
               }

               current = null;
               segmentStart = commands;
               this.waterSegments.add(mesh);
               this.waterSegmentCommands.add(-1);
               this.waterSegmentCommands.add(-1);
            } else {
               if (mesh.waterArena != current) {
                  if (current != null && commands > segmentStart) {
                     this.addWaterSegment(current, segmentStart, commands - segmentStart);
                  }

                  current = mesh.waterArena;
                  segmentStart = commands;
               }

               int size = 64 << LodTile.keyLevel(mesh.key);
               inst.putInt(LodTile.keyX(mesh.key) * size).putInt(0).putInt(LodTile.keyZ(mesh.key) * size).putFloat(1.0F);
               if (mesh.quadMask == 15 && !mesh.waterClip) {
                  cmd.putInt(mesh.waterQuads * 6).putInt(1).putInt(0).putInt(mesh.waterBase).putInt(instance);
                  commands++;
               } else {
                  int level = LodTile.keyLevel(mesh.key);
                  int runStart = -1;
                  int runQuads = 0;

                  for (int patch = 0; patch < mesh.waterPatches.length; patch += 5) {
                     int start = mesh.waterPatches[patch + 2];
                     int quads = mesh.waterPatches[patch + 3];
                     if (quads > 0 && this.waterPatchVisible(mesh, patch, 1 << level, size)) {
                        if (runStart >= 0 && runStart + runQuads == start) {
                           runQuads += quads;
                        } else {
                           if (runStart >= 0) {
                              cmd.putInt(runQuads * 6).putInt(1).putInt(0).putInt(mesh.waterBase + runStart * 4).putInt(instance);
                              commands++;
                           }

                           runStart = start;
                           runQuads = quads;
                        }
                     }
                  }

                  if (runStart >= 0) {
                     cmd.putInt(runQuads * 6).putInt(1).putInt(0).putInt(mesh.waterBase + runStart * 4).putInt(instance);
                     commands++;
                  }
               }

               instance++;
            }
         }

         if (current != null && commands > segmentStart) {
            this.addWaterSegment(current, segmentStart, commands - segmentStart);
         }

         inst.flip();
         cmd.flip();
         if (inst.remaining() > 0) {
            encoder.writeToBuffer(this.waterInstances.slice(0L, inst.remaining()), inst);
         }

         if (cmd.remaining() > 0) {
            encoder.writeToBuffer(this.waterCommands.slice(0L, cmd.remaining()), cmd);
         }

         this.waterBatchedFrame = true;
      }
   }

   private void addWaterSegment(LodRenderer.WaterArena arena, int start, int count) {
      this.waterSegments.add(arena);
      this.waterSegmentCommands.add(start);
      this.waterSegmentCommands.add(count);
   }

   private boolean waterPatchVisible(LodRenderer.Mesh mesh, int patch, int scale, int size) {
      int[] patches = mesh.waterPatches;
      int localX = patches[patch] * scale;
      int localZ = patches[patch + 1] * scale;
      if (mesh.quadMask != 15) {
         int quadrant = (localX >= size / 2 ? 1 : 0) + (localZ >= size / 2 ? 2 : 0);
         if ((mesh.quadMask >> quadrant & 1) == 0) {
            return false;
         }
      }

      VanillaCoverage cover = this.coverage;
      if (mesh.waterClip && cover != null) {
         int radius = this.waterRadius;
         int reach = Math.max(radius - 1, 1);
         int section = Math.floorDiv(patches[patch + 4] - 1, 16);
         if (Math.abs(section - (this.cameraBlockY >> 4)) > reach) {
            return true;
         }

         int bit = section - cover.minSection();
         if (bit >= 0 && bit <= 63) {
            int extent = Math.max(16, (scale <= 16 ? 16 / scale : 4) * scale);
            int x0 = LodTile.keyX(mesh.key) * size + localX;
            int z0 = LodTile.keyZ(mesh.key) * size + localZ;
            int camX = this.cameraBlockX >> 4;
            int camZ = this.cameraBlockZ >> 4;

            for (int cz = z0 >> 4; cz <= z0 + extent - 1 >> 4; cz++) {
               for (int cx = x0 >> 4; cx <= x0 + extent - 1 >> 4; cx++) {
                  if (!inside(cx - camX, cz - camZ, radius)) {
                     return true;
                  }

                  if ((cover.bits(ChunkPos.pack(cx, cz)) >>> bit & 1L) == 0L) {
                     return true;
                  }
               }
            }

            return false;
         } else {
            return true;
         }
      } else {
         return true;
      }
   }

   private void bindWater(RenderPass pass, Minecraft mc, AbstractTexture atlas, AutoStorageIndexBuffer indices, RenderPipeline pipeline) {
      RenderSystem.bindDefaultUniforms(pass);
      pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
      pass.setUniform("FarlandsInfo", this.infoRing.currentBuffer());
      pass.setUniform("TerrainUniform", this.waterTerrain);
      pass.setUniform("Sampler0", atlas.getTextureView(), this.atlasSampler);
      pass.setUniform("Sampler2", mc.gameRenderer.lightmap(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
      pass.setUniform("FarlandsWaterSprites", this.waterSpriteBuffer);
      pass.setIndexBuffer(indices.getBuffer(), indices.type());
   }

   private void drawWaterSingle(RenderPass pass, LodRenderer.Mesh mesh) {
      if (mesh.water != null && this.waterSections != null) {
         pass.setUniform("ChunkSection", this.waterSections.slice(mesh.waterOffset, DynamicGpuData.CHUNK_SECTION_UBO_SIZE));
         if (this.vanillaWater) {
            pass.setVertexBuffer(0, mesh.water.slice());
         } else {
            pass.setVertexBuffer(0, null);
            pass.setUniform("FarlandsWater", mesh.water);
         }

         if (mesh.quadMask == 15 && !mesh.waterClip) {
            pass.drawIndexed(mesh.waterQuads * 6, 1, 0, mesh.waterBase, 0);
         } else {
            int level = LodTile.keyLevel(mesh.key);
            int size = 64 << level;
            int runStart = -1;
            int runQuads = 0;

            for (int patch = 0; patch < mesh.waterPatches.length; patch += 5) {
               int start = mesh.waterPatches[patch + 2];
               int quads = mesh.waterPatches[patch + 3];
               if (quads > 0 && this.waterPatchVisible(mesh, patch, 1 << level, size)) {
                  if (runStart >= 0 && runStart + runQuads == start) {
                     runQuads += quads;
                  } else {
                     if (runStart >= 0) {
                        pass.drawIndexed(runQuads * 6, 1, 0, mesh.waterBase + runStart * 4, 0);
                     }

                     runStart = start;
                     runQuads = quads;
                  }
               }
            }

            if (runStart >= 0) {
               pass.drawIndexed(runQuads * 6, 1, 0, mesh.waterBase + runStart * 4, 0);
            }
         }
      }
   }

   private void renderWater(RenderPass pass) {
      if (!this.shaderMode && !this.waterDraws.isEmpty() && this.world != null && this.waterTerrain != null) {
         Minecraft mc = Minecraft.getInstance();
         AbstractTexture atlas = mc.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
         AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
         pass.pushDebugGroup(() -> "Farlands water");
         if (this.vanillaWater) {
            RenderSystem.bindDefaultUniforms(pass);
            pass.setPipeline(RenderSystem.getCompiledPipeline(RenderPipelines.TRANSLUCENT_TERRAIN));
            pass.setUniform("TerrainUniform", this.waterTerrain);
            pass.setUniform("Sampler0", atlas.getTextureView(), this.atlasSampler);
            pass.setUniform("Sampler2", mc.gameRenderer.lightmap(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.setIndexBuffer(indices.getBuffer(), indices.type());

            for (LodRenderer.Mesh mesh : this.waterDraws) {
               this.drawWaterSingle(pass, mesh);
            }

            pass.popDebugGroup();
         } else if (!this.waterBatchedFrame) {
            this.bindWater(pass, mc, atlas, indices, FarlandsPipelines.WATER);

            for (LodRenderer.Mesh mesh : this.waterDraws) {
               this.drawWaterSingle(pass, mesh);
            }

            pass.popDebugGroup();
         } else {
            int max = RenderSystem.getDevice().getDeviceInfo().limits().maxDrawIndirectDrawCount();
            int bound = 0;

            for (int s = 0; s < this.waterSegments.size(); s++) {
               Object segment = this.waterSegments.get(s);
               if (segment instanceof LodRenderer.WaterArena arena) {
                  if (bound != 1) {
                     this.bindWater(pass, mc, atlas, indices, FarlandsPipelines.WATER_MULTIDRAW);
                     pass.setVertexBuffer(0, this.waterInstances.slice());
                     bound = 1;
                  }

                  pass.setUniform("FarlandsWater", arena.buffer);
                  int start = this.waterSegmentCommands.getInt(s * 2);
                  int left = this.waterSegmentCommands.getInt(s * 2 + 1);

                  while (left > 0) {
                     int n = Math.min(left, max);
                     pass.drawIndexedIndirect(this.waterCommands.slice(start * 20L, n * 20L), n);
                     start += n;
                     left -= n;
                  }
               } else {
                  if (bound != 2) {
                     this.bindWater(pass, mc, atlas, indices, FarlandsPipelines.WATER);
                     bound = 2;
                  }

                  this.drawWaterSingle(pass, (LodRenderer.Mesh)segment);
               }
            }

            pass.popDebugGroup();
         }
      }
   }

   public void probePipelines() {
      if (this.pipelineSeen) {
         if (!this.pipelineProbed) {
            this.pipelineProbed = true;
            this.pipelineModified = this.pipelineObserved;
            this.pipelineVotes = 0;
         } else if (this.pipelineObserved == this.pipelineModified) {
            this.pipelineVotes = 0;
         } else if (++this.pipelineVotes >= 3) {
            this.pipelineModified = this.pipelineObserved;
            this.pipelineVotes = 0;
         }
      }

      this.pipelineSeen = false;
      this.plainTranslucent = RenderSystem.getCompiledPipelineNullable(RenderPipelines.TRANSLUCENT_TERRAIN);
   }

   private void observePipelines() {
      CompiledRenderPipeline plain = this.plainTranslucent;
      if (plain != null) {
         CompiledRenderPipeline now = RenderSystem.getCompiledPipelineNullable(RenderPipelines.TRANSLUCENT_TERRAIN);
         this.pipelineObserved = now != null && now != plain;
         this.pipelineSeen = true;
      }
   }

   public void renderFluid(RenderPass pass) {
      this.observePipelines();
      if (this.waterLayer) {
         this.renderWater(pass);
      } else if (this.lateFluid) {
         this.lateFluid = false;
         if (!this.shaderMode && !this.clipped.isEmpty() && this.world != null) {
            Minecraft mc = Minecraft.getInstance();
            AbstractTexture atlas = mc.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
            pass.pushDebugGroup(() -> "Farlands fluid");
            RenderSystem.bindDefaultUniforms(pass);
            pass.setPipeline(RenderSystem.getCompiledPipeline(FarlandsPipelines.FLUID_CLIP));
            this.bind(pass, mc, atlas, RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS), true);
            this.drawList(pass, this.clipped, 1);
            this.drawList(pass, this.clipped, 2);
            pass.popDebugGroup();
         }
      }
   }

   public void render(RenderPass pass, boolean late) {
      this.lateFluid = late;
      if (!this.shaderMode && (!this.draws.isEmpty() || !this.clipped.isEmpty()) && this.world != null) {
         Minecraft mc = Minecraft.getInstance();
         AbstractTexture atlas = mc.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
         pass.pushDebugGroup(() -> "Farlands");
         RenderSystem.bindDefaultUniforms(pass);
         AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
         if (!this.draws.isEmpty()) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(FarlandsPipelines.TERRAIN));
            this.bind(pass, mc, atlas, indices, false);
            this.drawList(pass, this.draws, 0);
         }

         if (!this.clipped.isEmpty()) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(FarlandsPipelines.TERRAIN_CLIP));
            this.bind(pass, mc, atlas, indices, true);
            this.drawList(pass, this.clipped, 0);
         }

         this.drawCompact(pass, mc, atlas, indices);

         for (int part = 1; part <= 2; part++) {
            if (!this.draws.isEmpty()) {
               pass.setPipeline(RenderSystem.getCompiledPipeline(FarlandsPipelines.FLUID));
               this.bind(pass, mc, atlas, indices, false);
               this.drawList(pass, this.draws, part);
            }

            if (!this.clipped.isEmpty() && !late) {
               pass.setPipeline(RenderSystem.getCompiledPipeline(FarlandsPipelines.FLUID_CLIP));
               this.bind(pass, mc, atlas, indices, true);
               this.drawList(pass, this.clipped, part);
            }
         }

         pass.popDebugGroup();
      }
   }

   private void bind(RenderPass pass, Minecraft mc, AbstractTexture atlas, AutoStorageIndexBuffer indices, boolean clip) {
      RenderSystem.bindDefaultUniforms(pass);
      pass.setUniform("Sampler0", atlas.getTextureView(), this.atlasSampler);
      pass.setUniform("Sampler2", mc.gameRenderer.lightmap(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
      pass.setUniform("FarlandsInfo", this.infoRing.currentBuffer());
      pass.setUniform("FarlandsTiles", this.tileRing.currentBuffer());
      pass.setUniform("FarlandsSprites", this.spriteBuffer);
      if (clip) {
         pass.setUniform("FarlandsMask", this.maskBuffer);
      }

      pass.setVertexBuffer(0, null);
      pass.setIndexBuffer(indices.getBuffer(), indices.type());
   }

   private static int quadsOf(LodRenderer.Mesh mesh, int part) {
      return part == 0 ? mesh.quads - mesh.fluidQuads : (part == 1 ? mesh.fluidQuads - mesh.surfaceQuads : mesh.surfaceQuads);
   }

   private static int baseOf(LodRenderer.Mesh mesh, int part) {
      if (mesh.materials > 0) {
         return (int)(mesh.allocation.getOffsetFromHeap() / 8L + mesh.materials) * 4;
      }

      int base = (int)(mesh.allocation.getOffsetFromHeap() / 16L) * 4;
      return part == 0 ? base : (part == 1 ? base + (mesh.quads - mesh.fluidQuads) * 4 : base + (mesh.quads - mesh.surfaceQuads) * 4);
   }

   private int ranges(LodRenderer.Mesh mesh, int part) {
      if (part != 0) {
         int quads = quadsOf(mesh, part);
         if (quads == 0) {
            return 0;
         }

         this.rangeBase[0] = baseOf(mesh, part);
         this.rangeQuads[0] = quads;
         return 1;
      } else {
         int size = 64 << LodTile.keyLevel(mesh.key);
         double x0 = (double)LodTile.keyX(mesh.key) * size;
         double z0 = (double)LodTile.keyZ(mesh.key) * size;
         Vec3 c = this.camera;
         int base = baseOf(mesh, 0);
         int n = 0;
         boolean open = false;
         boolean whole = mesh.subMask == -1;

         for (int i = 0; i < 7; i++) {
            int quads = mesh.buckets[i];
            if (quads != 0) {
               if (!facing(LodMesher.BUCKET_FACES[i], c, x0, z0, size, mesh.minY, mesh.maxY)) {
                  open = false;
                  base += quads * 4;
               } else if (whole) {
                  if (open) {
                     this.rangeQuads[n - 1] = this.rangeQuads[n - 1] + quads;
                  } else {
                     this.rangeBase[n] = base;
                     this.rangeQuads[n] = quads;
                     n++;
                     open = true;
                  }

                  base += quads * 4;
               } else {
                  for (int sub = 0; sub < 32; sub++) {
                     int count = mesh.groups[i * 32 + sub];
                     if (count != 0) {
                        if ((mesh.subMask >>> sub & 1) != 0) {
                           if (open) {
                              this.rangeQuads[n - 1] = this.rangeQuads[n - 1] + count;
                           } else {
                              this.rangeBase[n] = base;
                              this.rangeQuads[n] = count;
                              n++;
                              open = true;
                           }
                        } else {
                           open = false;
                        }

                        base += count * 4;
                     }
                  }
               }
            }
         }

         return n;
      }
   }

   private static int subMask(Frustum frustum, LodRenderer.Mesh mesh, int x0, int z0, int cell) {
      int mask = 0;
      int[] bounds = mesh.subBounds;

      for (int sub = 0; sub < 32; sub++) {
         int xz = bounds[sub * 2];
         int y = bounds[sub * 2 + 1];
         if (xz != 0 || y != 0) {
            int minX = x0 + (xz & 0xFF) * cell;
            int maxX = x0 + (xz >>> 8 & 0xFF) * cell;
            int minZ = z0 + (xz >>> 16 & 0xFF) * cell;
            int maxZ = z0 + (xz >>> 24 & 0xFF) * cell;
            int minY = Math.floorDiv(y & 65535, 16) - 2048;
            int maxY = Math.floorDiv((y >>> 16) + 15, 16) - 2048;
            int test = frustum.cubeInFrustum(new BoundingBox(minX - 1, minY - 1, minZ - 1, maxX, maxY, maxZ));
            if (test == -2 || test == -1) {
               mask |= 1 << sub;
            }
         }
      }

      return mask;
   }

   private static boolean facing(int face, Vec3 c, double x0, double z0, int size, int minY, int maxY) {
      return switch (face) {
         case 0 -> c.y < maxY + 32;
         case 1 -> c.y > minY - 64;
         case 2 -> c.z < z0 + size;
         case 3 -> c.z > z0;
         case 4 -> c.x < x0 + size;
         case 5 -> c.x > x0;
         default -> true;
      };
   }

   private int visibleQuads(LodRenderer.Mesh mesh) {
      int quads = 0;

      for (int part = 0; part <= 2; part++) {
         int n = this.ranges(mesh, part);

         for (int i = 0; i < n; i++) {
            quads += this.rangeQuads[i];
         }
      }

      return quads;
   }

   private void writeCompact(CommandEncoder encoder, GpuDevice device, Vec3 camera) {
      this.compactArenas.clear();
      this.compactRanges.clear();
      this.compactSplit = 0;
      if (batchSupported(device)) {
         int instances = 0;
         int bound = 0;

         for (LodRenderer.Mesh mesh : this.allDraws()) {
            if (mesh.materials != 0 && mesh.arena != null) {
               instances++;
               bound += 224;
            }
         }

         if (instances != 0) {
            if (this.compactInstances == null || this.compactInstanceCapacity < instances) {
               if (this.compactInstances != null) {
                  this.compactInstances.close();
               }

               this.compactInstanceCapacity = Math.max(256, Integer.highestOneBit(instances) << 1);
               this.compactInstances = device.createBuffer(() -> "Farlands compact tiles", 40, this.compactInstanceCapacity * 32L);
               this.compactInstanceData = ByteBuffer.allocateDirect(this.compactInstanceCapacity * 32).order(ByteOrder.nativeOrder());
            }

            if (this.compactCommands == null || this.compactCommandCapacity < bound) {
               if (this.compactCommands != null) {
                  this.compactCommands.close();
               }

               this.compactCommandCapacity = Math.max(4096, Integer.highestOneBit(bound) << 1);
               this.compactCommands = device.createBuffer(() -> "Farlands compact commands", 520, this.compactCommandCapacity * 20L);
               this.compactCommandData = ByteBuffer.allocateDirect(this.compactCommandCapacity * 20).order(ByteOrder.nativeOrder());
            }

            ByteBuffer inst = this.compactInstanceData;
            ByteBuffer cmd = this.compactCommandData;
            inst.clear();
            cmd.clear();
            int instance = 0;
            int commands = 0;

            for (int pass = 0; pass < 2; pass++) {
               List<LodRenderer.Mesh> list = pass == 0 ? this.draws : this.clipped;
               if (pass == 1) {
                  this.compactSplit = this.compactArenas.size();
               }

               for (LodRenderer.Arena arena : this.arenas) {
                  int start = commands;

                  for (LodRenderer.Mesh mesh : list) {
                     if (mesh.materials != 0 && mesh.arena == arena) {
                        int n = this.ranges(mesh, 0);
                        if (n != 0) {
                           int level = LodTile.keyLevel(mesh.key);
                           int size = 64 << level;
                           inst.putFloat((float)((double)LodTile.keyX(mesh.key) * size - camera.x));
                           inst.putFloat((float)camera.y);
                           inst.putFloat((float)((double)LodTile.keyZ(mesh.key) * size - camera.z));
                           inst.putFloat(mesh.quadMask + 16 * size);
                           inst.putInt(mesh.yBase);
                           inst.putInt((int)(mesh.allocation.getOffsetFromHeap() / 8L));
                           inst.putInt(0);
                           inst.putInt(0);

                           for (int i = 0; i < n; i++) {
                              cmd.putInt(this.rangeQuads[i] * 6).putInt(1).putInt(0).putInt(this.rangeBase[i]).putInt(instance);
                              commands++;
                           }

                           instance++;
                        }
                     }
                  }

                  if (commands > start) {
                     this.compactArenas.add(arena);
                     this.compactRanges.add(start);
                     this.compactRanges.add(commands - start);
                  }
               }
            }

            inst.flip();
            cmd.flip();
            if (inst.remaining() > 0) {
               encoder.writeToBuffer(this.compactInstances.slice(0L, inst.remaining()), inst);
            }

            if (cmd.remaining() > 0) {
               encoder.writeToBuffer(this.compactCommands.slice(0L, cmd.remaining()), cmd);
            }
         }
      }
   }

   private void drawCompact(RenderPass pass, Minecraft mc, AbstractTexture atlas, AutoStorageIndexBuffer indices) {
      if (!this.compactArenas.isEmpty() && this.compactInstances != null && this.compactCommands != null) {
         int max = RenderSystem.getDevice().getDeviceInfo().limits().maxDrawIndirectDrawCount();
         int bound = -1;

         for (int i = 0; i < this.compactArenas.size(); i++) {
            int clip = i >= this.compactSplit ? 1 : 0;
            if (bound != clip) {
               pass.setPipeline(RenderSystem.getCompiledPipeline(clip == 1 ? FarlandsPipelines.TERRAIN_COMPACT_CLIP : FarlandsPipelines.TERRAIN_COMPACT));
               RenderSystem.bindDefaultUniforms(pass);
               pass.setUniform("Sampler0", atlas.getTextureView(), this.atlasSampler);
               pass.setUniform("Sampler2", mc.gameRenderer.lightmap(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
               pass.setUniform("FarlandsInfo", this.infoRing.currentBuffer());
               pass.setUniform("FarlandsSprites", this.spriteBuffer);
               if (clip == 1) {
                  pass.setUniform("FarlandsMask", this.maskBuffer);
               }

               pass.setVertexBuffer(0, this.compactInstances.slice());
               pass.setIndexBuffer(indices.getBuffer(), indices.type());
               bound = clip;
            }

            pass.setUniform("FarlandsCompact", this.compactArenas.get(i).buffer);
            int start = this.compactRanges.getInt(i * 2);
            int left = this.compactRanges.getInt(i * 2 + 1);

            while (left > 0) {
               int n = Math.min(left, max);
               pass.drawIndexedIndirect(this.compactCommands.slice(start * 20L, n * 20L), n);
               start += n;
               left -= n;
            }
         }
      }
   }

   private void drawList(RenderPass pass, List<LodRenderer.Mesh> list, int part) {
      DeviceFeatures features = RenderSystem.getDevice().getDeviceInfo().features();

      for (LodRenderer.Arena arena : this.arenas) {
         int count = 0;

         for (LodRenderer.Mesh mesh : list) {
            if (mesh.arena == arena && mesh.materials == 0) {
               count += this.ranges(mesh, part);
            }
         }

         if (count != 0) {
            pass.setUniform("FarlandsQuads", arena.buffer);
            if (features.multiDrawDirectSeparate()) {
               this.ensureDrawBuffers(count);
               this.drawOffsets.clear();
               this.drawCounts.clear();
               this.drawBases.clear();

               for (LodRenderer.Mesh mesh : list) {
                  if (mesh.arena == arena && mesh.materials <= 0) {
                     int n = this.ranges(mesh, part);

                     for (int i = 0; i < n; i++) {
                        this.drawOffsets.put(0L);
                        this.drawCounts.put(this.rangeQuads[i] * 6);
                        this.drawBases.put(this.rangeBase[i]);
                     }
                  }
               }

               this.drawOffsets.flip();
               this.drawCounts.flip();
               this.drawBases.flip();
               pass.multiDrawIndexed(this.drawOffsets, this.drawCounts, this.drawBases, count);
            } else {
               for (LodRenderer.Mesh mesh : list) {
                  if (mesh.arena == arena && mesh.materials <= 0) {
                     int n = this.ranges(mesh, part);

                     for (int i = 0; i < n; i++) {
                        pass.drawIndexed(this.rangeQuads[i] * 6, 1, 0, this.rangeBase[i], 0);
                     }
                  }
               }
            }
         }
      }
   }

   private void ensureDrawBuffers(int count) {
      if (this.drawCounts.capacity() < count) {
         MemoryUtil.memFree(this.drawOffsets);
         MemoryUtil.memFree(this.drawCounts);
         MemoryUtil.memFree(this.drawBases);
         int size = Integer.highestOneBit(count) * 2;
         this.drawOffsets = MemoryUtil.memAllocPointer(size);
         this.drawCounts = MemoryUtil.memAllocInt(size);
         this.drawBases = MemoryUtil.memAllocInt(size);
      }
   }

   private void evict() {
      List<LodRenderer.Mesh> victims = new ArrayList<>();
      boolean pressure = this.freeSlots.size() < 512 || this.arenas.size() >= 63 || this.reclaim;
      long pressureIdle = this.reclaim ? 120L : 600L;
      double reach = (Minecraft.getInstance().options.getEffectiveRenderDistance() + FarlandsSettings.lodChunks) * 16.0 * 1.3;
      ObjectIterator var7 = this.meshes.values().iterator();

      while (var7.hasNext()) {
         LodRenderer.Mesh mesh = (LodRenderer.Mesh)var7.next();
         long idle = this.frame - mesh.lastFrame;
         if (idle >= 120L) {
            int size = 64 << LodTile.keyLevel(mesh.key);
            double cx = (LodTile.keyX(mesh.key) + 0.5) * size - this.camera.x;
            double cz = (LodTile.keyZ(mesh.key) + 0.5) * size - this.camera.z;
            double distance = Math.sqrt(cx * cx + cz * cz) - size * 0.71;
            if (distance > reach || idle > 6000L || pressure && idle > pressureIdle) {
               victims.add(mesh);
            }
         }
      }

      for (LodRenderer.Mesh mesh : victims) {
         if (!this.meshing.contains(mesh.key)) {
            this.meshes.remove(mesh.key);
            if (mesh.allocation != null && mesh.arena != null) {
               this.pendingFrees.add(new LodRenderer.PendingFree(this.frame, mesh.arena, mesh.allocation));
            }

            this.releaseShaderBuffers(mesh);
            this.releaseWater(mesh);
            this.bySlot[mesh.slot] = null;
            this.freeSlots.push(mesh.slot);
         }
      }

      this.trimArenas();
   }

   private void trimArenas() {
      boolean spare = false;
      int i = 0;

      while (i < this.arenas.size()) {
         LodRenderer.Arena arena = this.arenas.get(i);
         if (arena.allocator.isCompletelyFree() && spare) {
            arena.buffer.close();
            this.arenas.remove(i);
         } else {
            spare |= arena.allocator.isCompletelyFree();
            i++;
         }
      }

      boolean waterSpare = false;
      int w = 0;

      while (w < this.waterArenas.size()) {
         LodRenderer.WaterArena arena = this.waterArenas.get(w);
         if (arena.allocator.isCompletelyFree() && waterSpare) {
            arena.buffer.close();
            this.waterArenas.remove(w);
         } else {
            waterSpare |= arena.allocator.isCompletelyFree();
            w++;
         }
      }
   }

   public void releaseGpu() {
      this.close();
      this.uploadedSpriteVersion = -1;
      this.uploadedSprites = 0;
      this.maskDirty = true;
   }

   public void close() {
      this.clearMeshes();
      this.occlusion.close();
      if (this.waterSections != null) {
         this.waterSections.close();
      }

      this.waterSections = null;
      this.waterSectionData = null;
      this.waterSectionCapacity = 0;
      this.releaseFrees(true);

      for (LodRenderer.Arena arena : this.arenas) {
         arena.buffer.close();
      }

      this.arenas.clear();

      for (LodRenderer.WaterArena arena : this.waterArenas) {
         arena.buffer.close();
      }

      this.waterArenas.clear();
      if (this.waterInstances != null) {
         this.waterInstances.close();
      }

      if (this.waterCommands != null) {
         this.waterCommands.close();
      }

      this.waterInstances = null;
      this.waterCommands = null;
      if (this.compactInstances != null) {
         this.compactInstances.close();
      }

      if (this.compactCommands != null) {
         this.compactCommands.close();
      }

      this.compactInstances = null;
      this.compactCommands = null;
      this.compactInstanceCapacity = 0;
      this.compactCommandCapacity = 0;
      this.waterInstanceCapacity = 0;
      this.waterCommandCapacity = 0;
      if (this.tileRing != null) {
         this.tileRing.close();
      }

      if (this.infoRing != null) {
         this.infoRing.close();
      }

      if (this.spriteBuffer != null) {
         this.spriteBuffer.close();
      }

      if (this.waterSpriteBuffer != null) {
         this.waterSpriteBuffer.close();
      }

      this.waterSpriteBuffer = null;
      if (this.maskBuffer != null) {
         this.maskBuffer.close();
      }

      if (this.atlasSampler != null) {
         this.atlasSampler.close();
      }

      this.tileRing = null;
      this.infoRing = null;
      this.spriteBuffer = null;
      this.maskBuffer = null;
      this.atlasSampler = null;
   }

   private static final class Arena {
      final GpuBuffer buffer;
      final TlsfAllocator allocator;

      Arena(GpuDevice device, int index) {
         this.buffer = device.createBuffer(() -> "Farlands terrain " + index, 264, 33554432L);
         this.allocator = new TlsfAllocator(new Heap(33554432L));
      }
   }

   private record Built(long key, int minY, int maxY, int surfaceY, int slot, int generation, LodMesher.Mesh mesh) {
   }

   private static final class Mesh {
      final long key;
      final int slot;
      int minY;
      int maxY;
      boolean dirty;
      LodRenderer.@Nullable Arena arena;
      @Nullable Allocation allocation;
      int quads;
      int fluidQuads;
      int surfaceQuads;
      int[] buckets = new int[7];
      int materials;
      int yBase;
      long bytes;
      int[] groups = new int[224];
      int[] subBounds = new int[64];
      int subMask = -1;
      int[] occluders = new int[0];
      int version;
      @Nullable GpuBuffer water;
      LodRenderer.@Nullable WaterArena waterArena;
      @Nullable Allocation waterAllocation;
      int waterBase;
      int waterQuads;
      int[] waterPatches = new int[0];
      long waterOffset;
      boolean waterClip;
      int vbo;
      int vao;
      LodMesher.@Nullable Format format;
      long lastFrame;
      long retryFrame;
      boolean ready;
      boolean preview;
      int quadMask = 15;

      Mesh(long key, int slot) {
         this.key = key;
         this.slot = slot;
      }
   }

   private record PendingFree(long frame, LodRenderer.Arena arena, Allocation allocation) {
   }

   private record PendingWaterFree(long frame, LodRenderer.WaterArena arena, Allocation allocation) {
   }

   private static final class WaterArena {
      final GpuBuffer buffer;
      final TlsfAllocator allocator;

      WaterArena(GpuDevice device, int index) {
         this.buffer = device.createBuffer(() -> "Farlands water " + index, 264, 16777216L);
         this.allocator = new TlsfAllocator(new Heap(16777216L));
      }
   }
}
