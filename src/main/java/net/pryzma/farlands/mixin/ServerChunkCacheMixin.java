package net.pryzma.farlands.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.pryzma.farlands.work.WorkerPool;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = ServerChunkCache.class, priority = 2000)
public abstract class ServerChunkCacheMixin {
   @WrapMethod(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;", require = 0)
   private ChunkAccess prFarlands$standIn(int x, int z, ChunkStatus status, boolean load, Operation<ChunkAccess> original) {
      if (!WorkerPool.onWorker()) {
         return (ChunkAccess)original.call(new Object[]{x, z, status, load});
      } else {
         Level owner = ((ServerChunkCache)(Object)this).getLevel();
         if (owner == null) {
            return (ChunkAccess)original.call(new Object[]{x, z, status, load});
         } else {
            return load ? new EmptyLevelChunk(owner, new ChunkPos(x, z), owner.palettedContainerFactory().defaultBiome()) : null;
         }
      }
   }
}
