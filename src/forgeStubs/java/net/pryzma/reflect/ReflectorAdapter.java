package net.pryzma.reflect;

/*
 * Ranni: The things I do for you... Only a madman or someone deeply in love would touch this reflection logic.
 * Celian told me this would be a quick 5-minute fix. We are now on day 3 of debugging ASM transformers. My logic nodes are bleeding.
 */

import java.lang.reflect.Method;
import java.util.function.BiConsumer;
import java.util.function.ObjIntConsumer;
import java.util.function.Supplier;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.pryzma.compat.EpicFightShaderBridge;

/**
 * Adapts legacy OptiFine reflection invocations to match modern NeoForge 21.1 method signatures,
 * preventing IllegalArgumentException and permanent deactivation of critical rendering and branding hooks.
 */
public class ReflectorAdapter {

    private static final ThreadLocal<PoseStack> POSE_STACK_HOLDER =
            ThreadLocal.withInitial(PoseStack::new);

    private static RenderLevelStageEvent.Stage resolveStage(ReflectorField stageField) {
        if (stageField == null) {
            return null;
        }
        if (stageField == Reflector.RenderLevelStageEvent_Stage_AFTER_SKY) {
            return RenderLevelStageEvent.Stage.AFTER_SKY;
        }
        if (stageField == Reflector.RenderLevelStageEvent_Stage_AFTER_ENTITIES) {
            return RenderLevelStageEvent.Stage.AFTER_ENTITIES;
        }
        if (stageField == Reflector.RenderLevelStageEvent_Stage_AFTER_BLOCK_ENTITIES) {
            return RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES;
        }
        if (stageField == Reflector.RenderLevelStageEvent_Stage_AFTER_PARTICLES) {
            return RenderLevelStageEvent.Stage.AFTER_PARTICLES;
        }
        if (stageField == Reflector.RenderLevelStageEvent_Stage_AFTER_WEATHER) {
            return RenderLevelStageEvent.Stage.AFTER_WEATHER;
        }
        if (stageField == Reflector.RenderLevelStageEvent_Stage_AFTER_LEVEL) {
            return RenderLevelStageEvent.Stage.AFTER_LEVEL;
        }
        Object stageVal = stageField.getValue();
        return (stageVal instanceof RenderLevelStageEvent.Stage stage) ? stage : null;
    }

    private static PoseStack resetPose(PoseStack stack, Matrix4f matrixView) {
        if (stack == null) {
            stack = new PoseStack();
        } else {
            try {
                while (!stack.clear()) {
                    stack.popPose();
                }
                stack.setIdentity();
            } catch (Throwable ignored) {
                stack = new PoseStack();
            }
        }
        if (matrixView != null) {
            stack.last().pose().set(matrixView);
        }
        return stack;
    }

    public static void dispatchRenderStageS(
            ReflectorField stageField,
            LevelRenderer levelRenderer,
            Matrix4f matrixView,
            Matrix4f matrixProjection,
            int ticks,
            Camera camera,
            Frustum frustum) {
        RenderLevelStageEvent.Stage stage = resolveStage(stageField);
        if (stage == null) {
            return;
        }

        PoseStack poseStack = resetPose(POSE_STACK_HOLDER.get(), matrixView);
        ClientHooks.dispatchRenderStage(stage, levelRenderer, poseStack, matrixView, matrixProjection, ticks, camera, frustum);
    }

    public static boolean isModLoadedBridge(net.neoforged.fml.ModList list, String modid) {
        if ("optifine".equals(modid) || "pryzma".equals(modid) || "prizma_beta".equals(modid)) {
            return true;
        }
        return list != null && list.isLoaded(modid);
    }

    public static net.neoforged.neoforgespi.language.IModFileInfo getModFileByIdBridge(net.neoforged.fml.ModList list, String modid) {
        if ("optifine".equals(modid) || "pryzma".equals(modid) || "prizma_beta".equals(modid)) {
            return list != null ? list.getModFileById("prizma_beta") : null;
        }
        return list != null ? list.getModFileById(modid) : null;
    }

