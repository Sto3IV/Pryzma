package net.pryzma.lod;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL20C;

import net.pryzma.Pryzma;

/**
 * Dedicated OpenGL program for Farlands LOD terrain (assets/pryzma/shaders/core/lod_terrain).
 * Decoupled from vanilla Blaze3D/RenderPearl shader wrappers.
 */
public final class LodProgram {
    private static final float[] MATRIX_BUFFER = new float[16];

    private static int programId = -1;
    private static int uModelViewMat = -1;
    private static int uProjMat = -1;
    private static int uChunkOffset = -1;
    private static int uColorModulator = -1;
    private static int uFogStart = -1;
    private static int uFogEnd = -1;
    private static int uFogColor = -1;
    private static int uClipRadius = -1;
    private static int uSectorOrigin = -1;
    private static int uCameraBlock = -1;
    private static int uCameraFrac = -1;
    private static int uUseInstancedOffset = -1;
    private static int uSampler2 = -1;

    private static boolean failed;

    private LodProgram() {
    }

    public static boolean isAvailable() {
        if (programId < 0 && !failed) {
            init();
        }
        return programId >= 0;
    }

    public static void init() {
        if (programId >= 0) {
            GL20C.glDeleteProgram(programId);
            programId = -1;
        }
        failed = false;
        try {
            String vsh = loadResource("/assets/pryzma/shaders/core/lod_terrain.vsh");
            String fsh = loadResource("/assets/pryzma/shaders/core/lod_terrain.fsh");

            int vShader = compileShader(GL20C.GL_VERTEX_SHADER, vsh);
            int fShader = compileShader(GL20C.GL_FRAGMENT_SHADER, fsh);

            int prog = GL20C.glCreateProgram();
            GL20C.glAttachShader(prog, vShader);
            GL20C.glAttachShader(prog, fShader);

            // Bind attributes matching LodVertexFormat
            GL20C.glBindAttribLocation(prog, 0, "vPosition");
            GL20C.glBindAttribLocation(prog, 1, "iris_color");
            GL20C.glBindAttribLocation(prog, 2, "irisExtra");
            GL20C.glBindAttribLocation(prog, 3, "SectorOriginAttr");

            GL20C.glLinkProgram(prog);
            GL20C.glDeleteShader(vShader);
            GL20C.glDeleteShader(fShader);

            if (GL20C.glGetProgrami(prog, GL20C.GL_LINK_STATUS) == 0) {
                String log = GL20C.glGetProgramInfoLog(prog);
                GL20C.glDeleteProgram(prog);
                throw new IllegalStateException("LodProgram link failed:\n" + log);
            }

            programId = prog;
            uModelViewMat = GL20C.glGetUniformLocation(prog, "ModelViewMat");
            uProjMat = GL20C.glGetUniformLocation(prog, "ProjMat");
            uChunkOffset = GL20C.glGetUniformLocation(prog, "ChunkOffset");
            uColorModulator = GL20C.glGetUniformLocation(prog, "ColorModulator");
            uFogStart = GL20C.glGetUniformLocation(prog, "FogStart");
            uFogEnd = GL20C.glGetUniformLocation(prog, "FogEnd");
            uFogColor = GL20C.glGetUniformLocation(prog, "FogColor");
            uClipRadius = GL20C.glGetUniformLocation(prog, "ClipRadius");
            uSectorOrigin = GL20C.glGetUniformLocation(prog, "SectorOrigin");
            uCameraBlock = GL20C.glGetUniformLocation(prog, "CameraBlock");
            uCameraFrac = GL20C.glGetUniformLocation(prog, "CameraFrac");
            uUseInstancedOffset = GL20C.glGetUniformLocation(prog, "UseInstancedOffset");
            uSampler2 = GL20C.glGetUniformLocation(prog, "Sampler2");

            GL20C.glUseProgram(prog);
            if (uSampler2 >= 0) {
                GL20C.glUniform1i(uSampler2, 2);
            }
            if (uColorModulator >= 0) {
                GL20C.glUniform4f(uColorModulator, 1.0F, 1.0F, 1.0F, 1.0F);
            }
            GL20C.glUseProgram(0);
        } catch (Exception e) {
            failed = true;
            Pryzma.LOGGER.error("Failed to build Farlands LOD shader program", e);
        }
    }

    public static void use() {
        if (isAvailable()) {
            GL20C.glUseProgram(programId);
        }
    }

    public static void unuse() {
        GL20C.glUseProgram(0);
    }

    public static void setModelView(Matrix4f matrix) {
        if (uModelViewMat >= 0 && matrix != null) {
            matrix.get(MATRIX_BUFFER);
            GL20C.glUniformMatrix4fv(uModelViewMat, false, MATRIX_BUFFER);
        }
    }

    public static void setProj(Matrix4f matrix) {
        if (uProjMat >= 0 && matrix != null) {
            matrix.get(MATRIX_BUFFER);
            GL20C.glUniformMatrix4fv(uProjMat, false, MATRIX_BUFFER);
        }
    }

    public static void setChunkOffset(float x, float y, float z) {
        if (uChunkOffset >= 0) {
            GL20C.glUniform3f(uChunkOffset, x, y, z);
        }
    }

    public static void setSectorOrigin(int x, int z) {
        if (uSectorOrigin >= 0) {
            GL20C.glUniform2i(uSectorOrigin, x, z);
        }
    }

    public static void setCameraBlock(int x, int y, int z) {
        if (uCameraBlock >= 0) {
            GL20C.glUniform3i(uCameraBlock, x, y, z);
        }
    }

    public static void setCameraFrac(float x, float y, float z) {
        if (uCameraFrac >= 0) {
            GL20C.glUniform3f(uCameraFrac, x, y, z);
        }
    }

    public static void setUseInstancedOffset(int use) {
        if (uUseInstancedOffset >= 0) {
            GL20C.glUniform1i(uUseInstancedOffset, use);
        }
    }

    public static void setFog(float start, float end, float r, float g, float b, float a) {
        if (uFogStart >= 0) GL20C.glUniform1f(uFogStart, start);
        if (uFogEnd >= 0) GL20C.glUniform1f(uFogEnd, end);
        if (uFogColor >= 0) GL20C.glUniform4f(uFogColor, r, g, b, a);
    }

    public static void setClipRadius(float clip) {
        if (uClipRadius >= 0) {
            GL20C.glUniform1f(uClipRadius, clip);
        }
    }

    public static void setColorModulator(float r, float g, float b, float a) {
        if (uColorModulator >= 0) {
            GL20C.glUniform4f(uColorModulator, r, g, b, a);
        }
    }

    public static void bindLightmap(int glTextureId) {
        GL13C.glActiveTexture(GL13C.GL_TEXTURE2);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, glTextureId);
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0);
    }

    public static void unbindLightmap() {
        GL13C.glActiveTexture(GL13C.GL_TEXTURE2);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, 0);
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0);
    }

    public static void destroy() {
        if (programId >= 0) {
            GL20C.glDeleteProgram(programId);
            programId = -1;
        }
        failed = false;
    }

    private static int compileShader(int type, String source) {
        int shader = GL20C.glCreateShader(type);
        GL20C.glShaderSource(shader, source);
        GL20C.glCompileShader(shader);
        if (GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) == 0) {
            String log = GL20C.glGetShaderInfoLog(shader);
            GL20C.glDeleteShader(shader);
            throw new IllegalStateException("Lod shader compile failed (type " + type + "):\n" + log);
        }
        return shader;
    }

    private static String loadResource(String path) throws IOException {
        try (InputStream in = LodProgram.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Resource not found: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
