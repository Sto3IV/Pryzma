package net.pryzma.farlands.client;

import java.nio.ByteBuffer;

public interface ShaderPath {
   boolean active();

   void enabledChanged(boolean var1);

   void frame(float var1, float var2);

   int[] upload(ByteBuffer var1, int var2);

   void delete(int var1, int var2);

   void begin(boolean var1, float var2);

   void draw(boolean var1, int var2, float var3, float var4, float var5, int var6, int var7);

   void end(boolean var1);

   void clear();

   void close();
}
