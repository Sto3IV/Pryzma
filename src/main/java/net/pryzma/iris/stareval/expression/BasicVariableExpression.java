package net.pryzma.iris.stareval.expression;

import net.pryzma.iris.stareval.function.FunctionContext;
import net.pryzma.iris.stareval.function.FunctionReturn;
import net.pryzma.iris.stareval.function.Type;

public class BasicVariableExpression implements VariableExpression {
	final private String name;
	final private Type type;

	public BasicVariableExpression(String name, Type type) {
		this.name = name;
		this.type = type;
	}

	@Override
	public void evaluateTo(FunctionContext c, FunctionReturn r) {
		c.getVariable(name).evaluateTo(c, r);
	}

	@Override
	public Expression partialEval(FunctionContext context, FunctionReturn functionReturn) {
		if (context.hasVariable(this.name)) {
			context.getVariable(this.name).evaluateTo(context, functionReturn);
			return type.createConstant(functionReturn);
		} else {
			return this;
		}
	}

}
