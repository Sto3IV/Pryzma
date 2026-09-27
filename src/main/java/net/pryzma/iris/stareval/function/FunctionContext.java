package net.pryzma.iris.stareval.function;

import net.pryzma.iris.stareval.expression.Expression;

public interface FunctionContext {
	Expression getVariable(String name);

	boolean hasVariable(String name);
}
