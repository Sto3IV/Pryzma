package net.pryzma.iris.stareval.element.tree;

import net.pryzma.iris.stareval.element.ExpressionElement;
import net.pryzma.iris.stareval.parser.BinaryOp;

public record BinaryExpressionElement(BinaryOp op, ExpressionElement left,
									  ExpressionElement right) implements ExpressionElement {


	@Override
	public String toString() {
		return "BinaryExpr{ {" + this.left + "} " + this.op + " {" + this.right + "} }";
	}
}
