package net.pryzma.gui;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/**
 * One entry of the Pryzma video settings. {@link #key} is the language key of the caption and the
 * base of the {@code <key>.tooltip.N} lines, exactly as in Pryzma 1.x.
 */
public abstract sealed class PrOption permits PrOption.Cycle, PrOption.Slider {
    final String key;

    PrOption(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public abstract Component label();

    /** {@code "<caption>: "}, the prefix of every 1.x option label. */
    String caption() {
        return I18n.get(key) + ": ";
    }

    /** A button that steps through values; the direction matters only where 1.x honoured it. */
    public static final class Cycle extends PrOption {
        private final Supplier<String> value;
        private final IntConsumer step;

        Cycle(String key, Supplier<String> value, IntConsumer step) {
            super(key);
            this.value = value;
            this.step = step;
        }

        public void cycle(int direction) {
            step.accept(direction);
        }

        @Override
        public Component label() {
            return Component.literal(caption() + value.get());
        }
    }

    /**
     * A slider over {@code steps} positions ({@code steps == 0}: continuous 0..1). Options that
     * trigger expensive work apply on release, as 1.x did for them.
     */
    public static final class Slider extends PrOption {
        final int steps;
        final boolean applyOnRelease;
        private final DoubleSupplier get;
        private final DoubleConsumer set;
        private final DoubleFunction<Component> label;
        private final DoubleFunction<Double> fromPosition;
        private final DoubleFunction<Double> toPosition;

        Slider(String key, int steps, boolean applyOnRelease, DoubleSupplier get, DoubleConsumer set,
                DoubleFunction<Double> fromPosition, DoubleFunction<Double> toPosition, DoubleFunction<Component> label) {
            super(key);
            this.steps = steps;
            this.applyOnRelease = applyOnRelease;
            this.get = get;
            this.set = set;
            this.fromPosition = fromPosition;
            this.toPosition = toPosition;
            this.label = label;
        }

        /** Current value as a slider position in 0..1. */
        double position() {
            return toPosition.apply(get.getAsDouble());
        }

        /** Value for a slider position, snapped to the step grid. */
        double valueAt(double position) {
            double p = steps > 0 ? Math.round(position * steps) / (double) steps : position;
            return fromPosition.apply(Math.max(0.0, Math.min(1.0, p)));
        }

        void set(double value) {
            set.accept(value);
        }

        Component labelFor(double value) {
            return label.apply(value);
        }

        @Override
        public Component label() {
            return label.apply(get.getAsDouble());
        }
    }
}
