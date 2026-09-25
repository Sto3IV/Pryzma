package net.pryzma.item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.pryzma.core.res.PrPaths;
import net.pryzma.entity.model.PrCemContext;

/**
 * Custom Item Textures at render time: the entry points the item, armor and elytra hooks call.
 *
 * <p>Matching a stack against its rules (names, components, enchantments) is cached per stack
 * and redone only when the stack's components, count or hand change, so an inventory full of
 * renamed items costs one comparison per item per frame. All state is render-thread only.
 */
public final class PrCit {
    /** The vanilla override resolution, handed in by the item model hook. */
    @FunctionalInterface
    public interface Resolver {
        BakedModel resolve(ItemOverrides overrides, BakedModel model);
    }

    /** What an item or armor render replaced, restored when it ends. */
    public record Scope(ItemStack glintStack, ItemDisplayContext itemContext) {
    }

    private static final class Cache {
        final PrCitIndex index;
        final DataComponentPatch patch;
        final int count;
        final boolean offHand;
        final PrCitStack view;
        final PrCitEntry[] entries = new PrCitEntry[PrCitRule.Type.values().length];
        int done;
        PrCitGlint.Effects effects;

        Cache(PrCitIndex index, ItemStack stack, DataComponentPatch patch, boolean offHand) {
            this.index = index;
            this.patch = patch;
            this.count = stack.getCount();
            this.offHand = offHand;
            this.view = new PrCitStack(stack, offHand);
        }

        boolean valid(PrCitIndex index, ItemStack stack, DataComponentPatch patch, boolean offHand) {
            return this.index == index && count == stack.getCount() && this.offHand == offHand && this.patch.equals(patch);
        }
    }

    private static final Map<ItemStack, Cache> CACHE = new WeakHashMap<>();
    private static PrCitIndex index = PrCitIndex.EMPTY;
    private static boolean offHand;

    private PrCit() {
    }

    static void publish(PrCitIndex next) {
        PrCitGlint.release();
        CACHE.clear();
        index = next;
    }

    static PrCitGlobal global() {
        return index.global;
    }

    /** Number of active rules, for the debug overlay. */
    public static int ruleCount() {
        return index.rules;
    }

    // ------------------------------------------------------------------ items

    /** Whether any item rule names the stack's item; a cheap test before {@link #resolve}. */
    public static boolean hasRules(ItemStack stack) {
        return !stack.isEmpty() && index.items.containsKey(stack.getItem());
    }

    /**
     * The model to draw for {@code stack}, around vanilla's override resolution. A sub-model named
     * after the resolved override ({@code model.shield_blocking}) wins; otherwise a replacement
     * model resolves its own overrides; otherwise a texture rule re-skins the resolved model.
     */
    public static BakedModel resolve(ItemStack stack, ItemOverrides overrides, BakedModel model, Resolver vanilla) {
        PrCitEntry entry = find(stack, PrCitRule.Type.ITEM);
        BakedModel resolved = vanilla.resolve(overrides, model);
        if (entry == null || resolved == null) {
            return resolved;
        }
        ResourceLocation location = PrCitOverrides.location(resolved, model);
        BakedModel sub = entry.subModel(location);
        if (sub != null) {
            return sub;
        }
        if (entry.model != null) {
            return vanilla.resolve(entry.model.getOverrides(), entry.model);
        }
        return entry.textured(resolved, location);
    }

    /** Called around every item drawn by the hand renderer: which hand rules with {@code hand=} see. */
    public static boolean setOffHand(boolean value) {
        boolean previous = offHand;
        offHand = value;
        return previous;
    }

    /** Opens the render of an item: its enchantment layers and the CEM item context. */
    public static Scope beginItem(ItemStack stack, ItemDisplayContext context) {
        Scope saved = new Scope(PrCitGlint.stack, PrCemContext.itemContext());
        PrCitGlint.stack = stack;
        PrCemContext.setItemContext(context);
        return saved;
    }

    /** Opens the render of a worn item (armor, elytra): its enchantment layers. */
    public static Scope beginWorn(ItemStack stack) {
        Scope saved = new Scope(PrCitGlint.stack, PrCemContext.itemContext());
        PrCitGlint.stack = stack;
        return saved;
    }

    public static void end(Scope saved) {
        PrCitGlint.stack = saved.glintStack();
        PrCemContext.setItemContext(saved.itemContext());
    }

    // ------------------------------------------------------------------ armor and elytra

    /**
     * OptiFine {@code getCustomArmorTexture}: {@code texture.<name>} where {@code <name>} is the
     * vanilla layer's file name ({@code diamond_layer_1}, {@code leather_layer_2_overlay}), else
     * {@code texture}.
     */
    public static ResourceLocation armorTexture(ItemStack stack, ResourceLocation original) {
        PrCitEntry entry = find(stack, PrCitRule.Type.ARMOR);
        if (entry == null) {
            return original;
        }
        ResourceLocation texture = entry.plainTextures.get(PrPaths.baseName(original.getPath()));
        if (texture == null) {
            texture = entry.plainTexture;
        }
        return texture != null ? texture : original;
    }

