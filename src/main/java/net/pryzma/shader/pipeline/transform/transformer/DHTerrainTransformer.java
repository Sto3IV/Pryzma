package net.pryzma.shader.pipeline.transform.transformer;

import io.github.douira.glsl_transformer.ast.node.TranslationUnit;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.StorageQualifier;
import io.github.douira.glsl_transformer.ast.query.Root;
import io.github.douira.glsl_transformer.ast.transform.ASTInjectionPoint;
import io.github.douira.glsl_transformer.ast.transform.ASTParser;
import io.github.douira.glsl_transformer.util.Type;
import net.pryzma.shader.gl.shader.ShaderType;
import net.pryzma.shader.pipeline.transform.parameter.Parameters;

import static net.pryzma.shader.pipeline.transform.transformer.CommonTransformer.addIfNotExists;

public class DHTerrainTransformer {
	public static void transform(
		ASTParser t,
		TranslationUnit tree,
		Root root, Parameters parameters) {
		CommonTransformer.transform(t, tree, root, parameters, false);


		root.replaceExpressionMatches(t, CommonTransformer.glTextureMatrix0, "mat4(1.0)");
		root.replaceExpressionMatches(t, CommonTransformer.glTextureMatrix1, "mat4(1.0)");
		root.rename("gl_ProjectionMatrix", "iris_ProjectionMatrix");

		if (parameters.type.glShaderType == ShaderType.VERTEX) {
			// Alias of gl_MultiTexCoord1 on 1.15+ for OptiFine
			// See upstream issue #1149
			root.rename("gl_MultiTexCoord2", "gl_MultiTexCoord1");

			root.replaceReferenceExpressions(t, "gl_MultiTexCoord0",
				"vec4(0.0, 0.0, 0.0, 1.0)");

			root.replaceReferenceExpressions(t, "gl_MultiTexCoord1",
				"vec4(_vert_tex_light_coord, 0.0, 1.0)");


			// gl_MultiTexCoord0 and gl_MultiTexCoord1 are the only valid inputs (with
			// gl_MultiTexCoord2 and gl_MultiTexCoord3 as aliases), other texture
			// coordinates are not valid inputs.
			CommonTransformer.replaceGlMultiTexCoordBounded(t, root, 4, 7);
		}

		root.rename("gl_Color", "_vert_color");

		if (parameters.type.glShaderType == ShaderType.VERTEX) {
			root.replaceReferenceExpressions(t, "gl_Normal", "_vert_normal");

		}

		// TODO: Should probably add the normal matrix as a proper uniform that's
		// computed on the CPU-side of things
		root.replaceReferenceExpressions(t, "gl_NormalMatrix",
			"iris_NormalMatrix");
		tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS,
			"uniform mat3 iris_NormalMatrix;");

		tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS,
			"uniform mat4 iris_ModelViewMatrixInverse;");

		tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS,
			"uniform mat4 iris_ProjectionMatrixInverse;");

		// TODO: All of the transformed variants of the input matrices, preferably
		// computed on the CPU side...
		root.rename("gl_ModelViewMatrix", "iris_ModelViewMatrix");
		root.rename("gl_ModelViewMatrixInverse", "iris_ModelViewMatrixInverse");
		root.rename("gl_ProjectionMatrixInverse", "iris_ProjectionMatrixInverse");

		if (parameters.type.glShaderType == ShaderType.VERTEX) {
			// TODO: Vaporwave-Shaderpack expects that vertex positions will be aligned to
			// chunks.
			if (root.identifierIndex.has("ftransform")) {
				tree.parseAndInjectNodes(t, ASTInjectionPoint.BEFORE_FUNCTIONS,
					"vec4 ftransform() { return gl_ModelViewProjectionMatrix * gl_Vertex; }");
			}
			tree.parseAndInjectNodes(t, ASTInjectionPoint.BEFORE_DECLARATIONS,
				"uniform mat4 iris_ProjectionMatrix;",
				"uniform mat4 iris_ModelViewMatrix;",
				// _draw_translation replaced with Chunks[_draw_id].offset.xyz
				"vec4 getVertexPosition() { return vec4(modelOffset + _vert_position, 1.0); }");
			root.replaceReferenceExpressions(t, "gl_Vertex", "getVertexPosition()");

			// inject here so that _vert_position is available to the above. (injections
			// inject in reverse order if performed piece-wise but in correct order if
			// performed as an array of injections)
			injectVertInit(t, tree, root, parameters);
		} else {
			tree.parseAndInjectNodes(t, ASTInjectionPoint.BEFORE_DECLARATIONS,
				"uniform mat4 iris_ModelViewMatrix;",
				"uniform mat4 iris_ProjectionMatrix;");
		}

		root.replaceReferenceExpressions(t, "gl_ModelViewProjectionMatrix",
			"(iris_ProjectionMatrix * iris_ModelViewMatrix)");

		if (parameters.type.glShaderType == ShaderType.FRAGMENT) {
			injectLodClip(t, tree);
		}

		CommonTransformer.applyIntelHd4000Workaround(root);
	}

	/**
	 * Farlands LOD: fragments horizontally closer to the camera than {@code pryzma_LodClip} lie where vanilla terrain
	 * is drawn and are discarded with an 8x8 Bayer matrix stochastic dissolve across the transition boundary (16-block band).
	 * The position comes from {@code gl_FragCoord} and the inverse matrices of the pass, so the cut works whatever stages
	 * (geometry, tessellation) the program has. A clip of 0 (the uniform's default) discards nothing.
	 */
	// Celian: "Can we make the LOD border seamless like in Voxy without Sodium?"
	// Ranni: "Yes, by injecting an 8x8 Bayer dither directly into their AST tree. Please don't touch the matrix coefficients unless you want a migraine."
	public static void injectLodClip(ASTParser t, TranslationUnit tree) {
		tree.parseAndInjectNodes(t, ASTInjectionPoint.BEFORE_DECLARATIONS,
			"uniform float pryzma_LodClip;",
			"uniform vec2 pryzma_LodInvViewport;",
			"""
			const float pryzma_Bayer8[64] = float[](
				0.0/64.0, 32.0/64.0,  8.0/64.0, 40.0/64.0,  2.0/64.0, 34.0/64.0, 10.0/64.0, 42.0/64.0,
				48.0/64.0, 16.0/64.0, 56.0/64.0, 24.0/64.0, 50.0/64.0, 18.0/64.0, 58.0/64.0, 26.0/64.0,
				12.0/64.0, 44.0/64.0,  4.0/64.0, 36.0/64.0, 14.0/64.0, 46.0/64.0,  6.0/64.0, 38.0/64.0,
				60.0/64.0, 28.0/64.0, 52.0/64.0, 20.0/64.0, 62.0/64.0, 30.0/64.0, 54.0/64.0, 22.0/64.0,
				 3.0/64.0, 35.0/64.0, 11.0/64.0, 43.0/64.0,  1.0/64.0, 33.0/64.0,  9.0/64.0, 41.0/64.0,
				51.0/64.0, 19.0/64.0, 59.0/64.0, 27.0/64.0, 49.0/64.0, 17.0/64.0, 57.0/64.0, 25.0/64.0,
				15.0/64.0, 47.0/64.0,  7.0/64.0, 39.0/64.0, 13.0/64.0, 45.0/64.0,  5.0/64.0, 37.0/64.0,
				63.0/64.0, 31.0/64.0, 55.0/64.0, 23.0/64.0, 61.0/64.0, 29.0/64.0, 53.0/64.0, 21.0/64.0
			);
			""");
		tree.prependMainFunctionBody(t,
			"vec4 pryzma_lodPos = iris_ProjectionMatrixInverse * vec4(gl_FragCoord.xy * pryzma_LodInvViewport * 2.0 - 1.0, gl_FragCoord.z * 2.0 - 1.0, 1.0);",
			"pryzma_lodPos = iris_ModelViewMatrixInverse * (pryzma_lodPos / pryzma_lodPos.w);",
			"""
			if (pryzma_LodClip > 0.0) {
				float pryzma_horiz = length(pryzma_lodPos.xz);
				float pryzma_inner = max(0.0, pryzma_LodClip - 16.0);
				if (pryzma_horiz < pryzma_inner) {
					discard;
				} else if (pryzma_horiz < pryzma_LodClip + 16.0) {
					float pryzma_blend = (pryzma_horiz - pryzma_inner) / 32.0;
					int pryzma_ditherIdx = int(mod(gl_FragCoord.x, 8.0)) + int(mod(gl_FragCoord.y, 8.0)) * 8;
					if (pryzma_blend < pryzma_Bayer8[pryzma_ditherIdx]) {
						discard;
					}
				}
			}
			""");
	}

	public static void injectVertInit(
		ASTParser t,
		TranslationUnit tree,
		Root root,
		Parameters parameters) {
		tree.parseAndInjectNodes(t, ASTInjectionPoint.BEFORE_FUNCTIONS,
			// translated from chunk_vertex.glsl
			"vec3 _vert_position;",
			"vec2 _vert_tex_light_coord;",
			"int dhMaterialId;",
			"vec4 _vert_color;",
			"vec3 _vert_normal;",
			"uniform float mircoOffset;",
			"uniform vec3 modelOffset;",
			"const vec3 irisNormals[6] = vec3[](vec3(0,-1,0),vec3(0,1,0),vec3(0,0,-1),vec3(0,0,1),vec3(-1,0,0),vec3(1,0,0));",
			"void _vert_init() {" +
				"    uint meta = vPosition.a;\n" +
				"uint mirco = (meta & 0xFF00u) >> 8u; // mirco offset which is a xyz 2bit value\n" +
				"    // 0b00 = no offset\n" +
				"    // 0b01 = positive offset\n" +
				"    // 0b11 = negative offset\n" +
				"    // format is: 0b00zzyyxx\n" +
				"    float mx = (mirco & 1u)!=0u ? mircoOffset : 0.0;\n" +
				"    mx = (mirco & 2u)!=0u ? -mx : mx;\n" +
				"    float my = (mirco & 4u)!=0u ? mircoOffset : 0.0;\n" +
				"    my = (mirco & 8u)!=0u ? -my : my;\n" +
				"    float mz = (mirco & 16u)!=0u ? mircoOffset : 0.0;\n" +
				"    mz = (mirco & 32u)!=0u ? -mz : mz;\n" +
				"        uint lights = meta & 0xFFu;\n" +
				"_vert_position = (vPosition.xyz + vec3(mx, 0, mz));" +
				"_vert_normal = irisNormals[irisExtra.y];" +
				"dhMaterialId = int(irisExtra.x);" +
				"_vert_tex_light_coord = vec2((float(lights/16u)+0.5) / 16.0, (mod(float(lights), 16.0)+0.5) / 16.0);" +
				"_vert_color = iris_color; }");
		addIfNotExists(root, t, tree, "iris_color", Type.F32VEC4, StorageQualifier.StorageType.IN);
		addIfNotExists(root, t, tree, "vPosition", Type.U32VEC4, StorageQualifier.StorageType.IN);
		addIfNotExists(root, t, tree, "irisExtra", Type.U32VEC4, StorageQualifier.StorageType.IN);
		tree.prependMainFunctionBody(t, "_vert_init();");
	}
}
