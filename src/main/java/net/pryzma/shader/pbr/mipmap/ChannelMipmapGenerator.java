package net.pryzma.shader.pbr.mipmap;

public class ChannelMipmapGenerator extends AbstractMipmapGenerator {
	protected final BlendFunction redFunc;
	protected final BlendFunction greenFunc;
	protected final BlendFunction blueFunc;
	protected final BlendFunction alphaFunc;

	public ChannelMipmapGenerator(BlendFunction redFunc, BlendFunction greenFunc, BlendFunction blueFunc, BlendFunction alphaFunc) {
		this.redFunc = redFunc;
		this.greenFunc = greenFunc;
		this.blueFunc = blueFunc;
		this.alphaFunc = alphaFunc;
	}

	@Override
	public int blend(int c0, int c1, int c2, int c3) {
		int a = alphaFunc.blend((c0 >>> 24) & 0xFF, (c1 >>> 24) & 0xFF, (c2 >>> 24) & 0xFF, (c3 >>> 24) & 0xFF);
		int b = blueFunc.blend((c0 >>> 16) & 0xFF, (c1 >>> 16) & 0xFF, (c2 >>> 16) & 0xFF, (c3 >>> 16) & 0xFF);
		int g = greenFunc.blend((c0 >>> 8) & 0xFF, (c1 >>> 8) & 0xFF, (c2 >>> 8) & 0xFF, (c3 >>> 8) & 0xFF);
		int r = redFunc.blend(c0 & 0xFF, c1 & 0xFF, c2 & 0xFF, c3 & 0xFF);
		return ((a & 0xFF) << 24) | ((b & 0xFF) << 16) | ((g & 0xFF) << 8) | (r & 0xFF);
	}

	public interface BlendFunction {
		int blend(int v0, int v1, int v2, int v3);
	}
}
