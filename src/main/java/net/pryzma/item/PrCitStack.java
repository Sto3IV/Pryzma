package net.pryzma.item;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * An {@code ItemStack} as CIT rules read it. Components are encoded to NBT on demand, one
 * component at a time, and kept for the life of the view (one rule lookup).
 */
final class PrCitStack implements PrCitItem {
    private static final ResourceLocation[] NO_IDS = new ResourceLocation[0];

    private final ItemStack stack;
    private final boolean offHand;
    private ResourceLocation[] enchantmentIds;
    private int[] enchantmentLevels;
    private Map<String, Tag> components;

    PrCitStack(ItemStack stack, boolean offHand) {
        this.stack = stack;
        this.offHand = offHand;
    }

    @Override
    public ResourceLocation item() {
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    @Override
    public int damage() {
        if (stack.getItem() instanceof PotionItem) {
            PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
            String potion = contents == null ? null
                    : contents.potion().flatMap(Holder::unwrapKey).map(key -> key.location().toString()).orElse(null);
            return PrCitPotions.damage(potion, stack.is(Items.SPLASH_POTION));
        }
        return stack.getDamageValue();
    }

    @Override
    public int maxDamage() {
        return stack.getMaxDamage();
    }

    @Override
    public int count() {
        return stack.getCount();
    }

    @Override
    public int enchantments() {
        readEnchantments();
        return enchantmentIds.length;
    }

    @Override
    public ResourceLocation enchantment(int index) {
        readEnchantments();
        return enchantmentIds[index];
    }

    @Override
    public int enchantmentLevel(int index) {
        readEnchantments();
        return enchantmentLevels[index];
    }

    /** Enchantments, or the stored ones of an enchanted book, as OptiFine reads them. */
    private void readEnchantments() {
        if (enchantmentIds != null) {
            return;
        }
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (enchantments.isEmpty()) {
            enchantments = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        }
        if (enchantments.isEmpty()) {
            enchantmentIds = NO_IDS;
            enchantmentLevels = new int[0];
            return;
        }
        ResourceLocation[] ids = new ResourceLocation[enchantments.size()];
        int[] levels = new int[ids.length];
        int n = 0;
        for (Object2IntMap.Entry<Holder<Enchantment>> e : enchantments.entrySet()) {
            ResourceLocation id = e.getKey().unwrapKey().map(ResourceKey::location).orElse(null);
            if (id != null) {
                ids[n] = id;
                levels[n++] = e.getIntValue();
            }
        }
        enchantmentIds = n == ids.length ? ids : Arrays.copyOf(ids, n);
        enchantmentLevels = n == levels.length ? levels : Arrays.copyOf(levels, n);
    }

    @Override
    public Tag components(String id) {
        if (components == null) {
            components = new HashMap<>();
        }
        Tag cached = components.get(id);
        if (cached != null) {
            return cached;
        }
        CompoundTag root = new CompoundTag();
        DynamicOps<Tag> ops = ops();
        if (id.equals("*")) {
            for (TypedDataComponent<?> component : stack.getComponents()) {
                encode(component.type(), component.value(), ops, root);
            }
        } else {
            ResourceLocation location = ResourceLocation.tryParse(id);
            DataComponentType<?> type = location == null ? null : BuiltInRegistries.DATA_COMPONENT_TYPE.get(location);
            Object value = type == null ? null : stack.get(type);
            if (value != null) {
                encode(type, value, ops, root);
            }
        }
        components.put(id, root);
        return root;
    }

    @Override
    public boolean offHand() {
        return offHand;
    }

    @SuppressWarnings("unchecked")
    private static <T> void encode(DataComponentType<T> type, Object value, DynamicOps<Tag> ops, CompoundTag root) {
        Codec<T> codec = type.codec();
        ResourceLocation key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
        if (codec == null || key == null) {
            return;
        }
        codec.encodeStart(ops, (T) value).result().ifPresent(tag -> root.put(key.toString(), tag));
    }

    /** Registry-aware NBT ops when a world is loaded: components that hold registry entries need them. */
    private static DynamicOps<Tag> ops() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? NbtOps.INSTANCE : minecraft.level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
    }
}
