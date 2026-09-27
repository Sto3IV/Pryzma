package net.pryzma.iris.pathways.colorspace;

public interface ColorSpaceConverter {
	void rebuildProgram(int width, int height, ColorSpace colorSpace);

	void process(int target);
}
