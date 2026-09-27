package net.pryzma.iris.gl.state;

/**
 * Last framebuffer and program bindings made through {@code GlStateManager}, so that a bind of what is already
 * bound does not reach the driver: the same contract vanilla applies to texture bindings.
 *
 * <p>Iris ships this optimisation for framebuffers, but compares the cached framebuffer with the bind
 * <em>target</em> enum instead of the framebuffer name, so it never skips a bind; for programs it only skips
 * a second unbind. Shader pack rendering binds the same framebuffer and program again for every draw
 * ({@code ExtendedShader.apply} binds unconditionally), which this removes.
 *
 * <p>A binding made outside {@code GlStateManager} (raw LWJGL) is invisible here, exactly as it is to vanilla's
 * texture cache; {@link #invalidate()} runs at the start of every frame, so such a desync lasts at most one frame.
 * Render thread only.
 */
public final class GlBindingCache {
	public static final GlBindingCache INSTANCE = new GlBindingCache();

	/** No known binding: the next bind always reaches GL. Never a valid GL name. */
	public static final int UNKNOWN = -1;

	public static final int GL_FRAMEBUFFER = 0x8D40;
	public static final int GL_READ_FRAMEBUFFER = 0x8CA8;
	public static final int GL_DRAW_FRAMEBUFFER = 0x8CA9;

	private int drawFramebuffer = UNKNOWN;
	private int readFramebuffer = UNKNOWN;
	private int program = UNKNOWN;

	/**
	 * Records {@code glBindFramebuffer(target, framebuffer)}.
	 *
	 * @return whether the bind must be issued; false when it would change nothing
	 */
	public boolean bindFramebuffer(int target, int framebuffer) {
		switch (target) {
			case GL_FRAMEBUFFER -> {
				if (drawFramebuffer == framebuffer && readFramebuffer == framebuffer) {
					return false;
				}
				drawFramebuffer = framebuffer;
				readFramebuffer = framebuffer;
			}
			case GL_DRAW_FRAMEBUFFER -> {
				if (drawFramebuffer == framebuffer) {
					return false;
				}
				drawFramebuffer = framebuffer;
			}
			case GL_READ_FRAMEBUFFER -> {
				if (readFramebuffer == framebuffer) {
					return false;
				}
				readFramebuffer = framebuffer;
			}
			default -> {
				// Not a framebuffer target: let GL report the error, and trust nothing afterwards.
				drawFramebuffer = UNKNOWN;
				readFramebuffer = UNKNOWN;
			}
		}
		return true;
	}

	/** Deleting a bound framebuffer reverts that binding to the default framebuffer (0). */
	public void deleteFramebuffer(int framebuffer) {
		if (drawFramebuffer == framebuffer) {
			drawFramebuffer = 0;
		}
		if (readFramebuffer == framebuffer) {
			readFramebuffer = 0;
		}
	}

	/**
	 * Records {@code glUseProgram(program)}.
	 *
	 * @return whether the call must be issued; false when the program is already in use
	 */
	public boolean useProgram(int program) {
		if (this.program == program) {
			return false;
		}
		this.program = program;
		return true;
	}

	/**
	 * A deleted program stays current until unbound, but its name may be reused afterwards; forget it so that
	 * binding a new program with a recycled name always reaches GL.
	 */
	public void deleteProgram(int program) {
		if (this.program == program) {
			this.program = UNKNOWN;
		}
	}

	/** Forgets every binding; the next binds reach GL. */
	public void invalidate() {
		drawFramebuffer = UNKNOWN;
		readFramebuffer = UNKNOWN;
		program = UNKNOWN;
	}

	public int drawFramebuffer() {
		return drawFramebuffer;
	}

	public int readFramebuffer() {
		return readFramebuffer;
	}

	public int program() {
		return program;
	}
}
