package net.pryzma.shader.stareval.element.tree;

import net.pryzma.shader.stareval.element.ExpressionElement;
import net.pryzma.shader.stareval.parser.BinaryOp;

public record BinaryExpressionElement(BinaryOp op, ExpressionElement left,
									  ExpressionElement right) implements ExpressionElement {


	@Override
	public String toString() {
		return "BinaryExpr{ {" + this.left + "} " + this.op + " {" + this.right + "} }";
	}
}
