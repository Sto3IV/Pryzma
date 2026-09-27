package net.pryzma.iris.stareval.element.tree;

import net.pryzma.iris.stareval.element.AccessibleExpressionElement;

public record AccessExpressionElement(AccessibleExpressionElement base,
									  String index) implements AccessibleExpressionElement {


	@Override
	public String toString() {
		return "Access{" + this.base + "[" + this.index + "]}";
	}
}
