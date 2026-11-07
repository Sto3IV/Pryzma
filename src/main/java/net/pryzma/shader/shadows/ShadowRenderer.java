package net.pryzma.shader.shadows;

import com.google.common.collect.ImmutableList;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.pryzma.shader.PryzmaShaders;
import net.pryzma.shader.gl.GLDebug;
import net.pryzma.shader.gl.ShaderRenderSystem;
import net.pryzma.shader.gui.option.ShaderVideoSettings;
import net.pryzma.shader.mixin.LevelRendererAccessor;
import net.pryzma.shader.mixin.ViewAreaAccessor;
import net.pryzma.shader.platform.ShaderPlatformHelpers;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.pryzma.shader.pipeline.ShadedWorldRenderingPipeline;
import net.pryzma.shader.shaderpack.programs.ProgramSource;
import net.pryzma.shader.shaderpack.properties.PackDirectives;
import net.pryzma.shader.shaderpack.properties.PackShadowDirectives;
import net.pryzma.shader.shaderpack.properties.ShadowCullState;
import net.pryzma.shader.shadows.frustum.BoxCuller;
import net.pryzma.shader.shadows.frustum.CullEverythingFrustum;
import net.pryzma.shader.shadows.frustum.FrustumHolder;
import net.pryzma.shader.shadows.frustum.advanced.AdvancedShadowCullingFrustum;
import net.pryzma.shader.shadows.frustum.advanced.SafeZoneCullingFrustum;
import net.pryzma.shader.shadows.frustum.fallback.BoxCullingFrustum;
import net.pryzma.shader.shadows.frustum.fallback.NonCullingFrustum;
import net.pryzma.shader.uniforms.CameraUniforms;
import net.pryzma.shader.uniforms.CapturedRenderingState;
import net.pryzma.shader.uniforms.CelestialUniforms;
import net.pryzma.shader.uniforms.custom.CustomUniforms;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.RotatingSectionStorage;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.ARBTextureSwizzle;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class ShadowRenderer {
	public static int RESOLUTION;
	public static boolean ACTIVE = false;
	public static List<BlockEntity> visibleBlockEntities;
	public static int renderDistance;
	public static Matrix4f MODELVIEW;
	public static Matrix4f PROJECTION;
	public static Frustum FRUSTUM;
	private final float halfPlaneLength;
	private final float nearPlane, farPlane;
	private final float voxelDistance;
	private final float renderDistanceMultiplier;
	private final float entityShadowDistanceMultiplier;
	private final int resolution;
	private final float intervalSize;
	private final Float fov;
	private final ShadowRenderTargets targets;
	private final ShadowCullState packCullingState;
	private final ShadowCompositeRenderer compositeRenderer;
	private final boolean shouldRenderTerrain;
	private final boolean shouldRenderTranslucent;
	private final boolean shouldRenderEntities;
	private final boolean shouldRenderPlayer;
	private final boolean shouldRenderBlockEntities;
	private final float sunPathRotation;
	private final RenderBuffers buffers;
	private final List<MipmapPass> mipmapPasses = new ArrayList<>();
	private final String debugStringOverall;
	private final boolean separateHardwareSamplers;
	private final boolean shouldRenderLightBlockEntities;
	private final ShadedWorldRenderingPipeline pipeline;
	private boolean packHasVoxelization;
	private FrustumHolder terrainFrustumHolder;
	private FrustumHolder entityFrustumHolder;
	private String debugStringTerrain = "(unavailable)";
	private int renderedShadowEntities = 0;
	private int renderedShadowBlockEntities = 0;

	public ShadowRenderer(ShadedWorldRenderingPipeline pipeline, ProgramSource shadow, PackDirectives directives,
						  ShadowRenderTargets shadowRenderTargets, ShadowCompositeRenderer compositeRenderer, CustomUniforms customUniforms, boolean separateHardwareSamplers) {

		this.pipeline = pipeline;
		this.separateHardwareSamplers = separateHardwareSamplers;

		final PackShadowDirectives shadowDirectives = directives.getShadowDirectives();

		this.halfPlaneLength = shadowDirectives.getDistance();
		this.nearPlane = shadowDirectives.getNearPlane();
		this.farPlane = shadowDirectives.getFarPlane();

		this.voxelDistance = shadowDirectives.getVoxelDistance();
		this.renderDistanceMultiplier = shadowDirectives.getDistanceRenderMul();
		this.entityShadowDistanceMultiplier = shadowDirectives.getEntityShadowDistanceMul();
		this.resolution = shadowDirectives.getResolution();
		this.intervalSize = shadowDirectives.getIntervalSize();
		this.shouldRenderTerrain = shadowDirectives.shouldRenderTerrain();
		this.shouldRenderTranslucent = shadowDirectives.shouldRenderTranslucent();
		this.shouldRenderEntities = shadowDirectives.shouldRenderEntities();
		this.shouldRenderPlayer = shadowDirectives.shouldRenderPlayer();
		this.shouldRenderBlockEntities = shadowDirectives.shouldRenderBlockEntities();
		this.shouldRenderLightBlockEntities = shadowDirectives.shouldRenderLightBlockEntities();

		this.compositeRenderer = compositeRenderer;

		debugStringOverall = "half plane = " + halfPlaneLength + " meters @ " + resolution + "x" + resolution;

		this.terrainFrustumHolder = new FrustumHolder();
		this.entityFrustumHolder = new FrustumHolder();

		this.fov = shadowDirectives.getFov();
		this.targets = shadowRenderTargets;

		if (shadow != null) {
			// Assume that the shader pack is doing voxelization if a geometry shader is detected.
			// Also assume voxelization if image load / store is detected.
			this.packHasVoxelization = shadow.getGeometrySource().isPresent();
			this.packCullingState = shadowDirectives.getCullingState();
		} else {
			this.packHasVoxelization = false;
			this.packCullingState = ShadowCullState.DEFAULT;
		}

		this.sunPathRotation = directives.getSunPathRotation();

		int processors = Runtime.getRuntime().availableProcessors();
		this.buffers = new RenderBuffers(processors);

		configureSamplingSettings(shadowDirectives);
	}

	public static PoseStack createShadowModelView(float sunPathRotation, float intervalSize, float nearPlane, float farPlane) {
		// Determine the camera position
		Vector3d cameraPos = CameraUniforms.getUnshiftedCameraPosition();

		double cameraX = cameraPos.x;
		double cameraY = cameraPos.y;
		double cameraZ = cameraPos.z;

		// Set up our modelview matrix stack
		PoseStack modelView = new PoseStack();
		ShadowMatrices.createModelViewMatrix(modelView, getShadowAngle(), intervalSize, sunPathRotation, cameraX, cameraY, cameraZ, nearPlane, farPlane);

		return modelView;
	}

	private static ClientLevel getLevel() {
		return Objects.requireNonNull(Minecraft.getInstance().level);
	}

	private static float getSkyAngle() {
		Float angle = getLevel().environmentAttributes().getDimensionValue(EnvironmentAttributes.SUN_ANGLE);
		return angle != null ? (angle / 360.0F) % 1.0F : 0.0F;
	}

	private static float getSunAngle() {
		float skyAngle = getSkyAngle();

		if (skyAngle < 0.75F) {
			return skyAngle + 0.25F;
		} else {
			return skyAngle - 0.75F;
		}
	}

	private static float getShadowAngle() {
		float shadowAngle = getSunAngle();

		if (!CelestialUniforms.isDay()) {
			shadowAngle -= 0.5F;
		}

		return shadowAngle;
	}

	public void setUsesImages(boolean usesImages) {
		this.packHasVoxelization = packHasVoxelization || usesImages;
	}

	private void configureSamplingSettings(PackShadowDirectives shadowDirectives) {
		final ImmutableList<PackShadowDirectives.DepthSamplingSettings> depthSamplingSettings =
			shadowDirectives.getDepthSamplingSettings();

		final Int2ObjectMap<PackShadowDirectives.SamplingSettings> colorSamplingSettings =
			shadowDirectives.getColorSamplingSettings();

		GlStateManager._activeTexture(GL20C.GL_TEXTURE4);

		configureDepthSampler(targets.getDepthTexture().getTextureId(), depthSamplingSettings.get(0));

		configureDepthSampler(targets.getDepthTextureNoTranslucents().getTextureId(), depthSamplingSettings.get(1));

		for (int i = 0; i < targets.getNumColorTextures(); i++) {
			if (targets.get(i) != null) {
				int glTextureId = targets.get(i).getMainTexture();

				configureSampler(glTextureId, colorSamplingSettings.computeIfAbsent(i, a -> new PackShadowDirectives.SamplingSettings()));
			}
		}

		GlStateManager._activeTexture(GL20C.GL_TEXTURE0);
	}

	private void configureDepthSampler(int glTextureId, PackShadowDirectives.DepthSamplingSettings settings) {
		if (settings.getHardwareFiltering() && !separateHardwareSamplers) {
			// We have to do this or else shadow hardware filtering breaks entirely!
			ShaderRenderSystem.texParameteri(glTextureId, GL20C.GL_TEXTURE_2D, GL20C.GL_TEXTURE_COMPARE_MODE, GL30C.GL_COMPARE_REF_TO_TEXTURE);
		}

		// Workaround for issues with old shader packs like Chocapic v4.
		// They expected the driver to put the depth value in z, but it's supposed to only
		// be available in r. So we set up the swizzle to fix that.
		ShaderRenderSystem.texParameteriv(glTextureId, GL20C.GL_TEXTURE_2D, ARBTextureSwizzle.GL_TEXTURE_SWIZZLE_RGBA,
			new int[]{GL30C.GL_RED, GL30C.GL_RED, GL30C.GL_RED, GL30C.GL_ONE});

		configureSampler(glTextureId, settings);
	}

	private void configureSampler(int glTextureId, PackShadowDirectives.SamplingSettings settings) {
		if (settings.getMipmap()) {
			int filteringMode = settings.getNearest() ? GL20C.GL_NEAREST_MIPMAP_NEAREST : GL20C.GL_LINEAR_MIPMAP_LINEAR;
			mipmapPasses.add(new MipmapPass(glTextureId, filteringMode));
		}

		if (!settings.getNearest()) {
			// Make sure that things are smoothed
			ShaderRenderSystem.texParameteri(glTextureId, GL20C.GL_TEXTURE_2D, GL20C.GL_TEXTURE_MIN_FILTER, GL20C.GL_LINEAR);
			ShaderRenderSystem.texParameteri(glTextureId, GL20C.GL_TEXTURE_2D, GL20C.GL_TEXTURE_MAG_FILTER, GL20C.GL_LINEAR);
		} else {
			ShaderRenderSystem.texParameteri(glTextureId, GL20C.GL_TEXTURE_2D, GL20C.GL_TEXTURE_MIN_FILTER, GL20C.GL_NEAREST);
			ShaderRenderSystem.texParameteri(glTextureId, GL20C.GL_TEXTURE_2D, GL20C.GL_TEXTURE_MAG_FILTER, GL20C.GL_NEAREST);
		}
	}

	private void generateMipmaps() {
		GlStateManager._activeTexture(GL20C.GL_TEXTURE4);

		for (MipmapPass mipmapPass : mipmapPasses) {
			setupMipmappingForTexture(mipmapPass.texture(), mipmapPass.targetFilteringMode());
		}

		GlStateManager._activeTexture(GL20C.GL_TEXTURE0);
	}

	private void setupMipmappingForTexture(int texture, int filteringMode) {
		ShaderRenderSystem.generateMipmaps(texture, GL20C.GL_TEXTURE_2D);
		ShaderRenderSystem.texParameteri(texture, GL20C.GL_TEXTURE_2D, GL20C.GL_TEXTURE_MIN_FILTER, filteringMode);
	}

	private FrustumHolder createShadowFrustum(float renderMultiplier, FrustumHolder holder) {
		// TODO: Cull entities / block entities with Advanced Frustum Culling even if voxelization is detected.
		String distanceInfo;
		String cullingInfo;
		if ((packCullingState == ShadowCullState.DEFAULT && packHasVoxelization) || packCullingState == ShadowCullState.DISTANCE) {
			double distance = halfPlaneLength * renderMultiplier;

			String reason;

			if (packCullingState == ShadowCullState.DISTANCE) {
				reason = "(set by shader pack)";
			} else /*if (packHasVoxelization)*/ {
				reason = "(voxelization detected)";
			}

			if (distance <= 0 || distance > Minecraft.getInstance().options.getEffectiveRenderDistance() * 16) {
				distanceInfo = "render distance = " + Minecraft.getInstance().options.getEffectiveRenderDistance() * 16
					+ " blocks ";
				distanceInfo += Minecraft.getInstance().isLocalServer() ? "(capped by normal render distance)" : "(capped by normal/server render distance)";
				cullingInfo = "disabled " + reason;
				return holder.setInfo(new NonCullingFrustum(), distanceInfo, cullingInfo);
			} else {
				distanceInfo = distance + " blocks (set by shader pack)";
				cullingInfo = "distance only " + reason;
				BoxCuller boxCuller = new BoxCuller(distance);
				holder.setInfo(new BoxCullingFrustum(boxCuller), distanceInfo, cullingInfo);
			}
		} else {
			BoxCuller boxCuller;

			boolean isReversed = packCullingState == ShadowCullState.REVERSED;

			// Assume render multiplier is meant to be 1 if reversed culling is on
			if (isReversed && renderMultiplier < 0) renderMultiplier = 1.0f;

			double distance = (isReversed ? voxelDistance : halfPlaneLength) * renderMultiplier;
			String setter = "(set by shader pack)";

			if (renderMultiplier < 0) {
				distance = ShaderVideoSettings.shadowDistance * 16;
				setter = "(set by user)";
			}

			if (distance >= Minecraft.getInstance().options.getEffectiveRenderDistance() * 16 && !isReversed) {
				distanceInfo = "render distance = " + Minecraft.getInstance().options.getEffectiveRenderDistance() * 16
					+ " blocks ";
				distanceInfo += Minecraft.getInstance().isLocalServer() ? "(capped by normal render distance)" : "(capped by normal/server render distance)";
				boxCuller = null;
			} else {
				distanceInfo = distance + " blocks " + setter;

				if (distance == 0.0 && !isReversed) {
					cullingInfo = "no shadows rendered";
					holder.setInfo(new CullEverythingFrustum(), distanceInfo, cullingInfo);
				}

				boxCuller = new BoxCuller(distance);
			}

			cullingInfo = (isReversed ? "Safe Zone" : "Advanced") + " Frustum Culling enabled";

			Vector4f shadowLightPosition = new CelestialUniforms(sunPathRotation).getShadowLightPositionInWorldSpace();

			Vector3f shadowLightVectorFromOrigin =
				new Vector3f(shadowLightPosition.x(), shadowLightPosition.y(), shadowLightPosition.z());

			shadowLightVectorFromOrigin.normalize();

			Matrix4f projView = CapturedRenderingState.INSTANCE.getGbufferProjection()
					.mul(CapturedRenderingState.INSTANCE.getGbufferModelView(), new Matrix4f());

			if (isReversed) {
				return holder.setInfo(new SafeZoneCullingFrustum(projView, PROJECTION, shadowLightVectorFromOrigin, boxCuller, new BoxCuller(halfPlaneLength * renderMultiplier)), distanceInfo, cullingInfo);
			} else {
				return holder.setInfo(new AdvancedShadowCullingFrustum(projView, PROJECTION, shadowLightVectorFromOrigin, boxCuller), distanceInfo, cullingInfo);
			}
		}

		return holder;
	}

	/** Immersive Engineering draws its block entity VBOs from a level stage event; null without IE. */
	private static final MethodHandle IE_AFTER_TER_RENDERING = findImmersiveEngineeringHook();

	private static MethodHandle findImmersiveEngineeringHook() {
		if (!ShaderPlatformHelpers.getInstance().isModLoaded("immersiveengineering")) {
			return null;
		}
		try {
			return MethodHandles.lookup().findStatic(Class.forName("blusunrize.immersiveengineering.client.utils.VertexBufferHolder"),
				"afterTERRendering", MethodType.methodType(void.class, RenderLevelStageEvent.class));
		} catch (ReflectiveOperationException e) {
			PryzmaShaders.logger.error("Failed to load Immersive Engineering shadow compatibility", e);
			return null;
		}
	}

	/** PryzmaShaders NeoForge's Immersive Engineering hook, folded in from its mixin into this class. */
	private void renderImmersiveEngineeringShadows(Camera playerCamera, Matrix4f shadowProjection) {
		if (IE_AFTER_TER_RENDERING == null) {
			return;
		}
	}

	/** Sections kept by the frustum, before ordering; reused across frames. */
	private SectionRenderDispatcher.RenderSection[] shadowSections = new SectionRenderDispatcher.RenderSection[0];
	/** View-space depth bucket of each kept section, along the shadow camera's axis. */
	private int[] shadowSectionKeys = new int[0];
	private int[] shadowBucketStarts = new int[0];

	/**
	 * Fills the level renderer's visible section list (the shadow pass's own list; see {@link CullingDataCache})
	 * with every compiled section inside {@code frustum}, and {@link #visibleBlockEntities} with their block
	 * entities. Candidates come from {@link ShadowSectionIndex}. Sections are ordered nearest the light first, a counting sort on 16-block view-space depth
	 * buckets: the forward walk of the opaque layers then draws front to back (early depth rejection in the
	 * shadow map) and the backward walk vanilla uses for translucent terrain draws back to front.
	 */
	private void collectShadowSections(LevelRendererAccessor levelRenderer, Frustum frustum, Matrix4f shadowModelView,
									   double cameraX, double cameraY, double cameraZ) {
		ObjectArrayList<SectionRenderDispatcher.RenderSection> visible = levelRenderer.getVisibleSections();
		visible.clear();
		ViewArea viewArea = levelRenderer.getViewArea();
		if (viewArea == null) {
			return;
		}
		RotatingSectionStorage<SectionRenderDispatcher.RenderSection> storage = ((ViewAreaAccessor) viewArea).pryzma$getSections();
		int totalSections = storage.size();
		if (shadowSections.length < totalSections) {
			shadowSections = new SectionRenderDispatcher.RenderSection[totalSections];
			shadowSectionKeys = new int[totalSections];
		}
		// Row 2 of the modelview gives view-space z; the shadow camera looks down -z, so larger is nearer the light.
		float zx = shadowModelView.m02();
		float zy = shadowModelView.m12();
		float zz = shadowModelView.m22();
		int count = 0;
		int minKey = Integer.MAX_VALUE;
		int maxKey = Integer.MIN_VALUE;

		for (SectionRenderDispatcher.RenderSection section : storage) {
			if (section == null) {
				continue;
			}
			SectionMesh mesh = section.getSectionMesh();
			if (mesh == null) {
				continue;
			}
			boolean geometry = mesh.hasRenderableLayers();
			List<BlockEntity> blockEntities = mesh.getRenderableBlockEntities();
			if (!geometry && (blockEntities == null || blockEntities.isEmpty())) {
				continue;
			}
			AABB box = section.getBoundingBox();
			if (!frustum.isVisible(box)) {
				continue;
			}
			if (blockEntities != null && !blockEntities.isEmpty()) {
				visibleBlockEntities.addAll(blockEntities);
			}
			if (geometry) {
				float rx = (float) (box.minX + 8.0 - cameraX);
				float ry = (float) (box.minY + 8.0 - cameraY);
				float rz = (float) (box.minZ + 8.0 - cameraZ);
				int key = Mth.floor((zx * rx + zy * ry + zz * rz) * 0.0625F);
				shadowSections[count] = section;
				shadowSectionKeys[count] = key;
				count++;
				minKey = Math.min(minKey, key);
				maxKey = Math.max(maxKey, key);
			}
		}
		if (count == 0) {
			return;
		}
		visible.size(count);
		// The backing array is an Object[]: elements() is typed K[] but not created as one.
		Object[] out = visible.elements();
		shadowBucketStarts = ShadowSectionOrder.sortDescending(shadowSections, shadowSectionKeys, count, minKey, maxKey, shadowBucketStarts, out);
		Arrays.fill(shadowSections, 0, count, null);
	}

	public void setupShadowViewport() {
		// Set up the viewport
		GL11C.glViewport(0, 0, resolution, resolution);
	}

	public void renderShadows(LevelRendererAccessor levelRenderer, Camera playerCamera) {
		if (ShaderVideoSettings.getOverriddenShadowDistance(ShaderVideoSettings.shadowDistance) == 0) {
			return;
		}

		RESOLUTION = resolution;

		Minecraft client = Minecraft.getInstance();

		Profiler.get().popPush("shadows");
		ACTIVE = true;

		renderDistance = (int) ((halfPlaneLength * renderDistanceMultiplier) / 16);

		if (renderDistanceMultiplier < 0) {
			renderDistance = ShaderVideoSettings.shadowDistance;
		}


		visibleBlockEntities = new ArrayList<>();
		setupShadowViewport();

		// Create our camera
		PoseStack modelView = createShadowModelView(this.sunPathRotation, this.intervalSize, nearPlane, farPlane);
		MODELVIEW = new Matrix4f(modelView.last().pose());

		// Set up our orthographic projection matrix and load it into RenderSystem
		Matrix4f shadowProjection;
		if (this.fov != null) {
			// If FOV is not null, the pack wants a perspective based projection matrix. (This is to support legacy packs)
			shadowProjection = ShadowMatrices.createPerspectiveMatrix(this.fov);
		} else {
			shadowProjection = ShadowMatrices.createOrthoMatrix(halfPlaneLength, Mth.equal(nearPlane, -1.0f) ? -Minecraft.getInstance().options.getEffectiveRenderDistance() * 16 : nearPlane, Mth.equal(farPlane, -1.0f) ? Minecraft.getInstance().options.getEffectiveRenderDistance() * 16 : farPlane);
		}

		ShaderRenderSystem.setShadowProjection(shadowProjection);

		PROJECTION = shadowProjection;

		Profiler.get().push("terrain_setup");

		if (levelRenderer instanceof CullingDataCache) {
			((CullingDataCache) levelRenderer).saveState();
		}

		Profiler.get().push("initialize frustum");

		terrainFrustumHolder = createShadowFrustum(renderDistanceMultiplier, terrainFrustumHolder);

		FRUSTUM = terrainFrustumHolder.getFrustum();

		// Determine the player camera position
		Vector3d cameraPos = CameraUniforms.getUnshiftedCameraPosition();

		double cameraX = cameraPos.x();
		double cameraY = cameraPos.y();
		double cameraZ = cameraPos.z();

		// Center the frustum on the player camera position
		terrainFrustumHolder.getFrustum().prepare(cameraX, cameraY, cameraZ);

		Profiler.get().pop();

		// Pryzma: the shadow pass takes the compiled sections of the view area that its own frustum accepts.
		// PryzmaShaders runs vanilla setupRender here, which on the vanilla chunk path (1) invalidates the occlusion graph the
		// main pass shares, so it is rebuilt without smart culling every frame and the main view loses occlusion
		// culling, and (2) culls through Frustum(Frustum), a copy of base-class state these frustums never set: an
		// identity box at the world origin, or an endless offsetToFullyIncludeCameraCube for NonCullingFrustum.
		collectShadowSections(levelRenderer, terrainFrustumHolder.getFrustum(), MODELVIEW, cameraX, cameraY, cameraZ);

		Profiler.get().popPush("terrain");

		// Disable backface culling
		// This partially works around an issue where if the front face of a mountain isn't visible, it casts no
		// shadow.
		//
		// However, it only partially resolves issues of light leaking into caves.
		//
		// TODO: Better way of preventing light from leaking into places where it shouldn't
		GlStateManager._disableCull();

		// Render all opaque terrain unless pack requests not to
		if (shouldRenderTerrain) {
			((LevelRenderer) levelRenderer).prepareChunkRenders(MODELVIEW, false);
		}

		// Reset our viewport in case an external renderer overrode it
		GL11C.glViewport(0, 0, resolution, resolution);

		Profiler.get().popPush("entities");

		// Get the current tick delta. Normally this is the same as client.getTickDelta(), but when the game is paused,
		// it is set to a fixed value.
		final float tickDelta = CapturedRenderingState.INSTANCE.getTickDelta();

		// Create a constrained shadow frustum for entities to avoid rendering faraway entities in the shadow pass,
		// if the shader pack has requested it. Otherwise, use the same frustum as for terrain.
		boolean hasEntityFrustum = false;

		if (entityShadowDistanceMultiplier == 1.0F || entityShadowDistanceMultiplier < 0.0F) {
			entityFrustumHolder.setInfo(terrainFrustumHolder.getFrustum(), terrainFrustumHolder.getDistanceInfo(), terrainFrustumHolder.getCullingInfo());
		} else {
			hasEntityFrustum = true;
			entityFrustumHolder = createShadowFrustum(renderDistanceMultiplier * entityShadowDistanceMultiplier, entityFrustumHolder);
		}

		Frustum entityShadowFrustum = entityFrustumHolder.getFrustum();
		entityShadowFrustum.prepare(cameraX, cameraY, cameraZ);

		SubmitNodeStorage storage = levelRenderer.getSubmitNodeStorage();
		EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();

		if (shouldRenderEntities) {
			renderedShadowEntities = renderEntities(levelRenderer, dispatcher, storage, modelView, tickDelta, entityShadowFrustum, cameraX, cameraY, cameraZ);
		} else if (shouldRenderPlayer) {
			renderedShadowEntities = renderPlayerEntity(levelRenderer, dispatcher, storage, modelView, tickDelta, entityShadowFrustum, cameraX, cameraY, cameraZ);
		}

		Profiler.get().popPush("build blockentities");

		if (shouldRenderBlockEntities) {
			renderedShadowBlockEntities = renderBlockEntities(storage, modelView, playerCamera, cameraX, cameraY, cameraZ, tickDelta, hasEntityFrustum, false);
		} else if (shouldRenderLightBlockEntities) {
			renderedShadowBlockEntities = renderBlockEntities(storage, modelView, playerCamera, cameraX, cameraY, cameraZ, tickDelta, hasEntityFrustum, true);
		}

		Profiler.get().popPush("draw entities");

		copyPreTranslucentDepth(levelRenderer);

		Profiler.get().popPush("translucent terrain");

		if (shouldRenderTranslucent) {
			((LevelRenderer) levelRenderer).prepareChunkRenders(MODELVIEW, true);
		}

		ShaderRenderSystem.restorePlayerProjection();

		debugStringTerrain = "";

		Profiler.get().popPush("generate mipmaps");

		generateMipmaps();

		Profiler.get().popPush("restore gl state");

		// Restore backface culling
		GlStateManager._enableCull();

		GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, 0);

		// Restore the old viewport
		GL11C.glViewport(0, 0, client.gameRenderer.mainRenderTarget().width, client.gameRenderer.mainRenderTarget().height);

		if (levelRenderer instanceof CullingDataCache) {
			((CullingDataCache) levelRenderer).restoreState();
		}

		pipeline.removePhaseIfNeeded();

		GLDebug.pushGroup(901, "shadowcomp");
		compositeRenderer.renderAll();
		GLDebug.popGroup();

		visibleBlockEntities = null;
		ACTIVE = false;

		Profiler.get().pop();
		Profiler.get().popPush("updatechunks");
	}

	public int renderBlockEntities(SubmitNodeStorage storage, PoseStack modelView, Camera camera, double cameraX, double cameraY, double cameraZ, float tickDelta, boolean hasEntityFrustum, boolean lightsOnly) {
		Profiler.get().push("build blockentities");

		int shadowBlockEntities = 0;
		BoxCuller culler = null;
		if (hasEntityFrustum) {
			culler = new BoxCuller(halfPlaneLength * (renderDistanceMultiplier * entityShadowDistanceMultiplier));
			culler.setPosition(cameraX, cameraY, cameraZ);
		}

		var blockEntityDispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
		CameraRenderState cameraRenderState = new CameraRenderState();

		for (BlockEntity entity : visibleBlockEntities) {
			if (lightsOnly && entity.getBlockState().getLightEmission() == 0) {
				continue;
			}

			BlockPos pos = entity.getBlockPos();
			if (hasEntityFrustum) {
				if (culler.isCulled(pos.getX() - 1, pos.getY() - 1, pos.getZ() - 1, pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1)) {
					continue;
				}
			}
			var state = blockEntityDispatcher.tryExtractRenderState(entity, tickDelta, null, false);
			if (state != null) {
				modelView.pushPose();
				modelView.translate(pos.getX() - cameraX, pos.getY() - cameraY, pos.getZ() - cameraZ);
				blockEntityDispatcher.submit(state, modelView, storage, cameraRenderState);
				modelView.popPose();

				shadowBlockEntities++;
			}
		}

		Profiler.get().pop();

		return shadowBlockEntities;
	}

	private int renderEntities(LevelRendererAccessor levelRenderer, EntityRenderDispatcher dispatcher, SubmitNodeStorage storage, PoseStack modelView, float tickDelta, Frustum frustum, double cameraX, double cameraY, double cameraZ) {
		Profiler.get().push("cull");

		List<Entity> renderedEntities = new ArrayList<>(32);

		for (Entity entity : getLevel().entitiesForRendering()) {
			if (!dispatcher.shouldRender(entity, frustum, cameraX, cameraY, cameraZ, tickDelta) || entity.isSpectator()) {
				continue;
			}

			renderedEntities.add(entity);
		}

		Profiler.get().popPush("sort");

		renderedEntities.sort(Comparator.comparingInt(entity -> entity.getType().hashCode()));

		Profiler.get().popPush("build entity geometry");

		CameraRenderState cameraRenderState = new CameraRenderState();

		for (Entity entity : renderedEntities) {
			float realTickDelta = Minecraft.getInstance().level.tickRateManager().isEntityFrozen(entity) ? tickDelta : CapturedRenderingState.INSTANCE.getRealTickDelta();
			var state = dispatcher.extractEntity(entity, realTickDelta);
			dispatcher.submit(state, cameraRenderState, entity.getX(), entity.getY(), entity.getZ(), modelView, storage);
		}

		Profiler.get().pop();

		return renderedEntities.size();
	}

	private int renderPlayerEntity(LevelRendererAccessor levelRenderer, EntityRenderDispatcher dispatcher, SubmitNodeStorage storage, PoseStack modelView, float tickDelta, Frustum frustum, double cameraX, double cameraY, double cameraZ) {
		Profiler.get().push("cull");

		Entity player = Minecraft.getInstance().player;

		if (!dispatcher.shouldRender(player, frustum, cameraX, cameraY, cameraZ, tickDelta) || player.isSpectator()) {
			Profiler.get().pop();
			return 0;
		}

		Profiler.get().popPush("build geometry");

		int shadowEntities = 0;
		CameraRenderState cameraRenderState = new CameraRenderState();

		if (!player.getPassengers().isEmpty()) {
			for (int i = 0; i < player.getPassengers().size(); i++) {
				Entity passenger = player.getPassengers().get(i);
				float realTickDelta = Minecraft.getInstance().level.tickRateManager().isEntityFrozen(passenger) ? tickDelta : CapturedRenderingState.INSTANCE.getRealTickDelta();
				var state = dispatcher.extractEntity(passenger, realTickDelta);
				dispatcher.submit(state, cameraRenderState, passenger.getX(), passenger.getY(), passenger.getZ(), modelView, storage);
				shadowEntities++;
			}
		}

		if (player.getVehicle() != null) {
			Entity vehicle = player.getVehicle();
			float realTickDelta = Minecraft.getInstance().level.tickRateManager().isEntityFrozen(vehicle) ? tickDelta : CapturedRenderingState.INSTANCE.getRealTickDelta();
			var state = dispatcher.extractEntity(vehicle, realTickDelta);
			dispatcher.submit(state, cameraRenderState, vehicle.getX(), vehicle.getY(), vehicle.getZ(), modelView, storage);
			shadowEntities++;
		}

		float realTickDelta = Minecraft.getInstance().level.tickRateManager().isEntityFrozen(player) ? tickDelta : CapturedRenderingState.INSTANCE.getRealTickDelta();
		var state = dispatcher.extractEntity(player, realTickDelta);
		dispatcher.submit(state, cameraRenderState, player.getX(), player.getY(), player.getZ(), modelView, storage);
		shadowEntities++;

		Profiler.get().pop();

		return shadowEntities;
	}

	private void copyPreTranslucentDepth(LevelRendererAccessor levelRenderer) {
		Profiler.get().popPush("translucent depth copy");

		targets.copyPreTranslucentDepth();
	}

	public void addDebugText(List<String> messages) {
		if (ShaderVideoSettings.getOverriddenShadowDistance(ShaderVideoSettings.shadowDistance) == 0) {
			messages.add("[" + PryzmaShaders.MODNAME + "] Shadow Maps: off, shadow distance 0");
			return;
		}

		if (PryzmaShaders.getShaderConfig().areDebugOptionsEnabled()) {
			messages.add("[" + PryzmaShaders.MODNAME + "] Shadow Maps: " + debugStringOverall);
			messages.add("[" + PryzmaShaders.MODNAME + "] Shadow Distance Terrain: " + terrainFrustumHolder.getDistanceInfo() + " Entity: " + entityFrustumHolder.getDistanceInfo());
			messages.add("[" + PryzmaShaders.MODNAME + "] Shadow Culling Terrain: " + terrainFrustumHolder.getCullingInfo() + " Entity: " + entityFrustumHolder.getCullingInfo());
			messages.add("[" + PryzmaShaders.MODNAME + "] Shadow Projection: " + getProjectionInfo());
			messages.add("[" + PryzmaShaders.MODNAME + "] Shadow Terrain: " + debugStringTerrain
				+ (shouldRenderTerrain ? "" : " (no terrain) ") + (shouldRenderTranslucent ? "" : "(no translucent)"));
			messages.add("[" + PryzmaShaders.MODNAME + "] Shadow Entities: " + getEntitiesDebugString());
			messages.add("[" + PryzmaShaders.MODNAME + "] Shadow Block Entities: " + getBlockEntitiesDebugString());
		} else {
			messages.add("[" + PryzmaShaders.MODNAME + "] Shadow info: " + debugStringTerrain);
			messages.add("[" + PryzmaShaders.MODNAME + "] E: " + renderedShadowEntities);
			messages.add("[" + PryzmaShaders.MODNAME + "] BE: " + renderedShadowBlockEntities);
		}
	}

	private String getProjectionInfo() {
		return "Near: " + nearPlane + " Far: " + farPlane + " distance " + halfPlaneLength;
	}

	private String getEntitiesDebugString() {
		return (shouldRenderEntities || shouldRenderPlayer) ? (renderedShadowEntities + "/" + Minecraft.getInstance().level.getEntityCount()) : "disabled by pack";
	}

	private String getBlockEntitiesDebugString() {
		return (shouldRenderBlockEntities || shouldRenderLightBlockEntities) ? renderedShadowBlockEntities + "" : "disabled by pack"; // TODO: + "/" + MinecraftClient.getInstance().world.blockEntities.size();
	}

	public void destroy() {
	}

	private record MipmapPass(int texture, int targetFilteringMode) {


	}
}
