package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.textures.GpuTexture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.pryzma.lightmap.PryzmaLightmap;
import net.pryzma.shader.uniforms.CapturedRenderingState;

/**
 * Custom lightmaps. When the level has one, Pryzma populates the 16x16 lightmap
 * texture from custom resource pack files and uploads it to the GPU lightmap texture.
 */
@Mixin(Lightmap.class)
abstract class LightmapMixin {
    @Shadow @Final private GpuTexture texture;

    private NativeImage prLightPixels;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void prRenderCustomLightmap(LightmapRenderState state, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || !PryzmaLightmap.isActive(level)) {
            return;
        }
        if (prLightPixels == null) {
            prLightPixels = new NativeImage(16, 16, false);
        }
        float partialTicks = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float effectScale = mc.options.darknessEffectScale().get().floatValue();
        MobEffectInstance darkness = player.getEffect(MobEffects.DARKNESS);
        float darkGamma = (darkness == null ? 0.0F : player.getEffectBlendFactor(MobEffects.DARKNESS, partialTicks)) * effectScale;
        float darkPulse = (level.dimensionType().ambientLight() > 0.0F || level.dimensionType().hasCeiling())
                ? 0.0F : PryzmaLightmap.darknessScale(player.tickCount, darkGamma, partialTicks) * effectScale;
        float nightVision;
        float waterVision = player.getWaterVision();
        if (player.hasEffect(MobEffects.NIGHT_VISION)) {
            nightVision = GameRenderer.nightVisionScale(player, partialTicks);
        } else if (waterVision > 0.0F && player.hasEffect(MobEffects.CONDUIT_POWER)) {
            nightVision = waterVision;
        } else {
            nightVision = 0.0F;
        }
        boolean lightningFlash = level.endFlashState().getIntensity(partialTicks) > 0.0F;
        PryzmaLightmap.Frame frame = new PryzmaLightmap.Frame(
                (float) level.getSkyDarken(), lightningFlash, partialTicks,
                level.getRainLevel(partialTicks), level.getThunderLevel(partialTicks), nightVision,
                darkGamma * 0.25F + darkPulse * 0.75F, mc.options.gamma().get().floatValue());

        boolean done = PryzmaLightmap.update(level, frame, this.prLightPixels);
        if (done) {
            CapturedRenderingState.INSTANCE.setDarknessLightFactor(darkPulse);
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.writeToTexture(this.texture, this.prLightPixels);
            ci.cancel();
        }
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void prClose(CallbackInfo ci) {
        if (prLightPixels != null) {
            prLightPixels.close();
            prLightPixels = null;
        }
    }
}
