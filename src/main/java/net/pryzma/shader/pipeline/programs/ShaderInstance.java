package net.pryzma.shader.pipeline.programs;

import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import net.pryzma.shader.gl.shader.GlShader;
import net.pryzma.shader.gl.shader.ProgramCreator;
import net.pryzma.shader.gl.shader.ShaderType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20C;

import java.io.Closeable;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShaderInstance implements Closeable {
	public static Uniform MODEL_VIEW_MATRIX = new Uniform("ModelViewMat", -1);
	public static Uniform PROJECTION_MATRIX = new Uniform("ProjMat", -1);

	protected final int programId;
	protected final String name;
	protected final VertexFormat vertexFormat;
	protected final List<Uniform> uniforms = new ArrayList<>();
	protected final Map<String, Uniform> uniformMap = new HashMap<>();

	protected GlShader vertexShader;
	protected GlShader fragmentShader;
	protected GlShader geometryShader;
	protected GlShader tessControlShader;
	protected GlShader tessEvalShader;

	public ShaderInstance(ResourceProvider resourceFactory, String name, VertexFormat vertexFormat) throws IOException {
		this.name = name;
		this.vertexFormat = vertexFormat;

		String vertexSrc = readResource(resourceFactory, name + ".vsh");
		String fragmentSrc = readResource(resourceFactory, name + ".fsh");
		String geometrySrc = readResource(resourceFactory, name + "_geometry.gsh");
		String tessControlSrc = readResource(resourceFactory, name + "_tessControl.tcs");
		String tessEvalSrc = readResource(resourceFactory, name + "_tessEval.tes");

		List<GlShader> shaders = new ArrayList<>();
		if (vertexSrc != null) {
			this.vertexShader = new GlShader(ShaderType.VERTEX, name + "_vertex.vsh", vertexSrc);
			shaders.add(this.vertexShader);
		}
		if (geometrySrc != null) {
			this.geometryShader = new GlShader(ShaderType.GEOMETRY, name + "_geometry.gsh", geometrySrc);
			shaders.add(this.geometryShader);
		}
		if (tessControlSrc != null) {
			this.tessControlShader = new GlShader(ShaderType.TESSELATION_CONTROL, name + "_tessControl.tcs", tessControlSrc);
			shaders.add(this.tessControlShader);
		}
		if (tessEvalSrc != null) {
			this.tessEvalShader = new GlShader(ShaderType.TESSELATION_EVAL, name + "_tessEval.tes", tessEvalSrc);
			shaders.add(this.tessEvalShader);
		}
		if (fragmentSrc != null) {
			this.fragmentShader = new GlShader(ShaderType.FRAGMENT, name + "_fragment.fsh", fragmentSrc);
			shaders.add(this.fragmentShader);
		}

		if (!shaders.isEmpty()) {
			this.programId = ProgramCreator.create(name, shaders.toArray(new GlShader[0]));
		} else {
			this.programId = 0;
		}
	}

	public ShaderInstance(int programId, String name, VertexFormat vertexFormat) {
		this.programId = programId;
		this.name = name;
		this.vertexFormat = vertexFormat;
	}

	private static @Nullable String readResource(ResourceProvider provider, String filename) {
		return provider.getResource(Identifier.fromNamespaceAndPath("minecraft", filename))
			.map(r -> {
				try {
					return IOUtils.toString(r.open(), StandardCharsets.UTF_8);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}).orElse(null);
	}

	public int getId() {
		return programId;
	}

	public String getName() {
		return name;
	}

	public VertexFormat getVertexFormat() {
		return vertexFormat;
	}

	public GlShader getVertexProgram() {
		return vertexShader;
	}

	public GlShader getFragmentProgram() {
		return fragmentShader;
	}

	public GlShader getGeometryProgram() {
		return geometryShader;
	}

	public GlShader getTessControlProgram() {
		return tessControlShader;
	}

	public GlShader getTessEvalProgram() {
		return tessEvalShader;
	}

	public void apply() {
		if (this.programId > 0) {
			GlStateManager._glUseProgram(this.programId);
		}
	}

	public void clear() {
		GlStateManager._glUseProgram(0);
	}

	public void attachToProgram() {
	}

	@Override
	public void close() {
		if (this.programId > 0) {
			GlStateManager.glDeleteProgram(this.programId);
		}
		if (vertexShader != null) vertexShader.destroy();
		if (fragmentShader != null) fragmentShader.destroy();
		if (geometryShader != null) geometryShader.destroy();
		if (tessControlShader != null) tessControlShader.destroy();
		if (tessEvalShader != null) tessEvalShader.destroy();
	}

	@Nullable
	public Uniform getUniform(@NotNull String name) {
		return uniformMap.computeIfAbsent(name, n -> {
			int location = GlStateManager._glGetUniformLocation(this.programId, n);
			if (location == -1) {
				return null;
			}
			Uniform u = new Uniform(n, location);
			uniforms.add(u);
			return u;
		});
	}

	@Nullable
	public Uniform safeGetUniform(String name) {
		return getUniform(name);
	}

	public void setSampler(String name, Object texture) {
	}

	public static class Uniform {
		private final String name;
		private final int location;
		private float[] floatValues;
		private int[] intValues;
		private FloatBuffer floatBuffer;
		private boolean dirty;

		public Uniform(String name, int location) {
			this.name = name;
			this.location = location;
		}

		public int getLocation() {
			return location;
		}

		public String getName() {
			return name;
		}

		public void set(float f) {
			this.floatValues = new float[]{f};
			this.dirty = true;
		}

		public void set(int i) {
			this.intValues = new int[]{i};
			this.dirty = true;
		}

		public void set(float[] values) {
			this.floatValues = values;
			this.dirty = true;
		}

		public void set(Matrix4f matrix) {
			if (this.floatValues == null || this.floatValues.length < 16) {
				this.floatValues = new float[16];
			}
			matrix.get(this.floatValues);
			this.dirty = true;
		}

		public FloatBuffer getFloatBuffer() {
			if (this.floatBuffer == null) {
				this.floatBuffer = BufferUtils.createFloatBuffer(16);
			}
			this.floatBuffer.clear();
			if (this.floatValues != null) {
				this.floatBuffer.put(this.floatValues);
				this.floatBuffer.flip();
			}
			return this.floatBuffer;
		}

		public void upload() {
			if (location == -1 || !dirty) return;
			if (floatValues != null) {
				if (floatValues.length == 1) {
					GL20C.glUniform1f(location, floatValues[0]);
				} else if (floatValues.length == 2) {
					GL20C.glUniform2f(location, floatValues[0], floatValues[1]);
				} else if (floatValues.length == 3) {
					GL20C.glUniform3f(location, floatValues[0], floatValues[1], floatValues[2]);
				} else if (floatValues.length == 4) {
					GL20C.glUniform4f(location, floatValues[0], floatValues[1], floatValues[2], floatValues[3]);
				} else if (floatValues.length == 9) {
					GL20C.glUniformMatrix3fv(location, false, floatValues);
				} else if (floatValues.length == 16) {
					GL20C.glUniformMatrix4fv(location, false, floatValues);
				}
			} else if (intValues != null) {
				if (intValues.length == 1) {
					GL20C.glUniform1i(location, intValues[0]);
				} else if (intValues.length == 2) {
					GL20C.glUniform2i(location, intValues[0], intValues[1]);
				}
			}
			dirty = false;
		}
	}
}
