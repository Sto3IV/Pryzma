package net.pryzma.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Layer-1 gate + layer-0 fence around Epic Fight first-person hand draws.
 *
 * <p>When OptiFine/Pryzma shaders are active and Epic Fight is present, forces
 * {@code ClientConfig.activateComputeShader = false} for the duration of the draw (CPU
 * {@code drawPosed} → {@code MultiBufferSource.getBuffer} → {@code SVertexBuilder}) and restores
 * {@code Shaders.useProgram(saved)} afterwards. {@code useProgram} identity-compares
 * {@code activeProgram} and would otherwise no-op when Epic Fight stole {@code GL_CURRENT_PROGRAM}
 * without mutating the Java field. Restore pokes the live {@code Shaders.ProgramNone} (never null)
 * so the identity check fails and {@code updateAlphaBlend} still has a real {@code Program} to
 * {@code invokevirtual}.
 *
 * <p>No compile-time {@code yesman.epicfight} types. Absent mod, absent shaders, or any resolution
 * failure: pass-through of {@code action.get()}.
 */
public final class EpicFightShaderBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("Pryzma");
    private static final String CLIENT_CONFIG = "yesman.epicfight.config.ClientConfig";
    private static final String ACTIVATE_COMPUTE = "activateComputeShader";
    private static final String[] CONFIG_CLASSES = {
            "net.optifine.Config",
            "net.pryzma.Config"
    };
    private static final String[] SHADER_CLASSES = {
            "net.optifine.shaders.Shaders",
            "net.pryzma.shaders.Shaders"
    };

    private static final Object LOCK = new Object();

    private static volatile boolean fenceDisabled;
    private static volatile boolean shaderResolved;
    private static volatile boolean computeResolved;

    private static MethodHandle isShaders;
    private static MethodHandle getActiveProgram;
    private static MethodHandle setActiveProgram;
    private static MethodHandle getProgramNone;
    private static MethodHandle useProgram;
    private static MethodHandle getActivateCompute;
    private static MethodHandle setActivateCompute;

    private EpicFightShaderBridge() {}

    public static <T> T wrapHandRender(Supplier<T> action) {
        if (!shouldFence()) {
            return action.get();
        }
        boolean original;
        try {
            original = (boolean) getActivateCompute.invoke();
            setActivateCompute.invoke(false);
        } catch (Throwable t) {
            disableFence("activateComputeShader", t);
            return action.get();
        }
        Object savedProgram = captureProgram();
        try {
            return action.get();
        } finally {
            try {
                setActivateCompute.invoke(original);
            } catch (Throwable t) {
                disableFence("restore activateComputeShader", t);
            }
            restoreProgram(savedProgram);
        }
    }

    public static Object wrapHandRender(Callable<Object> action) {
        return wrapHandRender((Supplier<Object>) () -> {
            try {
                return action.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static boolean shouldFence() {
        if (fenceDisabled) {
            return false;
        }
        if (!EpicFightOutline.isEpicFightLoaded()) {
            return false;
        }
        resolveCompute();
        resolveShaders();
        if (fenceDisabled || getActivateCompute == null || setActivateCompute == null || isShaders == null) {
            return false;
        }
        try {
            return (boolean) isShaders.invoke();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void resolveCompute() {
        if (computeResolved || fenceDisabled) {
            return;
        }
        synchronized (LOCK) {
            if (computeResolved || fenceDisabled) {
                return;
            }
            computeResolved = true;
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                Class<?> config = Class.forName(CLIENT_CONFIG);
                getActivateCompute = lookup.findStaticGetter(config, ACTIVATE_COMPUTE, boolean.class);
                setActivateCompute = lookup.findStaticSetter(config, ACTIVATE_COMPUTE, boolean.class);
            } catch (Throwable t) {
                disableFence("resolve " + ACTIVATE_COMPUTE, t);
            }
        }
    }

    private static void resolveShaders() {
        if (shaderResolved) {
            return;
        }
        synchronized (LOCK) {
            if (shaderResolved) {
                return;
            }
            shaderResolved = true;
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            for (String name : CONFIG_CLASSES) {
                try {
                    Class<?> cls = Class.forName(name);
                    isShaders = lookup.findStatic(cls, "isShaders", MethodType.methodType(boolean.class));
                    break;
                } catch (Throwable ignored) {
                }
            }
            for (String name : SHADER_CLASSES) {
                try {
                    Class<?> cls = Class.forName(name);
                    Field active = cls.getField("activeProgram");
                    getActiveProgram = lookup.unreflectGetter(active);
                    setActiveProgram = lookup.unreflectSetter(active);
                    useProgram = lookup.findStatic(
                            cls, "useProgram", MethodType.methodType(void.class, active.getType()));
                    try {
                        getProgramNone = lookup.unreflectGetter(cls.getField("ProgramNone"));
                    } catch (Throwable ignored) {
                    }
                    break;
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static Object captureProgram() {
        if (getActiveProgram == null) {
            return null;
        }
        try {
            return getActiveProgram.invoke();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Live {@code Program} to park in {@code activeProgram} so {@code useProgram(saved)} does not
     * identity-early-return. Never {@code saved}, never null — a null current program makes
     * {@code updateAlphaBlend} {@code invokevirtual getAlphaState} and NPEs before {@code _glUseProgram}.
     */
    static Object sentinelProgram(Object saved, Object programNone) {
        if (programNone != null && programNone != saved) {
            return programNone;
        }
        return null;
    }

    static void restoreProgram(Object saved) {
        if (saved == null || useProgram == null) {
            return;
        }
        try {
            Object poke = sentinelProgram(saved, readProgramNone());
            if (poke != null && setActiveProgram != null) {
                setActiveProgram.invoke(poke);
            }
            useProgram.invoke(saved);
        } catch (Throwable t) {
            disableFence("restore useProgram", t);
        }
    }

    private static Object readProgramNone() {
        if (getProgramNone == null) {
            return null;
        }
        try {
            return getProgramNone.invoke();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void disableFence(String phase, Throwable t) {
        fenceDisabled = true;
        LOGGER.warn("EpicFightShaderBridge {} failed; disabling fence: {}", phase, String.valueOf(t));
    }
}
