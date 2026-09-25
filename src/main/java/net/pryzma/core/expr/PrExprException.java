package net.pryzma.core.expr;

/** A malformed expression: unknown name, wrong argument count or type, unbalanced brackets. */
public final class PrExprException extends Exception {
    public PrExprException(String message) {
        super(message);
    }
}
