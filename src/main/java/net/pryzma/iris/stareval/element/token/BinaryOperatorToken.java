package net.pryzma.iris.stareval.element.token;

import net.pryzma.iris.stareval.parser.BinaryOp;

public class BinaryOperatorToken extends Token {
	public final BinaryOp op;

	public BinaryOperatorToken(BinaryOp op) {
		this.op = op;
	}

	@Override
	public String toString() {
		return "BinaryOp{" + this.op + "}";
	}
}