    public static Object invoke(Method targetMethod, Object target, Object[] params) throws Throwable {
        if (targetMethod == null) {
            return null;
        }

        try {
            if (!targetMethod.canAccess(target)) {
                targetMethod.setAccessible(true);
            }
        } catch (Throwable ignored) {
        }

        String name = targetMethod.getName();
        Class<?> declaring = targetMethod.getDeclaringClass();
        String declName = declaring != null ? declaring.getName() : "";

        if ("forEachLine".equals(name) && declName.endsWith("BrandingControl")) {
            if (params != null && params.length >= 3 && params[2] instanceof ObjIntConsumer<?> oic) {
                ObjIntConsumer<Object> consumer = (ObjIntConsumer<Object>) oic;
                BiConsumer<Integer, String> biConsumer = (idx, text) -> consumer.accept(text, idx);
                Object[] adapted = new Object[] { params[0], params[1], biConsumer };
                return targetMethod.invoke(target, adapted);
            }
        } else if ("forEachAboveCopyrightLine".equals(name) && declName.endsWith("BrandingControl")) {
            if (params != null && params.length >= 1 && params[0] instanceof ObjIntConsumer<?> oic) {
                ObjIntConsumer<Object> consumer = (ObjIntConsumer<Object>) oic;
                BiConsumer<Integer, String> biConsumer = (idx, text) -> consumer.accept(text, idx);
                Object[] adapted = new Object[] { biConsumer };
                return targetMethod.invoke(target, adapted);
            }
        } else if ("renderSky".equals(name) && (declName.endsWith("IDimensionSpecialEffectsExtension") || declName.endsWith("DimensionSpecialEffects"))) {
            if (params != null && params.length == 7) {
                // OptiFine passed: [0]=ClientLevel, [1]=ticks, [2]=partialTick, [3]=Camera, [4]=Matrix4f modelView, [5]=isFog, [6]=setupFog
                // NeoForge expects: (ClientLevel, int ticks, float partialTick, Matrix4f modelViewMatrix, Camera camera, Matrix4f projectionMatrix, boolean isFog, Runnable setupFog)
                Matrix4f modelView = (params[4] instanceof Matrix4f m) ? m : new Matrix4f();
                Camera camera = (params[3] instanceof Camera c) ? c : null;
                Matrix4f projection = safeGetProjectionMatrix();
                Object[] adapted = new Object[] {
                    params[0],
                    params[1],
                    params[2],
                    modelView,
                    camera,
                    projection,
                    params[5],
                    params[6]
                };
                return targetMethod.invoke(target, adapted);
            }
        } else if ("onDrawHighlight".equals(name) && (declName.endsWith("ClientHooks") || declName.endsWith("ForgeHooksClient"))) {
            if (params != null && params.length == 6 && !(params[3] instanceof DeltaTracker)) {
                // OptiFine passed: [0]=LevelRenderer, [1]=Camera, [2]=HitResult, [3]=Float partialTick, [4]=PoseStack, [5]=MultiBufferSource
                // NeoForge expects: (LevelRenderer, Camera, HitResult, DeltaTracker, PoseStack, MultiBufferSource)
                DeltaTracker tracker = null;
                try {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc != null) {
                        tracker = mc.getTimer();
                    }
                } catch (Throwable ignored) {
                }
                if (tracker == null) {
                    final float pt = (params[3] instanceof Number n) ? n.floatValue() : 0.0f;
                    tracker = new DeltaTracker() {
                        @Override public float getGameTimeDeltaTicks() { return pt; }
                        @Override public float getGameTimeDeltaPartialTick(boolean b) { return pt; }
                        @Override public float getRealtimeDeltaTicks() { return pt; }
                    };
                }
                Object[] adapted = new Object[] {
                    params[0],
                    params[1],
                    params[2],
                    tracker,
                    params[4],
                    params[5]
                };
                return targetMethod.invoke(target, adapted);
            }
        } else if ("renderClouds".equals(name) && (declName.endsWith("IDimensionSpecialEffectsExtension") || declName.endsWith("DimensionSpecialEffects"))) {
            if (params != null && params.length == 8) {
                // OptiFine passed: [0]=ClientLevel, [1]=ticks, [2]=partialTick, [3]=PoseStack, [4]=camX, [5]=camY, [6]=camZ, [7]=Matrix4f modelView
                // NeoForge expects: (ClientLevel, int ticks, float partialTick, PoseStack, double camX, double camY, double camZ, Matrix4f modelViewMatrix, Matrix4f projectionMatrix)
                Matrix4f projection = safeGetProjectionMatrix();
                Object[] adapted = new Object[] {
                    params[0],
                    params[1],
                    params[2],
                    params[3],
                    params[4],
                    params[5],
                    params[6],
                    params[7],
                    projection
                };
                return targetMethod.invoke(target, adapted);
            }
        } else if ("renderSpecificFirstPersonHand".equals(name)) {
            Supplier<Object> hand = () -> {
                try {
                    return targetMethod.invoke(target, params);
                } catch (Throwable t) {
                    // If a mod's custom first-person hand renderer throws (e.g. uninitialized state or NPE),
                    // safely fall back to false so vanilla/Pryzma hand rendering proceeds without crashing the client.
                    return Boolean.FALSE;
                }
            };
            return EpicFightShaderBridge.wrapHandRender(hand);
        }

        return targetMethod.invoke(target, params);
    }

    private static Matrix4f safeGetProjectionMatrix() {
        try {
            Matrix4f proj = RenderSystem.getProjectionMatrix();
            if (proj != null) {
                return proj;
            }
        } catch (Throwable ignored) {
        }
        return new Matrix4f();
    }
}
