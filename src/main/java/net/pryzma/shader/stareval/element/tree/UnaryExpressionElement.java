package net.pryzma.shader.stareval.element.tree;

import net.pryzma.shader.stareval.element.ExpressionElement;
import net.pryzma.shader.stareval.parser.UnaryOp;

public record UnaryExpressionElement(UnaryOp op, ExpressionElement inner) implements ExpressionElement {


	@Override
	public String toString() {
		return "UnaryExpr{" + this.op + " {" + this.inner + "} }";
	}
}
