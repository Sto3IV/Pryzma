package net.pryzma.farlands.client.iris;

import org.joml.Matrix4f;

public final class IrisState {
   public static volatile boolean enabled;
   public static volatile float near = 16.0F;
   public static volatile float far = 4096.0F;
   public static volatile int renderBlocks = 2048;
   public static volatile int depthTex;
   public static volatile int depthTexNoTranslucent;
   public static volatile Matrix4f projection = new Matrix4f();

   private IrisState() {
   }
}
