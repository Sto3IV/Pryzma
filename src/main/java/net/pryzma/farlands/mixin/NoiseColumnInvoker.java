package net.pryzma.farlands.mixin;

import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(NoiseBasedChunkGenerator.class)
public interface NoiseColumnInvoker {
   @Invoker("createNoiseChunk")
   NoiseChunk prFarlands$createNoiseChunk(ChunkAccess var1, StructureManager var2, Blender var3, RandomState var4, NoiseSettings var5);

   @Invoker("doFill")
   void prFarlands$doFill(NoiseChunk var1, ChunkAccess var2);
}
