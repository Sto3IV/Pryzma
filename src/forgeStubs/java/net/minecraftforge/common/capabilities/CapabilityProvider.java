package net.minecraftforge.common.capabilities;

import net.neoforged.neoforge.attachment.AttachmentHolder;

/**
 * Stub for Forge's removed capability base. Pryzma's patched
 * {@code BlockEntity} still extends this type; NeoForge 21.1's BlockEntity
 * extends {@link AttachmentHolder}. Bridge the two so AttachmentSync can
 * treat Pryzma BlockEntities as attachment holders.
 */
public abstract class CapabilityProvider<B> extends AttachmentHolder {
    protected CapabilityProvider(Class<B> baseClass) {
    }

    public void gatherCapabilities() {
    }

    public CapabilityDispatcher getCapabilities() {
        return null;
    }

    public net.minecraft.nbt.CompoundTag serializeCaps(net.minecraft.core.HolderLookup.Provider registries) {
        return serializeAttachments(registries);
    }

    public void deserializeCaps(net.minecraft.core.HolderLookup.Provider registries, net.minecraft.nbt.CompoundTag tag) {
        deserializeAttachments(registries, tag);
    }

    public void invalidateCaps() {
    }

    public void reviveCaps() {
    }
}
