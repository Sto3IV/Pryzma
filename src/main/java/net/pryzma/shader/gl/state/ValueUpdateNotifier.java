package net.pryzma.shader.gl.state;

/**
 * A
 */
public interface ValueUpdateNotifier {
	/**
	 * Sets up a listener with this notifier.
	 */
	void setListener(Runnable listener);
}
