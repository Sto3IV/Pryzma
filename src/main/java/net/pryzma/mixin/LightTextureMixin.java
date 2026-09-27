package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.pryzma.iris.uniforms.CapturedRenderingState;
import net.pryzma.lightmap.PrGuiLightmap;
import net.pryzma.lightmap.PryzmaLightmap;

/**
 * Custom lightmaps. When the dimension has one, the table is rebuilt every frame from interpolated
 * inputs (vanilla rebuilds once per tick) and vanilla's computation is skipped.
 */
@Mixin(LightTexture.class)
public abstract class LightTextureMixin {
    @Shadow @Final private NativeImage lightPixels;
    @Shadow @Final private DynamicTexture lightTexture;
    @Shadow @Final private Minecraft minecraft;
    @Shadow private float blockLightRedFlicker;
    @Shadow private boolean updateLightTexture;

    /** Vanilla's own table reached the texture: the GUI can sample it again. */
    @Inject(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/DynamicTexture;upload()V"))
    private void prVanillaLightmapUploaded(float partialTicks, CallbackInfo ci) {
        PrGuiLightmap.onLevelLightmap(false);
    }

    /** The GUI binds a white lightmap while the texture holds a custom one; see {@link PrGuiLightmap}. */
    @ModifyArg(method = "turnOnLightLayer", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/resources/ResourceLocation;)V"), index = 1)
    private ResourceLocation prGuiLightmapShader(ResourceLocation location) {
        return PrGuiLightmap.substitute(location);
    }

    @ModifyArg(method = "turnOnLightLayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureManager;bindForSetup(Lnet/minecraft/resources/ResourceLocation;)V"), index = 0)
    private ResourceLocation prGuiLightmapBind(ResourceLocation location) {
        return PrGuiLightmap.substitute(location);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void prTickFlicker(CallbackInfo ci) {
        PryzmaLightmap.onFlickerTick(this.blockLightRedFlicker);
    }

    @Inject(method = "updateLightTexture", at = @At("HEAD"), cancellable = true)
    private void prUpdateLightTexture(float partialTicks, CallbackInfo ci) {
        ClientLevel level = this.minecraft.level;
        LocalPlayer player = this.minecraft.player;
        if (level == null || player == null || !PryzmaLightmap.isActive(level)) {
            return;
        }
        float effectScale = this.minecraft.options.darknessEffectScale().get().floatValue();
        MobEffectInstance darkness = player.getEffect(MobEffects.DARKNESS);
        float darkGamma = (darkness == null ? 0.0F : darkness.getBlendFactor(player, partialTicks)) * effectScale;
        float darkPulse = level.effects().forceBrightLightmap()
                ? 0.0F : PryzmaLightmap.darknessScale(player.tickCount, darkGamma, partialTicks) * effectScale;
        float nightVision;
        float waterVision = player.getWaterVision();
        if (player.hasEffect(MobEffects.NIGHT_VISION)) {
            nightVision = GameRenderer.getNightVisionScale(player, partialTicks);
        } else if (waterVision > 0.0F && player.hasEffect(MobEffects.CONDUIT_POWER)) {
            nightVision = waterVision;
        } else {
            nightVision = 0.0F;
        }
        PryzmaLightmap.Frame frame = new PryzmaLightmap.Frame(
                level.getSkyDarken(partialTicks), level.getSkyFlashTime() > 0, partialTicks,
                level.getRainLevel(partialTicks), level.getThunderLevel(partialTicks), nightVision,
                darkGamma * 0.25F + darkPulse * 0.75F, this.minecraft.options.gamma().get().floatValue());
        this.minecraft.getProfiler().push("prLightTex");
        boolean done = PryzmaLightmap.update(level, frame, this.lightPixels);
        if (done) {
            PrGuiLightmap.onLevelLightmap(true);
            // Vanilla's path, which this replaces, is where the shader pipeline reads darknessLightFactor.
            CapturedRenderingState.INSTANCE.setDarknessLightFactor(darkPulse);
            this.lightTexture.upload();
            this.updateLightTexture = false;
        }
        this.minecraft.getProfiler().pop();
        if (done) {
            ci.cancel();
        }
    }
}
