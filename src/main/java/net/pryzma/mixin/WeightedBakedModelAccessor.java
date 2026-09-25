package net.pryzma.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.WeightedBakedModel;
import net.minecraft.util.random.WeightedEntry;

/** The variants of a weighted block model, so CTM sees every sprite a random variant can show. */
@Mixin(WeightedBakedModel.class)
public interface WeightedBakedModelAccessor {
    @Accessor("list")
    List<WeightedEntry.Wrapper<BakedModel>> prGetList();
}
