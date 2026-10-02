package net.pryzma.shader.stareval.function;

import net.pryzma.shader.stareval.expression.Expression;

public interface FunctionContext {
	Expression getVariable(String name);

	boolean hasVariable(String name);
}
