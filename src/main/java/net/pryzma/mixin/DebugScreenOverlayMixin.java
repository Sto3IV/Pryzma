package net.pryzma.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.pryzma.gui.PrDebugOverlay;
import net.pryzma.render.PrF3RenderCache;

/**
 * The Pryzma lines of the F3 screen ({@link PrDebugOverlay}) and its text cache ({@link PrF3RenderCache}).
 *
 * <p>NeoForge's {@code render} raycasts twice, then its {@code drawManaged} lambda collects both lists,
 * posts {@code CustomizeGuiOverlayEvent.DebugText}, draws each column with {@code renderLines} and
 * draws the charts. A replayed frame skips everything before the charts. {@code drawGameInformation}
 * and {@code drawSystemInformation} are not on this path. {@code DebugScreenOverlayMixinTargetsTest}
 * pins the lambda and every call wrapped here.
 */
@Mixin(DebugScreenOverlay.class)
abstract class DebugScreenOverlayMixin {
    @Shadow
    private HitResult block;

    @Shadow
    private HitResult liquid;

    /** One decision per frame, before the raycasts: replay the text or build it. */
    @Inject(method = "render", at = @At("HEAD"))
    private void prBeginFrame(GuiGraphics graphics, CallbackInfo ci) {
        PrF3RenderCache.beginFrame(System.nanoTime(), graphics.guiWidth(), graphics.guiHeight(), graphics.pose().last().pose());
    }

    /** The raycasts feed only the targeted block and fluid lines; a replayed frame keeps the last hits. */
    @WrapOperation(method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;pick(DFZ)Lnet/minecraft/world/phys/HitResult;"))
    private HitResult prPick(Entity entity, double distance, float partialTick, boolean fluids, Operation<HitResult> original) {
        if (PrF3RenderCache.replaying()) {
            return fluids ? liquid : block;
        }
        return original.call(entity, distance, partialTick, fluids);
    }

    /** The left list: chunk and light lookups, the noise router, the spawn state. */
    @WrapOperation(method = "lambda$render$2",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/DebugScreenOverlay;collectGameInformationText()Ljava/util/List;"))
    private List<String> prCollectGame(DebugScreenOverlay overlay, Operation<List<String>> original) {
        return PrF3RenderCache.replaying() ? PrF3RenderCache.noLines() : original.call(overlay);
    }

    /** The right list: memory figures, the GPU strings, the targeted block's properties and tags. */
    @WrapOperation(method = "lambda$render$2",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/DebugScreenOverlay;collectSystemInformationText()Ljava/util/List;"))
    private List<String> prCollectSystem(DebugScreenOverlay overlay, Operation<List<String>> original) {
        return PrF3RenderCache.replaying() ? PrF3RenderCache.noLines() : original.call(overlay);
    }

    /** DebugText listeners edit the lists of build frames; a replayed frame has none to give them. */
    @WrapOperation(method = "lambda$render$2",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/bus/api/IEventBus;post(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;"))
    private Event prPostDebugText(IEventBus bus, Event event, Operation<Event> original) {
        if (PrF3RenderCache.replaying()) {
            return event;
        }
        PrF3RenderCache.eventHookRan();
        return original.call(bus, event);
    }

    /** Each column: replayed from its vertex buffers, or built through vanilla renderLines. */
    @WrapOperation(method = "lambda$render$2",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/DebugScreenOverlay;renderLines(Lnet/minecraft/client/gui/GuiGraphics;Ljava/util/List;Z)V"))
    private void prRenderLines(DebugScreenOverlay overlay, GuiGraphics graphics, List<String> lines, boolean left, Operation<Void> original) {
        if (PrF3RenderCache.replaying()) {
            PrF3RenderCache.replay(graphics, left);
        } else if (PrF3RenderCache.enabled) {
            PrF3RenderCache.build(graphics, left, g -> original.call(overlay, g, lines, left));
        } else {
            original.call(overlay, graphics, lines, left);
        }
    }

    /** The chart line changes with every toggle, and a reopened F3 shows fresh text. */
    @Inject(method = {"toggleOverlay", "toggleNetworkCharts", "toggleFpsCharts", "toggleProfilerChart", "reset"}, at = @At("TAIL"))
    private void prInvalidate(CallbackInfo ci) {
        PrF3RenderCache.invalidate();
    }

    @Inject(method = "getGameInformation()Ljava/util/List;", at = @At("RETURN"))
    private void prGameInformation(CallbackInfoReturnable<List<String>> cir) {
        PrDebugOverlay.left(cir.getReturnValue());
    }

    @Inject(method = "getSystemInformation()Ljava/util/List;", at = @At("RETURN"))
    private void prSystemInformation(CallbackInfoReturnable<List<String>> cir) {
        PrDebugOverlay.right(cir.getReturnValue());
    }
}
