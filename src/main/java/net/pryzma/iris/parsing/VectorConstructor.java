package net.pryzma.iris.parsing;

import net.pryzma.iris.stareval.Util;
import net.pryzma.iris.stareval.expression.Expression;
import net.pryzma.iris.stareval.function.AbstractTypedFunction;
import net.pryzma.iris.stareval.function.FunctionContext;
import net.pryzma.iris.stareval.function.FunctionReturn;
import net.pryzma.iris.stareval.function.Type;

import java.util.Arrays;

public class VectorConstructor extends AbstractTypedFunction {

	public VectorConstructor(Type inner, int size) {
		super(
			new VectorType.ArrayVector(inner, size),
			Util.make(new Type[size], params -> Arrays.fill(params, inner))
		);
	}

	@Override
	public VectorType.ArrayVector getReturnType() {
		return (VectorType.ArrayVector) super.getReturnType();
	}

	@Override
	public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn
		functionReturn) {
		VectorType.ArrayVector vectorType = this.getReturnType();
		vectorType.map(params, context, functionReturn, (i, p, ctx, fr) -> p[i].evaluateTo(ctx, fr));
	}
}
