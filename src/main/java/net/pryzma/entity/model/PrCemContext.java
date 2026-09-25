package net.pryzma.entity.model;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * What CEM animations read about the render in progress: the entity or block entity, the
 * arguments its renderer passed to {@code setupAnim}, and a serial number that changes with every
 * entity render so a model runs its animations once per render.
 */
public final class PrCemContext {
    private static int serial;
    static Entity entity;
    static EntityRenderer<?> renderer;
    static BlockEntity blockEntity;
    static float limbSwing;
    static float limbSpeed;
    static float age;
    static float headYaw;
    static float headPitch;
    static float partialTick;
    static int ruleIndex;
    /** How an item model is being drawn ({@code is_in_hand}, {@code is_in_gui} ...), or {@code null}. */
    static ItemDisplayContext itemContext;
    private static long lastFrameNanos;
    static float frameSeconds;
    static int frames;

    private PrCemContext() {
    }

    public record Scope(Entity entity, EntityRenderer<?> renderer, BlockEntity blockEntity, float limbSwing, float limbSpeed,
            float age, float headYaw, float headPitch, float partialTick, int ruleIndex) {
    }

    public static Scope begin(Entity e, EntityRenderer<?> r, float partial) {
        Scope saved = save();
        serial++;
        entity = e;
        renderer = r;
        blockEntity = null;
        partialTick = partial;
        limbSwing = 0.0F;
        limbSpeed = 0.0F;
        age = e.tickCount + partial;
        headYaw = 0.0F;
        headPitch = 0.0F;
        return saved;
    }

    public static Scope begin(BlockEntity be, float partial) {
        Scope saved = save();
        serial++;
        entity = null;
        renderer = null;
        blockEntity = be;
        partialTick = partial;
        return saved;
    }

    private static Scope save() {
        return new Scope(entity, renderer, blockEntity, limbSwing, limbSpeed, age, headYaw, headPitch, partialTick, ruleIndex);
    }

    public static void end(Scope s) {
        entity = s.entity();
        renderer = s.renderer();
        blockEntity = s.blockEntity();
        limbSwing = s.limbSwing();
        limbSpeed = s.limbSpeed();
        age = s.age();
        headYaw = s.headYaw();
        headPitch = s.headPitch();
        partialTick = s.partialTick();
        ruleIndex = s.ruleIndex();
    }

    /** The arguments a living entity renderer passes to {@code setupAnim}. */
    public static void onSetupAnim(float swing, float speed, float ageInTicks, float netHeadYaw, float pitch) {
        limbSwing = swing;
        limbSpeed = speed;
        age = ageInTicks;
        headYaw = netHeadYaw;
        headPitch = pitch;
    }

    /** A second render of the same entity (the emissive pass): animations run again after its setupAnim. */
    public static void nextPass() {
        serial++;
    }

    public static void setRuleIndex(int index) {
        ruleIndex = index;
    }

    public static void setItemContext(ItemDisplayContext context) {
        itemContext = context;
    }

    public static ItemDisplayContext itemContext() {
        return itemContext;
    }

    static int serial() {
        return serial;
    }

    static boolean active() {
        return entity != null || blockEntity != null;
    }

    /** Once per rendered frame: {@code frame_time} and {@code frame_counter}. */
    public static void onFrame() {
        long now = System.nanoTime();
        frameSeconds = lastFrameNanos == 0 ? 0.0F : (now - lastFrameNanos) / 1.0E9F;
        lastFrameNanos = now;
        frames = (frames + 1) % 720720;
        // Renders outside any entity (item models in menus) still advance their animations per frame.
        serial++;
    }
}
