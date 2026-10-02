package net.pryzma.shader.parsing;

import net.pryzma.shader.stareval.expression.Expression;
import net.pryzma.shader.stareval.function.AbstractTypedFunction;
import net.pryzma.shader.stareval.function.B2BFunction;
import net.pryzma.shader.stareval.function.BB2BFunction;
import net.pryzma.shader.stareval.function.F2FFunction;
import net.pryzma.shader.stareval.function.F2IFunction;
import net.pryzma.shader.stareval.function.FF2BFunction;
import net.pryzma.shader.stareval.function.FF2FFunction;
import net.pryzma.shader.stareval.function.FFF2BFunction;
import net.pryzma.shader.stareval.function.FFF2FFunction;
import net.pryzma.shader.stareval.function.FunctionContext;
import net.pryzma.shader.stareval.function.FunctionResolver;
import net.pryzma.shader.stareval.function.FunctionReturn;
import net.pryzma.shader.stareval.function.I2IFunction;
import net.pryzma.shader.stareval.function.II2BFunction;
import net.pryzma.shader.stareval.function.II2IFunction;
import net.pryzma.shader.stareval.function.III2BFunction;
import net.pryzma.shader.stareval.function.III2IFunction;
import net.pryzma.shader.stareval.function.Type;
import net.pryzma.shader.stareval.function.TypedFunction;
import net.pryzma.shader.stareval.function.TypedFunction.Parameter;
import net.pryzma.shader.stareval.function.V2FFunction;
import net.pryzma.shader.stareval.function.V2IFunction;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.Vector4f;
import org.joml.Vector4i;

import java.util.Arrays;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * #   sin(x)
 * #   cos(x)
 * #   asin(x)
 * #   acos(x)
 * #   tan(x)
 * #   atan(x)
 * #   atan2(y, x)
 * #   torad(deg)
 * #   todeg(rad)
 * #   min(x, y ,...)
 * #   max(x, y, ...)
 * #   clamp(x, min, max)                             Limits a value to be between min and max values
 * #   abs(x)
 * #   floor(x)
 * #   ceil(x)
 * #   exp(x)
 * #   frac(x)
 * #   log(x)
 * #   pow(x)
 * #   random()
 * #   round(x)
 * #   signum(x)
 * #   sqrt(x)
 * #   fmod(x, y)                                     Similar to Math.floorMod()
 * #   if(cond, val, [cond2, val2, ...], val_else)    Select a value based one or more conditions
 * #   smooth([id], val, [fadeInTime, [fadeOutTime]]) Smooths a variable with custom fade-in time.
 * #                                                  The "id" must be unique, if not specified it is generated automatically
 * #                                                  Default fade time is 1 sec.
 * <p>
 * # Boolean functions
 * #   between(x, min, max)                           Check if a value is between min and max values
 * #   equals(x, y, epsilon)                          Compare two float values with error margin
 * #   in(x, val1, val2, ...)                         Check if a value equals one of several values
 */
public class ShaderFunctions {
	public static final FunctionResolver functions;
	static final FunctionResolver.Builder builder = new FunctionResolver.Builder();

