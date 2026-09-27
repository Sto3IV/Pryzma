package net.pryzma.iris.stareval.element.tree;

import net.pryzma.iris.stareval.element.ExpressionElement;
import net.pryzma.iris.stareval.parser.UnaryOp;

public record UnaryExpressionElement(UnaryOp op, ExpressionElement inner) implements ExpressionElement {


	@Override
	public String toString() {
		return "UnaryExpr{" + this.op + " {" + this.inner + "} }";
	}
}
