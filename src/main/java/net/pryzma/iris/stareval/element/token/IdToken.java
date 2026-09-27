package net.pryzma.iris.stareval.element.token;

import net.pryzma.iris.stareval.element.AccessibleExpressionElement;

public class IdToken extends Token implements AccessibleExpressionElement {
	private final String id;

	public IdToken(String id) {
		this.id = id;
	}

	public String getId() {
		return id;
	}

	@Override
	public String toString() {
		return "Id{" + this.id + "}";
	}
}