	static {
		{
			// Unary ops
			{
				// negate
				ShaderFunctions.<I2IFunction>addVectorizable("negate", (a) -> -a);
				ShaderFunctions.<F2FFunction>add("negate", (a) -> -a);

				ShaderFunctions.addUnaryOpJOML("negate", VectorType.VEC2, Vector2f::negate);
				ShaderFunctions.addUnaryOpJOML("negate", VectorType.VEC3, Vector3f::negate);
				ShaderFunctions.addUnaryOpJOML("negate", VectorType.VEC4, Vector4f::negate);
			}
		}
		{
			// binary ops
			{
				// add
				ShaderFunctions.<II2IFunction>addVectorizable("add", Integer::sum);
				ShaderFunctions.<FF2FFunction>add("add", Float::sum);

				ShaderFunctions.addBinaryOpJOML("add", VectorType.VEC2, Vector2f::add);
				ShaderFunctions.addBinaryOpJOML("add", VectorType.VEC3, Vector3f::add);
				ShaderFunctions.addBinaryOpJOML("add", VectorType.VEC4, Vector4f::add);
			}

			{
				// subtract
				ShaderFunctions.<II2IFunction>addVectorizable("subtract", (a, b) -> a - b);
				ShaderFunctions.<FF2FFunction>add("subtract", (a, b) -> a - b);

				ShaderFunctions.addBinaryOpJOML("subtract", VectorType.VEC2, Vector2f::sub);
				ShaderFunctions.addBinaryOpJOML("subtract", VectorType.VEC3, Vector3f::sub);
				ShaderFunctions.addBinaryOpJOML("subtract", VectorType.VEC4, Vector4f::sub);
			}

			{
				// multiply
				ShaderFunctions.<II2IFunction>addVectorizable("multiply", (a, b) -> a * b);
				ShaderFunctions.<FF2FFunction>add("multiply", (a, b) -> a * b);

				ShaderFunctions.addBinaryOpJOML("multiply", VectorType.VEC2, Vector2f::mul);
				ShaderFunctions.addBinaryOpJOML("multiply", VectorType.VEC3, Vector3f::mul);
				ShaderFunctions.addBinaryOpJOML("multiply", VectorType.VEC4, Vector4f::mul);
			}

			{
				// divide
				ShaderFunctions.<FF2FFunction>add("divide", (a, b) -> a / b);

				ShaderFunctions.addBinaryOpJOML("divide", VectorType.VEC2, Vector2f::div);
				ShaderFunctions.addBinaryOpJOML("divide", VectorType.VEC3, Vector3f::div);
				ShaderFunctions.addBinaryOpJOML("divide", VectorType.VEC4, Vector4f::div);
			}

			{
				// remainder
				ShaderFunctions.<II2IFunction>addVectorizable("remainder", (a, b) -> a % b);
				ShaderFunctions.<FF2FFunction>add("remainder", (a, b) -> a % b);

				// ShaderFunctions.addBinaryOpJOML("multiply", VectorType.VEC2, Vector2f::??);
				// ShaderFunctions.addBinaryOpJOML("multiply", VectorType.VEC3, Vector3f::??);
				// ShaderFunctions.addBinaryOpJOML("multiply", VectorType.VEC4, Vector4f::??);
			}
		}
		{
			ShaderFunctions.<II2BFunction>addBooleanVectorizable("equals", (a, b) -> a == b);
			ShaderFunctions.<FF2BFunction>add("equals", (a, b) -> a == b);

			ShaderFunctions.addBinaryToBooleanOpJOML("equal", VectorType.VEC2, false, Vector2f::equals);
			ShaderFunctions.addBinaryToBooleanOpJOML("equal", VectorType.VEC3, false, Vector3f::equals);
			ShaderFunctions.addBinaryToBooleanOpJOML("equal", VectorType.VEC4, false, Vector4f::equals);

			ShaderFunctions.<II2BFunction>addBooleanVectorizable("notEquals", (a, b) -> a != b);
			ShaderFunctions.<FF2BFunction>add("notEquals", (a, b) -> a != b);

			ShaderFunctions.addBinaryToBooleanOpJOML("equal", VectorType.VEC2, true, Vector2f::equals);
			ShaderFunctions.addBinaryToBooleanOpJOML("equal", VectorType.VEC3, true, Vector3f::equals);
			ShaderFunctions.addBinaryToBooleanOpJOML("equal", VectorType.VEC4, true, Vector4f::equals);

			ShaderFunctions.<II2BFunction>add("lessThanOrEquals", (a, b) -> a <= b);
			ShaderFunctions.<FF2BFunction>add("lessThanOrEquals", (a, b) -> a <= b);

			ShaderFunctions.<II2BFunction>add("moreThanOrEquals", (a, b) -> a >= b);
			ShaderFunctions.<FF2BFunction>add("moreThanOrEquals", (a, b) -> a >= b);

			ShaderFunctions.<II2BFunction>add("lessThan", (a, b) -> a < b);
			ShaderFunctions.<FF2BFunction>add("lessThan", (a, b) -> a < b);

			ShaderFunctions.<II2BFunction>add("moreThan", (a, b) -> a > b);
			ShaderFunctions.<FF2BFunction>add("moreThan", (a, b) -> a > b);
		}
		{

			ShaderFunctions.<BB2BFunction>addVectorizable("equals", (a, b) -> a == b);
			ShaderFunctions.<BB2BFunction>addVectorizable("notEquals", (a, b) -> a != b);
			ShaderFunctions.<BB2BFunction>addVectorizable("and", (a, b) -> a && b);
			ShaderFunctions.<BB2BFunction>addVectorizable("or", (a, b) -> a || b);
			ShaderFunctions.<B2BFunction>addVectorizable("not", (a) -> !a);
		}

		{
			// these are also vectorizable in glsl
			// http://learnwebgl.brown37.net/12_shader_language/documents/webgl-reference-card-1_0.pdf
			// page 4

			{
				// Angle & Trigonometry Functions

				// optifine
				ShaderFunctions.<F2FFunction>add("torad", (a) -> (float) Math.toRadians(a));
				ShaderFunctions.<F2FFunction>add("todeg", (a) -> (float) Math.toDegrees(a));

				ShaderFunctions.<F2FFunction>add("radians", (a) -> (float) Math.toRadians(a));
				ShaderFunctions.<F2FFunction>add("degrees", (a) -> (float) Math.toDegrees(a));


				ShaderFunctions.<F2FFunction>add("sin", (a) -> (float) Math.sin(a));
				ShaderFunctions.<F2FFunction>add("cos", (a) -> (float) Math.cos(a));
				ShaderFunctions.<F2FFunction>add("tan", (a) -> (float) Math.tan(a));
				ShaderFunctions.<F2FFunction>add("asin", (a) -> (float) Math.asin(a));
				ShaderFunctions.<F2FFunction>add("acos", (a) -> (float) Math.acos(a));
				ShaderFunctions.<F2FFunction>add("atan", (a) -> (float) Math.atan(a));
				ShaderFunctions.<FF2FFunction>add("atan", (y, x) -> (float) Math.atan2(y, x));
				// optifine
				ShaderFunctions.<FF2FFunction>add("atan2", (y, x) -> (float) Math.atan2(y, x));
			}
			{
				// Exponential Functions
				ShaderFunctions.<FF2FFunction>add("pow", (a, b) -> (float) Math.pow(a, b));
				ShaderFunctions.<F2FFunction>add("exp", (a) -> (float) Math.exp(a));
				ShaderFunctions.<F2FFunction>add("log", (a) -> (float) Math.log(a));
				// java does not have built ins: https://bugs.java.com/bugdatabase/view_bug.do?bug_id=4851627
				ShaderFunctions.<F2FFunction>add("exp2", (a) -> (float) Math.pow(2, a));
				ShaderFunctions.<F2FFunction>add("log2", (a) -> (float) (Math.log(a) / Math.log(2)));

				ShaderFunctions.<F2FFunction>add("sqrt", (a) -> (float) Math.sqrt(a));
				// ShaderFunctions.<F2FFunction>addVectorizable("inversesqrt", (a) -> (float) Math.(a));

				// optifine
				ShaderFunctions.<F2FFunction>add("log10", (a) -> (float) Math.log10(a));

				// TODO the base may be static so doing `log(2, x)` would be slower than `log(x)/log(2)`
				ShaderFunctions.<FF2FFunction>add("log",
					(base, value) -> (float) (Math.log(value) / Math.log(base)));

				// cause I want consistency
				ShaderFunctions.<F2FFunction>add("exp10", (a) -> (float) Math.pow(10, a));

			}

			{
				// Common Functions
				ShaderFunctions.<I2IFunction>addVectorizable("abs", Math::abs);
				ShaderFunctions.<F2FFunction>add("abs", Math::abs);

				ShaderFunctions.addUnaryOpJOML("abs", VectorType.VEC2, Vector2f::absolute);
				ShaderFunctions.addUnaryOpJOML("abs", VectorType.VEC3, Vector3f::absolute);
				ShaderFunctions.addUnaryOpJOML("abs", VectorType.VEC4, Vector4f::absolute);


				ShaderFunctions.<F2FFunction>add("sign", Math::signum);
				// ShaderFunctions.addUnaryOpJOML("abs", VectorType.VEC2, Vector2f::??);
				// ShaderFunctions.addUnaryOpJOML("abs", VectorType.VEC3, Vector3f::??);
				// ShaderFunctions.addUnaryOpJOML("abs", VectorType.VEC4, Vector4f::??);

				// optifine
				ShaderFunctions.<F2FFunction>add("signum", Math::signum);
				// ShaderFunctions.addUnaryOpJOML("abs", VectorType.VEC2, Vector2f::??);
				// ShaderFunctions.addUnaryOpJOML("abs", VectorType.VEC3, Vector3f::??);
				// ShaderFunctions.addUnaryOpJOML("abs", VectorType.VEC4, Vector4f::??);

				// because my type checker can handle (float) -> float and (float) -> int,
				// floor doesn't require a cast to int, but does not cause issues if the float is too big and
				// casting to float and back would change the result

				ShaderFunctions.<F2FFunction>add("floor", (a) -> (float) Math.floor(a));
				ShaderFunctions.<F2IFunction>add("floor", (a) -> (int) Math.floor(a));

				ShaderFunctions.addUnaryOpJOML("floor", VectorType.VEC2, Vector2f::floor);
				ShaderFunctions.addUnaryOpJOML("floor", VectorType.VEC3, Vector3f::floor);
				ShaderFunctions.addUnaryOpJOML("floor", VectorType.VEC4, Vector4f::floor);

				ShaderFunctions.<F2FFunction>add("ceil", (a) -> (float) Math.ceil(a));
				ShaderFunctions.<F2IFunction>add("ceil", (a) -> (int) Math.ceil(a));

				ShaderFunctions.addUnaryOpJOML("ceil", VectorType.VEC2, Vector2f::ceil);
				ShaderFunctions.addUnaryOpJOML("ceil", VectorType.VEC3, Vector3f::ceil);
				ShaderFunctions.addUnaryOpJOML("ceil", VectorType.VEC4, Vector4f::ceil);

				ShaderFunctions.<F2FFunction>add("frac", (a) -> (float) (a - Math.floor(a)));

				// ShaderFunctions.addUnaryOpJOML("frac", VectorType.VEC2, Vector2f::??);
				// ShaderFunctions.addUnaryOpJOML("frac", VectorType.VEC3, Vector3f::??);
				// ShaderFunctions.addUnaryOpJOML("frac", VectorType.VEC4, Vector4f::??);

				// optifine
				// TODO: why does Math.round give an int?
				// ShaderFunctions.<F2FFunction>addVectorizable("round", (a) -> (float) Math.round(a));
				// TODO maybe add round with a specifyable precission?
				// ShaderFunctions.<F2IFunction>addVectorizable("round", (a) -> (int) Math.round(a));


				// mod is also already an operator

				// TODO: min and max require vararg for optifine compat
				// TODO: glsl has vecn min(vecn a, float b)
				ShaderFunctions.<II2IFunction>addVectorizable("min", Math::min);
				ShaderFunctions.<FF2FFunction>add("min", Math::min);

				ShaderFunctions.addBinaryOpJOML("min", VectorType.VEC2, Vector2f::min);
				ShaderFunctions.addBinaryOpJOML("min", VectorType.VEC3, Vector3f::min);
				ShaderFunctions.addBinaryOpJOML("min", VectorType.VEC4, Vector4f::min);

				ShaderFunctions.<II2IFunction>addVectorizable("max", Math::max);
				ShaderFunctions.<FF2FFunction>add("max", Math::max);

				ShaderFunctions.addBinaryOpJOML("max", VectorType.VEC2, Vector2f::max);
				ShaderFunctions.addBinaryOpJOML("max", VectorType.VEC3, Vector3f::max);
				ShaderFunctions.addBinaryOpJOML("max", VectorType.VEC4, Vector4f::max);

				{
					// Fake vararg
					for (int length = 3; length <= 16; length++) {
						{
							// min float
							Type[] inputs = new Type[length];
							Arrays.fill(inputs, Type.Float);
							ShaderFunctions.add("min", new AbstractTypedFunction(Type.Float, inputs) {
								@Override
								public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
									params[0].evaluateTo(context, functionReturn);
									float min = functionReturn.floatReturn;
									for (int i = 1; i < params.length; i++) {
										params[1].evaluateTo(context, functionReturn);
										min = Math.min(min, functionReturn.floatReturn);
									}
									functionReturn.floatReturn = min;
								}
							});
						}
						{
							// max float
							Type[] inputs = new Type[length];
							Arrays.fill(inputs, Type.Float);
							ShaderFunctions.add("max", new AbstractTypedFunction(Type.Float, inputs) {
								@Override
								public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
									params[0].evaluateTo(context, functionReturn);
									float max = functionReturn.floatReturn;
									for (int i = 1; i < params.length; i++) {
										params[1].evaluateTo(context, functionReturn);
										max = Math.max(max, functionReturn.floatReturn);
									}
									functionReturn.floatReturn = max;
								}
							});
						}
						{
							// min int
							Type[] inputs = new Type[length];
							Arrays.fill(inputs, Type.Int);
							ShaderFunctions.addVectorizable("min", new AbstractTypedFunction(Type.Int, inputs) {
								@Override
								public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
									params[0].evaluateTo(context, functionReturn);
									int min = functionReturn.intReturn;
									for (int i = 1; i < params.length; i++) {
										params[1].evaluateTo(context, functionReturn);
										min = Math.min(min, functionReturn.intReturn);
									}
									functionReturn.intReturn = min;
								}
							});
						}
						{
							// max int
							Type[] inputs = new Type[length];
							Arrays.fill(inputs, Type.Int);
							ShaderFunctions.addVectorizable("max", new AbstractTypedFunction(Type.Int, inputs) {
								@Override
								public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
									params[0].evaluateTo(context, functionReturn);
									int max = functionReturn.intReturn;
									for (int i = 1; i < params.length; i++) {
										params[1].evaluateTo(context, functionReturn);
										max = Math.max(max, functionReturn.intReturn);
									}
									functionReturn.intReturn = max;
								}
							});
						}
					}
				}

				// if max < min => undefined behaviour
				// TODO: glsl has vecn mix(vecn x, float min, float max)
				ShaderFunctions.<III2IFunction>addVectorizable("clamp",
					(val, min, max) -> Math.max(min, Math.min(max, val)));
				ShaderFunctions.<FFF2FFunction>add("clamp",
					(val, min, max) -> Math.max(min, Math.min(max, val)));
				ShaderFunctions.addTernaryOpJOML("clamp", VectorType.VEC2, (val, min, max, dest) -> {
					val.min(max, dest);
					dest.max(min);
				});
				ShaderFunctions.addTernaryOpJOML("clamp", VectorType.VEC3, (val, min, max, dest) -> {
					val.min(max, dest);
					dest.max(min);
				});
				ShaderFunctions.addTernaryOpJOML("clamp", VectorType.VEC4, (val, min, max, dest) -> {
					val.min(max, dest);
					dest.max(min);
				});

				// TODO: glsl has vecn mix(vecn x, vecn y, float a)
				ShaderFunctions.<FFF2FFunction>add("mix", (x, y, a) -> x + (y - x) * a);
				// TODO flaot vector lerp

				// TODO: glsl has vecn step(float edge, vecn x)
				ShaderFunctions.<II2IFunction>addVectorizable("edge", (edge, x) -> (x < edge) ? 0 : 1);
				ShaderFunctions.<FF2FFunction>add("edge", (edge, x) -> (x < edge) ? 0 : 1);
				// TODO float vector step
				// TODO: smooth step
			}

			{
				// Geometric Functions
				// TODO: Geometric Functions
			}

			{
				// Matrix Functions
				// TODO: Add matrices
			}

			{
				// Vector Relational Functions
				// TODO: These might clash with the operators
				// Although we can have multiple return values so
			}


			{
				{
					// fmod
					ShaderFunctions.<II2IFunction>addVectorizable("fmod", Math::floorMod);
					ShaderFunctions.<FF2FFunction>add("fmod", (a, b) -> (a % b + b) % b);
				}
				{
					Random random = new Random();
					// randomInt(), randomInt(int bound), randomInt(int inclusiveMin, int exclusiveMax)
					ShaderFunctions.<V2IFunction>addVectorizable("randomInt", random::nextInt);
					ShaderFunctions.<I2IFunction>addVectorizable("randomInt", random::nextInt);
					ShaderFunctions.<II2IFunction>addVectorizable("randomInt", (a, b) -> random.nextInt(b - a) + a);

					// random, random(float min, float max)
					ShaderFunctions.<V2FFunction>add("random", random::nextFloat);
					ShaderFunctions.<FF2FFunction>add("random", (min, max) ->
						min + random.nextFloat() * (max - min));
				}
				{
					// IF
					// if(boolean, primitive, primitive) -> primitive
					// if(boolean, xvec, xvec) -> xvec
					// TODO: REDO: if(bvec, xvec, xvec) -> xvec
					// TODO: optifine requires vararg
					for (Type.Primitive type : Type.AllPrimitives) {
						add("if", new AbstractTypedFunction(type, new Type[]{Type.Boolean, type, type}) {
							@Override
							public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
								params[0].evaluateTo(context, functionReturn);

								params[
									functionReturn.booleanReturn ? 1 : 2
									].evaluateTo(context, functionReturn);

							}
						});
					}

					for (Type type : VectorType.AllVectorTypes) {
						add("if", new AbstractTypedFunction(type, new Type[]{Type.Boolean, type, type}) {
							@Override
							public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
								params[0].evaluateTo(context, functionReturn);

								params[
									functionReturn.booleanReturn ? 1 : 2
									].evaluateTo(context, functionReturn);

							}
						});
					}

					{
						// FAKE vararg
						for (int length = 2; length <= 16; length++) {
							for (Type.Primitive type : Type.AllPrimitives) {
								Type[] params = new Type[length * 2 + 1];
								for (int i = 0; i < length * 2; i += 2) {
									params[i] = Type.Boolean;
									params[i + 1] = type;
								}
								params[length * 2] = type;
								int finalLength = length * 2;
								add("if", new AbstractTypedFunction(type, params) {
									@Override
									public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
										for (int i = 0; i < finalLength; i += 2) {
											params[i].evaluateTo(context, functionReturn);
											if (functionReturn.booleanReturn) {
												params[i + 1].evaluateTo(context, functionReturn);
												return;
											}
											params[finalLength].evaluateTo(context, functionReturn);
										}
									}
								});
							}
						}
					}
				}
				{
					// smooth

					// smooth(target)
					builder.addDynamicFunction("smooth", Type.Float, () ->
						new AbstractTypedFunction(
							Type.Float,
							new Parameter[]{
								new Parameter(Type.Float, false), // target
							},
							0,
							false
						) {
							private final SmoothFloat smoothFloat = new SmoothFloat();

							@Override
							public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
								params[0].evaluateTo(context, functionReturn);
								float target = functionReturn.floatReturn;
								functionReturn.floatReturn = smoothFloat.updateAndGet(
									target,
									1,
									1
								);
							}
						});

					// smooth(id, target)
					builder.addDynamicFunction("smooth", Type.Float, () ->
						new AbstractTypedFunction(
							Type.Float,
							new Parameter[]{
								new Parameter(Type.Float, true), // id for backward compat with optifine
								new Parameter(Type.Float, false), // target
							},
							1,
							false
						) {
							private final SmoothFloat smoothFloat = new SmoothFloat();

							@Override
							public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
								params[1].evaluateTo(context, functionReturn);
								float target = functionReturn.floatReturn;
								functionReturn.floatReturn = smoothFloat.updateAndGet(
									target,
									1,
									1
								);
							}
						});

					// smooth(target, fadeTime)
					builder.addDynamicFunction("smooth", Type.Float, () ->
						new AbstractTypedFunction(
							Type.Float,
							new Parameter[]{
								new Parameter(Type.Float, false), // target
								new Parameter(Type.Float, false), // fadeTime
							},
							0,
							false
						) {
							private final SmoothFloat smoothFloat = new SmoothFloat();

							@Override
							public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
								params[0].evaluateTo(context, functionReturn);
								float target = functionReturn.floatReturn;

								params[1].evaluateTo(context, functionReturn);
								float fadeTime = functionReturn.floatReturn;

								functionReturn.floatReturn = smoothFloat.updateAndGet(
									target,
									fadeTime,
									fadeTime
								);
							}
						});

					// smooth(id, target, fadeTime)
					builder.addDynamicFunction("smooth", Type.Float, () ->
						new AbstractTypedFunction(
							Type.Float,
							new Parameter[]{
								new Parameter(Type.Float, true), // id for backward compat with optifine
								new Parameter(Type.Float, false), // target
								new Parameter(Type.Float, false), // fadeTime
							},
							1,
							false
						) {
							private final SmoothFloat smoothFloat = new SmoothFloat();

							@Override
							public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
								params[1].evaluateTo(context, functionReturn);
								float target = functionReturn.floatReturn;

								params[2].evaluateTo(context, functionReturn);
								float fadeTime = functionReturn.floatReturn;

								functionReturn.floatReturn = smoothFloat.updateAndGet(
									target,
									fadeTime,
									fadeTime
								);
							}
						});

					// smooth(target, fadeUpTime, fadeDownTime)
					builder.addDynamicFunction("smooth", Type.Float, () ->
						new AbstractTypedFunction(
							Type.Float,
							new Parameter[]{
								new Parameter(Type.Float, false), // target
								new Parameter(Type.Float, false), // fadeUpTime
								new Parameter(Type.Float, false), // fadeDownTime
							},
							0,
							false
						) {
							private final SmoothFloat smoothFloat = new SmoothFloat();

							@Override
							public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
								params[0].evaluateTo(context, functionReturn);
								float target = functionReturn.floatReturn;

								params[1].evaluateTo(context, functionReturn);
								float fadeUpTime = functionReturn.floatReturn;
								params[2].evaluateTo(context, functionReturn);
								float fadeDownTime = functionReturn.floatReturn;

								functionReturn.floatReturn = smoothFloat.updateAndGet(
									target,
									fadeUpTime,
									fadeDownTime
								);
							}
						});

					// smooth(id, target, fadeUpTime, fadeDownTime)
					builder.addDynamicFunction("smooth", Type.Float, () ->
						new AbstractTypedFunction(
							Type.Float,
							new Parameter[]{
								new Parameter(Type.Float, true), // id for backward compat with optifine
								new Parameter(Type.Float, false), // target
								new Parameter(Type.Float, false), // fadeUpTime
								new Parameter(Type.Float, false), // fadeDownTime
							},
							1,
							false
						) {
							private final SmoothFloat smoothFloat = new SmoothFloat();

							@Override
							public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
								params[1].evaluateTo(context, functionReturn);
								float target = functionReturn.floatReturn;

								params[2].evaluateTo(context, functionReturn);
								float fadeUpTime = functionReturn.floatReturn;
								params[3].evaluateTo(context, functionReturn);
								float fadeDownTime = functionReturn.floatReturn;

								functionReturn.floatReturn = smoothFloat.updateAndGet(
									target,
									fadeUpTime,
									fadeDownTime
								);
							}
						});
				}
			}
		}

		// casts
		{
			addImplicitCast(Type.Int, Type.Float, r -> r.floatReturn = r.intReturn);
			// this is actually done by round i think
			addExplicitCast(Type.Float, Type.Int, r -> r.intReturn = (int) r.floatReturn);
		}

		// boolean functions
		{
			ShaderFunctions.<III2BFunction>add("between", (a, min, max) -> a >= min && a <= max);
			ShaderFunctions.<FFF2BFunction>add("between", (a, min, max) -> a >= min && a <= max);

			ShaderFunctions.<FFF2BFunction>add("equals", (a, b, epsilon) -> Math.abs(a - b) <= epsilon);

			// TODO: varargs
			// TODO also for other types
			{
				// FAKE vararg
				for (int length = 2; length <= 32; length++) {
					Type[] params = new Type[length];
					Arrays.fill(params, Type.Float);
					int finalLength = length;
					ShaderFunctions.add("in", new AbstractTypedFunction(
						Type.Boolean,
						params
					) {
						@Override
						public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
							params[0].evaluateTo(context, functionReturn);
							float value = functionReturn.floatReturn;
							for (int i = 1; i < finalLength; i++) {
								params[i].evaluateTo(context, functionReturn);
								if (functionReturn.floatReturn == value) {
									functionReturn.booleanReturn = true;
									return;
								}
							}
							functionReturn.booleanReturn = false;
						}
					});
				}
			}
		}

		// create vectors
		{
			for (Type.Primitive type : new Type.Primitive[]{Type.Boolean, Type.Int}) {
				for (int size = 2; size <= 4; size++) {
					TypedFunction function = new VectorConstructor(type, size);
					// TODO make it possible to do `vec3(vec2(0),0)`
					add(
						Character.toLowerCase(
							type.getClass().getSimpleName().charAt(0)
						) + "vec" + size, function);
				}
			}

			add("vec2", new AbstractTypedFunction(
				VectorType.VEC2,
				new Type[]{Type.Float, Type.Float}
			) {
				@Override
				public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
					params[0].evaluateTo(context, functionReturn);
					float x = functionReturn.floatReturn;

					params[1].evaluateTo(context, functionReturn);
					float y = functionReturn.floatReturn;

					// TODO: this can't be cached atm. If we swap this to a function provider we could
					functionReturn.objectReturn = new Vector2f(x, y);
				}
			});

			add("vec3", new AbstractTypedFunction(
				VectorType.VEC3,
				new Type[]{Type.Float, Type.Float, Type.Float}
			) {
				@Override
				public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
					params[0].evaluateTo(context, functionReturn);
					float x = functionReturn.floatReturn;

					params[1].evaluateTo(context, functionReturn);
					float y = functionReturn.floatReturn;

					params[2].evaluateTo(context, functionReturn);
					float z = functionReturn.floatReturn;

					// TODO: this can't be cached atm. If we swap this to a function provider we could
					functionReturn.objectReturn = new Vector3f(x, y, z);
				}
			});

			add("vec4", new AbstractTypedFunction(
				VectorType.VEC4,
				new Type[]{Type.Float, Type.Float, Type.Float, Type.Float}
			) {
				@Override
				public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
					params[0].evaluateTo(context, functionReturn);
					float x = functionReturn.floatReturn;

					params[1].evaluateTo(context, functionReturn);
					float y = functionReturn.floatReturn;

					params[2].evaluateTo(context, functionReturn);
					float z = functionReturn.floatReturn;

					params[3].evaluateTo(context, functionReturn);
					float w = functionReturn.floatReturn;

					// TODO: this can't be cached atm. If we swap this to a function provider we could
					functionReturn.objectReturn = new Vector4f(x, y, z, w);
				}
			});
		}

		// accessors
		{
			// is this the best way to do these?
			String[][] accessNames = new String[][]{
				new String[]{"0", "r", "x", "s"},
				new String[]{"1", "g", "y", "t"},
				new String[]{"2", "b", "z", "p"},
				new String[]{"3", "a", "w", "q"}
			};

			{
				// access$0
				for (String access : accessNames[0]) {
					ShaderFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
						Type.Float,
						new Type[]{VectorType.VEC2}
					) {
						@Override
						public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
							params[0].evaluateTo(context, functionReturn);
							functionReturn.floatReturn = ((Vector2f) functionReturn.objectReturn).x;
						}
					});

					ShaderFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
						Type.Int,
						new Type[]{VectorType.I_VEC2}
					) {
						@Override
						public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
							params[0].evaluateTo(context, functionReturn);
							functionReturn.intReturn = ((Vector2i) functionReturn.objectReturn).x;
						}
					});

					ShaderFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
						Type.Float,
						new Type[]{VectorType.VEC3}
					) {
						@Override
						public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
							params[0].evaluateTo(context, functionReturn);
							functionReturn.floatReturn = ((Vector3f) functionReturn.objectReturn).x;
						}
					});

					ShaderFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
						Type.Int,
						new Type[]{VectorType.I_VEC3}
					) {
						@Override
						public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
							params[0].evaluateTo(context, functionReturn);
							functionReturn.intReturn = ((Vector3i) functionReturn.objectReturn).x;
						}
					});

					ShaderFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
						Type.Float,
						new Type[]{VectorType.VEC4}
					) {
						@Override
						public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
							params[0].evaluateTo(context, functionReturn);
							functionReturn.floatReturn = ((Vector4f) functionReturn.objectReturn).x;
						}
					});

					ShaderFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
						Type.Int,
						new Type[]{VectorType.I_VEC4}
					) {
						@Override
						public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
							params[0].evaluateTo(context, functionReturn);
							functionReturn.intReturn = ((Vector4i) functionReturn.objectReturn).x;
						}
					});
				}
			}

			{
				// access$1
				for (String access : accessNames[1]) {
					ShaderFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
						Type.Float,
						new Type[]{VectorType.VEC2}
					) {
						@Override
						public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
							params[0].evaluateTo(context, functionReturn);
							functionReturn.floatReturn = ((Vector2f) functionReturn.objectReturn).y;
						}
					});

					ShaderFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
						Type.Int,
						new Type[]{VectorType.I_VEC2}
					) {
						@Override
						public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
							params[0].evaluateTo(context, functionReturn);
							functionReturn.intReturn = ((Vector2i) functionReturn.objectReturn).y;
						}
					});

					ShaderFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
						Type.Float,
						new Type[]{VectorType.VEC3}
					) {
						@Override
						public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
							params[0].evaluateTo(context, functionReturn);
							functionReturn.floatReturn = ((Vector3f) functionReturn.objectReturn).y;
						}
					});

					ShaderFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
						Type.Int,
						new Type[]{VectorType.I_VEC3}
					) {
						@Override
						public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
							params[0].evaluateTo(context, functionReturn);
							functionReturn.intReturn = ((Vector3i) functionReturn.objectReturn).y;
						}
					});

					ShaderFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
						Type.Float,
						new Type[]{VectorType.VEC4}
					) {
						@Override
						public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
							params[0].evaluateTo(context, functionReturn);
							functionReturn.floatReturn = ((Vector4f) functionReturn.objectReturn).y;
						}
					});

					ShaderFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
						Type.Int,
						new Type[]{VectorType.I_VEC4}
					) {
						@Override
						public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
							params[0].evaluateTo(context, functionReturn);
							functionReturn.intReturn = ((Vector4i) functionReturn.objectReturn).y;
						}
					});
				}
			}

			{
				// access$2
				for (String access : accessNames[2]) {
					ShaderFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
						Type.Float,
						new Type[]{VectorType.VEC3}
					) {
						@Override
						public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
							params[0].evaluateTo(context, functionReturn);
							functionReturn.floatReturn = ((Vector3f) functionReturn.objectReturn).z;
						}
					});

					ShaderFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
						Type.Int,
						new Type[]{VectorType.I_VEC3}
					) {
						@Override
						public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
							params[0].evaluateTo(context, functionReturn);
							functionReturn.intReturn = ((Vector3i) functionReturn.objectReturn).z;
						}
					});

					ShaderFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
						Type.Float,
						new Type[]{VectorType.VEC4}
					) {
						@Override
						public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
							params[0].evaluateTo(context, functionReturn);
							functionReturn.floatReturn = ((Vector4f) functionReturn.objectReturn).z;
						}
					});

					ShaderFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
						Type.Int,
						new Type[]{VectorType.I_VEC4}
					) {
						@Override
						public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
							params[0].evaluateTo(context, functionReturn);
							functionReturn.intReturn = ((Vector4i) functionReturn.objectReturn).z;
						}
					});
				}
			}

			{
				// access$3
				for (String access : accessNames[3]) {
					ShaderFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
						Type.Float,
						new Type[]{VectorType.VEC4}
					) {
						@Override
						public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
							params[0].evaluateTo(context, functionReturn);
							functionReturn.floatReturn = ((Vector4f) functionReturn.objectReturn).w;
						}
					});

					ShaderFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
						Type.Int,
						new Type[]{VectorType.I_VEC4}
					) {
						@Override
						public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
							params[0].evaluateTo(context, functionReturn);
							functionReturn.intReturn = ((Vector4i) functionReturn.objectReturn).w;
						}
					});
				}
			}

			{
				// matrix access
				for (int i = 0; i < 4; i++) {
					for (String access : accessNames[i]) {
						int finalI = i;
						ShaderFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
							VectorType.VEC4,
							new Type[]{MatrixType.MAT4}
						) {
							@Override
							public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
								params[0].evaluateTo(context, functionReturn);
								functionReturn.objectReturn = ((Matrix4f) functionReturn.objectReturn).getColumn(finalI, new Vector4f());
							}
						});
					}
				}
			}
		}

		functions = builder.build();
	}

	static <T extends TypedFunction> void addVectorized(String name, T function) {
		if (function.getReturnType() instanceof Type.Primitive) {
			add(name, new VectorizedFunction(function, 2));
			add(name, new VectorizedFunction(function, 3));
			add(name, new VectorizedFunction(function, 4));
		} else {
			throw new IllegalArgumentException(name + " is not vectorizable");
		}
	}

	static <T extends TypedFunction> void addVectorizable(String name, T function) {
		add(name, function);
		addVectorized(name, function);
	}

	static <T extends TypedFunction> void addBooleanVectorizable(String name, T function) {
		assert function.getReturnType().equals(Type.Boolean);
		add(name, function);
		if (function.getReturnType() instanceof Type.Primitive) {
			add(name, new BooleanVectorizedFunction(function, 2));
			add(name, new BooleanVectorizedFunction(function, 3));
			add(name, new BooleanVectorizedFunction(function, 4));
		} else {
			throw new IllegalArgumentException(name + " is not vectorizable");
		}
	}

	static <T> void addUnaryOpJOML(String name, VectorType.JOMLVector<T> type, BiConsumer<T, T> function) {
		builder.add(name, new AbstractTypedFunction(
			type,
			new Type[]{type}
		) {
			final private T vector = type.create();

			@SuppressWarnings("unchecked")
			@Override
			public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
				params[0].evaluateTo(context, functionReturn);
				T a = (T) functionReturn.objectReturn;

				function.accept(a, this.vector);
				functionReturn.objectReturn = this.vector;
			}
		});
	}

	static <T> void addBinaryOpJOML(String name, VectorType.JOMLVector<T> type, TriConsumer<T, T, T> function) {
		builder.add(name, new AbstractTypedFunction(
			type,
			new Type[]{type, type}
		) {
			final private T vector = type.create();

			@SuppressWarnings("unchecked")
			@Override
			public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
				params[0].evaluateTo(context, functionReturn);
				T a = (T) functionReturn.objectReturn;

				params[1].evaluateTo(context, functionReturn);
				T b = (T) functionReturn.objectReturn;

				function.accept(a, b, this.vector);
				functionReturn.objectReturn = this.vector;
			}
		});
	}

	static <T> void addTernaryOpJOML(String name, VectorType.JOMLVector<T> type, QuadConsumer<T, T, T, T> function) {
		builder.add(name, new AbstractTypedFunction(
			type,
			new Type[]{type, type, type}
		) {
			final private T vector = type.create();

			@SuppressWarnings("unchecked")
			@Override
			public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
				params[0].evaluateTo(context, functionReturn);
				T a = (T) functionReturn.objectReturn;

				params[1].evaluateTo(context, functionReturn);
				T b = (T) functionReturn.objectReturn;

				params[2].evaluateTo(context, functionReturn);
				T c = (T) functionReturn.objectReturn;

				function.accept(a, b, c, this.vector);
				functionReturn.objectReturn = this.vector;
			}
		});
	}

	static <T> void addBinaryToBooleanOpJOML(
		String name,
		VectorType.JOMLVector<T> type,
		boolean inverted,
		ObjectObject2BooleanFunction<T, T> function) {
		builder.add(name, new AbstractTypedFunction(
			type,
			new Type[]{type, type}
		) {
			@SuppressWarnings("unchecked")
			@Override
			public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
				params[0].evaluateTo(context, functionReturn);
				T a = (T) functionReturn.objectReturn;

				params[1].evaluateTo(context, functionReturn);
				T b = (T) functionReturn.objectReturn;

				functionReturn.objectReturn = function.apply(a, b) != inverted;
			}
		});
	}

	static <T extends TypedFunction> void add(String name, T function) {
		builder.add(name, function);
	}

	static void addCast(final String name, final Type from, final Type to, final Consumer<FunctionReturn> function) {
		add(name, new TypedFunction() {
			@Override
			public Type getReturnType() {
				return to;
			}

			@Override
			public Parameter[] getParameters() {
				return new Parameter[]{new Parameter(from)};
			}

			@Override
			public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
				params[0].evaluateTo(context, functionReturn);
				function.accept(functionReturn);
			}
		});
	}

	static void addImplicitCast(final Type from, final Type to, final Consumer<FunctionReturn> function) {
		addCast("<cast>", from, to, function);
		addExplicitCast(from, to, function);
	}

	static void addExplicitCast(final Type from, final Type to, final Consumer<FunctionReturn> function) {
		addCast("to" + to.getClass().getSimpleName(), from, to, function);
	}

	public static void main(String[] args) {
		functions.logAllFunctions();
	}

	interface ObjectObject2BooleanFunction<T, U> {
		boolean apply(T t, U u);
	}

	interface TriConsumer<T, U, V> {
		void accept(T t, U u, V v);
	}

	interface QuadConsumer<T, U, V, W> {
		void accept(T t, U u, V v, W w);
	}
}

