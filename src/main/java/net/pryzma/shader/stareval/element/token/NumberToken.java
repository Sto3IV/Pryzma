package net.pryzma.shader.stareval.element.token;

import net.pryzma.shader.stareval.element.ExpressionElement;

public class NumberToken extends Token implements ExpressionElement {
	private final String number;

	public NumberToken(String number) {
		this.number = number;
	}

	public String getNumber() {
		return number;
	}

	@Override
	public String toString() {
		return "Number{" + this.number + "}";
	}
}