    public static ResourceLocation elytraTexture(ItemStack stack, ResourceLocation original) {
        PrCitEntry entry = find(stack, PrCitRule.Type.ELYTRA);
        return entry == null || entry.plainTexture == null ? original : entry.plainTexture;
    }

    // ------------------------------------------------------------------ matching

    static PrCitEntry find(ItemStack stack, PrCitRule.Type type) {
        if (stack.isEmpty()) {
            return null;
        }
        PrCitIndex current = index;
        PrCitEntry[] entries = current.items.get(stack.getItem());
        if (entries == null) {
            return null;
        }
        Cache cache = cache(current, stack);
        if (cache == null) {
            return match(entries, type, new PrCitStack(stack, offHand));
        }
        int bit = 1 << type.ordinal();
        if ((cache.done & bit) == 0) {
            cache.entries[type.ordinal()] = match(entries, type, cache.view);
            cache.done |= bit;
        }
        return cache.entries[type.ordinal()];
    }

    private static PrCitEntry match(PrCitEntry[] entries, PrCitRule.Type type, PrCitItem view) {
        for (PrCitEntry entry : entries) {
            if (entry.rule.type == type && entry.rule.matches(view)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * The enchantment layers of {@code stack}: {@code null} to leave the default glint alone,
     * {@link PrCitGlint.Effects#HIDE} to draw none.
     */
    static PrCitGlint.Effects effects(ItemStack stack) {
        PrCitIndex current = index;
        if (current.enchantments.isEmpty() && current.global.useGlint() || stack.isEmpty()) {
            return null;
        }
        Cache cache = cache(current, stack);
        PrCitGlint.Effects effects = cache != null && cache.effects != null ? cache.effects
                : selectEffects(current, cache != null ? cache.view : new PrCitStack(stack, offHand));
        if (cache != null) {
            cache.effects = effects;
        }
        if (effects.isEmpty()) {
            return current.global.useGlint() ? null : PrCitGlint.Effects.HIDE;
        }
        return effects;
    }

    /**
     * Enchantment rules matching the item: one per layer, the first in OptiFine order (highest
     * weight), at most {@code cap} of them keeping the highest weights, drawn by layer.
     */
    static PrCitGlint.Effects selectEffects(PrCitIndex current, PrCitItem view) {
        int n = view.enchantments();
        if (n == 0) {
            return PrCitGlint.Effects.NONE;
        }
        Map<PrCitEntry, Integer> levels = new IdentityHashMap<>();
        ResourceLocation item = view.item();
        for (int i = 0; i < n; i++) {
            PrCitEntry[] entries = current.enchantments.get(view.enchantment(i));
            if (entries == null) {
                continue;
            }
            for (PrCitEntry entry : entries) {
                if (entry.rule.appliesTo(item) && entry.rule.matches(view)) {
                    levels.merge(entry, view.enchantmentLevel(i), Math::max);
                }
            }
        }
        if (levels.isEmpty()) {
            return PrCitGlint.Effects.NONE;
        }
        Comparator<PrCitEntry> order = (a, b) -> PrCitRule.ORDER.compare(a.rule, b.rule);
        List<PrCitEntry> sorted = new ArrayList<>(levels.keySet());
        sorted.sort(order);
        List<PrCitEntry> chosen = new ArrayList<>();
        Set<Integer> layers = new HashSet<>();
        for (PrCitEntry entry : sorted) {
            if (layers.add(entry.rule.layer)) {
                chosen.add(entry);
            }
        }
        int cap = current.global.cap();
        if (chosen.size() > cap) {
            chosen.sort((a, b) -> Integer.compare(b.rule.weight, a.rule.weight));
            chosen = new ArrayList<>(chosen.subList(0, cap));
            chosen.sort(order);
        }
        PrCitEntry[] entries = chosen.toArray(new PrCitEntry[0]);
        int[] entryLevels = new int[entries.length];
        boolean vanilla = false;
        for (int i = 0; i < entries.length; i++) {
            entryLevels[i] = levels.get(entries[i]);
            vanilla |= entries[i].rule.useGlint;
        }
        return new PrCitGlint.Effects(entries, entryLevels, vanilla);
    }

    private static Cache cache(PrCitIndex current, ItemStack stack) {
        if (!RenderSystem.isOnRenderThread()) {
            return null;
        }
        DataComponentPatch patch = stack.getComponentsPatch();
        Cache cache = CACHE.get(stack);
        if (cache == null || !cache.valid(current, stack, patch, offHand)) {
            cache = new Cache(current, stack, patch, offHand);
            CACHE.put(stack, cache);
        }
        return cache;
    }
}
