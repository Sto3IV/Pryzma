package net.pryzma.entity.model;

import net.pryzma.Pryzma;

/**
 * The animation program of one CEM model tree: every {@code .jem} assignment in declaration
 * order, run once per entity render (the first part drawn triggers it, after vanilla
 * {@code setupAnim} has posed the parts, so expressions can build on vanilla values).
 */
public final class PrCemModel {
    private final String name;
    private final Runnable[] program;
    private int lastSerial = Integer.MIN_VALUE;
    private boolean failed;

    PrCemModel(String name, Runnable[] program) {
        this.name = name;
        this.program = program;
    }

    public int size() {
        return program.length;
    }

    void animate() {
        if (program.length == 0 || failed || !PrCemContext.active()) {
            return;
        }
        int serial = PrCemContext.serial();
        if (serial == lastSerial) {
            return;
        }
        lastSerial = serial;
        try {
            run();
        } catch (RuntimeException e) {
            // A broken animation must not take the entity render down with it; the model stays static.
            failed = true;
            Pryzma.LOGGER.warn("CEM animations of {} disabled after an error", name, e);
        }
    }

    /** Runs every assignment once, in order. */
    void run() {
        for (Runnable step : program) {
            step.run();
        }
    }
}
