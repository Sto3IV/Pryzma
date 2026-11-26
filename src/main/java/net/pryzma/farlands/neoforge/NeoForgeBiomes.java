package net.pryzma.farlands.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.Tag;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.Biome.ClimateSettings;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo.BiomeInfo;
import net.neoforged.neoforge.registries.NeoForgeRegistries.Keys;

final class NeoForgeBiomes {
   private static final Codec<BiomeInfo> INFO = RecordCodecBuilder.create(
      i -> i.group(
            ClimateSettings.CODEC.forGetter(BiomeInfo::climateSettings),
            EnvironmentAttributeMap.CODEC_ONLY_POSITIONAL.optionalFieldOf("attributes", EnvironmentAttributeMap.EMPTY).forGetter(BiomeInfo::attributes),
            BiomeSpecialEffects.CODEC.fieldOf("effects").forGetter(BiomeInfo::effects),
            BiomeGenerationSettings.CODEC.forGetter(BiomeInfo::generationSettings)
         )
         .apply(i, BiomeInfo::new)
   );

   private NeoForgeBiomes() {
   }

   static DataResult<Tag> encode(Biome biome, DynamicOps<Tag> ops) {
      return INFO.encodeStart(ops, biome.modifiableBiomeInfo().get());
   }

   static void applyModifiers(RegistryAccess access) {
      List<BiomeModifier> modifiers = access.lookup(Keys.BIOME_MODIFIERS)
         .map(registry -> registry.listElements().<BiomeModifier>map(Holder::value).toList())
         .orElse(List.of());
      if (!modifiers.isEmpty()) {
         access.lookupOrThrow(Registries.BIOME)
            .listElements()
            .forEach(holder -> ((Biome)holder.value()).modifiableBiomeInfo().applyBiomeModifiers(holder, modifiers, access));
      }
   }
}
