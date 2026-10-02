package net.pryzma.shader.stareval.expression;

import java.util.Collection;

public interface VariableExpression extends Expression {
	@Override
	default void listVariables(Collection<? super VariableExpression> variables) {
		variables.add(this);
	}
}
