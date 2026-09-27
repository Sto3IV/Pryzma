package net.pryzma.render;

import net.minecraft.world.phys.Vec3;

/**
 * Two-entry memo of {@code ClientLevel.getSkyColor}, keyed by every input the result depends on: position,
 * partial tick, game time and day time. Least recently used entry is replaced. Render thread only.
 */
public final class PrSkyColorMemo {
    private final Entry first = new Entry();
    private final Entry second = new Entry();

    public Vec3 get(double x, double y, double z, float partialTick, long gameTime, long dayTime) {
        if (first.matches(x, y, z, partialTick, gameTime, dayTime)) {
            return first.color;
        }
        if (second.matches(x, y, z, partialTick, gameTime, dayTime)) {
            second.swapWith(first);
            return first.color;
        }
        return null;
    }

    /** Stores a computed colour as the most recent entry, evicting the older one. */
    public void put(double x, double y, double z, float partialTick, long gameTime, long dayTime, Vec3 color) {
        second.swapWith(first);
        first.set(x, y, z, partialTick, gameTime, dayTime, color);
    }

    public void clear() {
        first.color = null;
        second.color = null;
    }

    private static final class Entry {
        private Vec3 color;
        private double x;
        private double y;
        private double z;
        private float partialTick;
        private long gameTime;
        private long dayTime;

        boolean matches(double x, double y, double z, float partialTick, long gameTime, long dayTime) {
            return color != null && this.x == x && this.y == y && this.z == z && this.partialTick == partialTick
                    && this.gameTime == gameTime && this.dayTime == dayTime;
        }

        void set(double x, double y, double z, float partialTick, long gameTime, long dayTime, Vec3 color) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.partialTick = partialTick;
            this.gameTime = gameTime;
            this.dayTime = dayTime;
            this.color = color;
        }

        void swapWith(Entry other) {
            Vec3 c = color; color = other.color; other.color = c;
            double d = x; x = other.x; other.x = d;
            d = y; y = other.y; other.y = d;
            d = z; z = other.z; other.z = d;
            float f = partialTick; partialTick = other.partialTick; other.partialTick = f;
            long l = gameTime; gameTime = other.gameTime; other.gameTime = l;
            l = dayTime; dayTime = other.dayTime; other.dayTime = l;
        }
    }
}
