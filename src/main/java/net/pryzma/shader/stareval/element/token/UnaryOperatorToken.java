package net.pryzma.shader.stareval.element.token;

import net.pryzma.shader.stareval.element.ExpressionElement;
import net.pryzma.shader.stareval.element.PriorityOperatorElement;
import net.pryzma.shader.stareval.element.tree.UnaryExpressionElement;
import net.pryzma.shader.stareval.parser.UnaryOp;

public class UnaryOperatorToken extends Token implements PriorityOperatorElement {
	private final UnaryOp op;

	public UnaryOperatorToken(UnaryOp op) {
		this.op = op;
	}

	@Override
	public String toString() {
		return "UnaryOp{" + this.op + "}";
	}

	@Override
	public int getPriority() {
		return -1;
	}

	@Override
	public UnaryExpressionElement resolveWith(ExpressionElement right) {
		return new UnaryExpressionElement(this.op, right);
	}
}
