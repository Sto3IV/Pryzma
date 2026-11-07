package net.pryzma.shader.gl.program;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import net.pryzma.shader.gl.GlResource;
import net.pryzma.shader.gl.ShaderRenderSystem;
import org.lwjgl.opengl.GL43C;

public final class Program extends GlResource {
	private final ProgramUniforms uniforms;
	private final ProgramSamplers samplers;
	private final ProgramImages images;

	Program(int program, ProgramUniforms uniforms, ProgramSamplers samplers, ProgramImages images) {
		super(program);

		this.uniforms = uniforms;
		this.samplers = samplers;
		this.images = images;
	}

	public static void unbind() {
		ProgramUniforms.clearActiveUniforms();
		ProgramSamplers.clearActiveSamplers();
		GlStateManager._glUseProgram(0);
	}

	public void use() {
		ShaderRenderSystem.memoryBarrier(GL43C.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL43C.GL_TEXTURE_FETCH_BARRIER_BIT | GL43C.GL_SHADER_STORAGE_BARRIER_BIT);
		GlStateManager._glUseProgram(getGlId());

		uniforms.update();
		samplers.update();
		images.update();
	}

	public void destroyInternal() {
		GlStateManager.glDeleteProgram(getGlId());
	}

	/**
	 * @return the OpenGL ID of this program.
	 * @deprecated this should be encapsulated eventually
	 */
	@Deprecated
	public int getProgramId() {
		return getGlId();
	}

	public int getActiveImages() {
		return images.getActiveImages();
	}
}
