package net.pryzma.shader.pathways;

import net.pryzma.shader.PryzmaShaders;
import net.pryzma.shader.pipeline.WorldRenderingPhase;
import net.pryzma.shader.pipeline.WorldRenderingPipeline;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.TransparentBlock;
import org.joml.Matrix4fc;

public class HandRenderer {
	public static final HandRenderer INSTANCE = new HandRenderer();
	public static final float DEPTH = 0.125F;
	private boolean ACTIVE;
	private boolean renderingSolid;

	private boolean canRender(Camera camera, GameRenderer gameRenderer) {
		Minecraft mc = Minecraft.getInstance();
		return !(camera.isDetached()
			|| !(camera.entity() instanceof Player)
			|| mc.gui.hud.isHidden()
			|| (camera.entity() instanceof LivingEntity living && living.isSleeping())
			|| mc.gameMode.getPlayerMode() == GameType.SPECTATOR);
	}

	public boolean isHandTranslucent(ItemStack itemStack) {
		if (itemStack == null || itemStack.isEmpty()) {
			return false;
		}
		Item item = itemStack.getItem();
		if (item instanceof BlockItem blockItem) {
			return blockItem.getBlock() instanceof HalfTransparentBlock || blockItem.getBlock() instanceof TransparentBlock;
		}
		return false;
	}

	public boolean isAnyHandSolid() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return false;
		}
		return !isHandTranslucent(mc.player.getMainHandItem()) || !isHandTranslucent(mc.player.getOffhandItem());
	}

	public boolean isAnyHandTranslucent() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return false;
		}
		return isHandTranslucent(mc.player.getMainHandItem()) || isHandTranslucent(mc.player.getOffhandItem());
	}

	public void renderSolid(Matrix4fc modelMatrix, float tickDelta, Camera camera, GameRenderer gameRenderer, WorldRenderingPipeline pipeline) {
		if (!canRender(camera, gameRenderer) || !isAnyHandSolid() || !PryzmaShaders.isPackInUseQuick()) {
			return;
		}
		ACTIVE = true;
		renderingSolid = true;
		pipeline.setPhase(WorldRenderingPhase.HAND_SOLID);
	}

	public void endSolid(WorldRenderingPipeline pipeline) {
		renderingSolid = false;
		pipeline.setPhase(WorldRenderingPhase.NONE);
		ACTIVE = false;
	}

	public void renderTranslucent(Matrix4fc modelMatrix, float tickDelta, Camera camera, GameRenderer gameRenderer, WorldRenderingPipeline pipeline) {
		if (!canRender(camera, gameRenderer) || !isAnyHandTranslucent() || !PryzmaShaders.isPackInUseQuick()) {
			return;
		}
		ACTIVE = true;
		pipeline.setPhase(WorldRenderingPhase.HAND_TRANSLUCENT);
	}

	public void endTranslucent(WorldRenderingPipeline pipeline) {
		pipeline.setPhase(WorldRenderingPhase.NONE);
		ACTIVE = false;
	}

	public boolean isActive() {
		return ACTIVE;
	}

	public boolean isRenderingSolid() {
		return renderingSolid;
	}
}
