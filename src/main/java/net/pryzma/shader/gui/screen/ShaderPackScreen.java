package net.pryzma.shader.gui.screen;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.GlUtil;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import net.pryzma.shader.PryzmaShaders;
import net.pryzma.shader.api.v0.ShaderApi;
import net.pryzma.shader.gui.GuiUtil;
import net.pryzma.shader.gui.NavigationController;
import net.pryzma.shader.gui.element.ShaderPackOptionList;
import net.pryzma.shader.gui.element.ShaderPackSelectionList;
import net.pryzma.shader.gui.element.screen.ShaderButton;
import net.pryzma.shader.gui.element.widget.AbstractElementWidget;
import net.pryzma.shader.gui.element.widget.CommentedElementWidget;
import net.pryzma.shader.mixin.GameRendererAccessor;
import net.pryzma.shader.platform.ShaderPlatformHelpers;
import net.pryzma.shader.shaderpack.ShaderPack;
import net.pryzma.shader.uniforms.FrameUpdateNotifier;
import net.pryzma.shader.uniforms.transforms.SmoothedFloat;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class ShaderPackScreen extends Screen implements HudHideable {
	/**
	 * Queue rendering to happen on top of all elements. Useful for tooltips or dialogs.
	 */
	public static final Set<Runnable> TOP_LAYER_RENDER_QUEUE = new HashSet<>();

	private static final Component SELECT_TITLE = Component.translatable("pack.pryzma.select.title").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
	private static final Component CONFIGURE_TITLE = Component.translatable("pack.pryzma.configure.title").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
	private static final int COMMENT_PANEL_WIDTH = 314;
	private static final String development = "Development Environment";
	private static String cachedGpuInfo = null;

	public static String getGpuInfo() {
		if (cachedGpuInfo == null) {
			try {
				String ver = GlStateManager._getString(GL11.GL_VERSION);
				String vendor = GlUtil.getVendor();
				String renderer = GlUtil.getRenderer();
				cachedGpuInfo = "OpenGL: " + (ver != null ? ver : "Unknown") + ", " +
						(vendor != null ? vendor : "Unknown") + ", " +
						(renderer != null ? renderer : "Unknown");
			} catch (Throwable t) {
				cachedGpuInfo = "OpenGL: Unknown";
			}
		}
		return cachedGpuInfo;
	}
	private final Screen parent;
	private final MutableComponent modInfoComponent;
	private final FrameUpdateNotifier notifier = new FrameUpdateNotifier();
	private ShaderPackSelectionList shaderPackList;
	private @Nullable ShaderPackOptionList shaderOptionList = null;
	private @Nullable NavigationController navigation = null;
	private Button screenSwitchButton;
	private Component notificationDialog = null;
	private int notificationDialogTimer = 0;
	private @Nullable AbstractElementWidget<?> hoveredElement = null;
	private Optional<Component> hoveredElementCommentTitle = Optional.empty();
	private List<FormattedCharSequence> hoveredElementCommentBody = new ArrayList<>();
	private int hoveredElementCommentTimer = 0;
	private boolean optionMenuOpen = false;
	private boolean dropChanges = false;
	private MutableComponent developmentComponent;
	private boolean guiHidden = false;
	public final SmoothedFloat blurTransition = new SmoothedFloat(2, 2, () -> {
		if (guiHidden) {
			return 0.0f;
		} else if (this.optionMenuOpen) {
			return 0.1f;
		} else {
			return (float) this.minecraft.options.getMenuBackgroundBlurriness();
		}
	}, notifier);
	private float guiButtonHoverTimer = 0.0f;
	private Button openFolderButton;
	private float backgroundInit = 0.0f;
	public final SmoothedFloat listTransition = new SmoothedFloat(1, 1, () -> {
		if (guiHidden || this.optionMenuOpen) {
			return 0.0f;
		} else {
			return backgroundInit;
		}
	}, notifier);

	public final SmoothedFloat buttonTransition = new SmoothedFloat(1, 1, () -> {
		if (guiHidden) {
			return 0.0f;
		} else {
			return backgroundInit;
		}
	}, notifier);
	private Button showHideButton;

	public ShaderPackScreen(Screen parent) {
		super(Component.translatable("options.pryzma.shaderPackSelection.title"));

		this.parent = parent;

		String modInfo = PryzmaShaders.MODNAME + " " + PryzmaShaders.getVersion();

		if (ShaderPlatformHelpers.getInstance().isDevelopmentEnvironment()) {
			this.developmentComponent = Component.literal("Development Environment").withStyle(ChatFormatting.GOLD);
		}

		this.modInfoComponent = Component.literal(modInfo).withStyle(ChatFormatting.GRAY);

		refreshForChangedPack();
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
		notifier.onNewFrame();
		backgroundInit = 1.0f;

		if (Screen.hasControlDown() && InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_D)) {
			Minecraft.getInstance().setScreen(new ConfirmScreen((option) -> {
				PryzmaShaders.setDebug(option);
				Minecraft.getInstance().setScreen(this);
			}, Component.literal("Shader debug mode toggle"),
				Component.literal("Debug mode helps investigate problems and shows shader errors. Would you like to enable it?"),
				Component.literal("Yes"),
				Component.literal("No")));
		}

		if (Screen.hasControlDown() && InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_G)) {
			Minecraft.getInstance().setScreen(new ConfirmScreen((option) -> {
				try {
					PryzmaShaders.getShaderConfig().setUnknown(option);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
				Minecraft.getInstance().setScreen(this);
			}, Component.literal("Unknown shader toggle"),
				Component.literal("This allows unknown shaders to load in."),
				Component.literal("Enable"),
				Component.literal("Disable")));
		}

		if (!this.guiHidden) {
			super.render(guiGraphics, mouseX, mouseY, delta);
		} else {
			this.renderBlurredBackground(delta);
			if (this.showHideButton != null) {
				this.showHideButton.render(guiGraphics, mouseX, mouseY, delta);
			}
		}

		float previousHoverTimer = this.guiButtonHoverTimer;
		if (previousHoverTimer == this.guiButtonHoverTimer) {
			this.guiButtonHoverTimer = 0.0f;
		}

		if (!this.guiHidden) {
			guiGraphics.drawCenteredString(this.font, this.title, (int) (this.width * 0.5), 8, 0xFFFFFF);

			if (notificationDialog != null && notificationDialogTimer > 0) {
				guiGraphics.drawCenteredString(this.font, notificationDialog, (int) (this.width * 0.5), 21, 0xFFFFFF);
			} else {
				if (optionMenuOpen) {
					guiGraphics.drawCenteredString(this.font, CONFIGURE_TITLE, (int) (this.width * 0.5), 21, 0xFFFFFF);
				} else {
					guiGraphics.drawCenteredString(this.font, SELECT_TITLE, (int) (this.width * 0.5), 21, 0xFFFFFF);
				}
			}

			if (!optionMenuOpen) {
				String gpuInfo = getGpuInfo();
				int textWidth = this.font.width(gpuInfo);
				int maxAvailableWidth = this.width - 16;
				int y = this.height - 62;
				if (textWidth <= maxAvailableWidth) {
					guiGraphics.drawCenteredString(this.font, gpuInfo, this.width / 2, y, 0xA0A0A0);
				} else {
					float scale = (float) maxAvailableWidth / (float) textWidth;
					var pose = guiGraphics.pose();
					pose.pushPose();
					pose.translate(this.width / 2.0f, y, 0);
					pose.scale(scale, scale, 1.0f);
					guiGraphics.drawCenteredString(this.font, gpuInfo, 0, 0, 0xA0A0A0);
					pose.popPose();
				}
			}

			// Draw the comment panel
			if (this.isDisplayingComment()) {
				// Determine panel height and position
				int panelHeight = Math.max(50, 18 + (this.hoveredElementCommentBody.size() * 10));
				int x = (int) (0.5 * this.width) - 157;
				int y = this.height - (panelHeight + 4);
				// Draw panel
				GuiUtil.drawPanel(guiGraphics, x, y, COMMENT_PANEL_WIDTH, panelHeight);
				// Draw text
				guiGraphics.drawString(font, this.hoveredElementCommentTitle.orElse(Component.empty()), x + 4, y + 4, 0xFFFFFF);
				for (int i = 0; i < this.hoveredElementCommentBody.size(); i++) {
					guiGraphics.drawString(font, this.hoveredElementCommentBody.get(i), x + 4, (y + 16) + (i * 10), 0xFFFFFF);
				}
			}
		}

		// Render everything queued to render last
		for (Runnable render : TOP_LAYER_RENDER_QUEUE) {
			render.run();
		}
		TOP_LAYER_RENDER_QUEUE.clear();

		if (this.developmentComponent != null) {
			guiGraphics.drawString(font, developmentComponent, 2, this.height - 10, 0xFFFFFF);
			guiGraphics.drawString(font, modInfoComponent, 2, this.height - 20, 0xFFFFFF);
		} else {
			guiGraphics.drawString(font, modInfoComponent, 2, this.height - 10, 0xFFFFFF);
		}
	}

	@Override
	protected void init() {
		super.init();
		int bottomCenter = this.width / 2 - 50;
		int topCenter = this.width / 2 - 76;
		boolean inWorld = this.minecraft.level != null;

		this.removeWidget(this.shaderPackList);
		this.removeWidget(this.shaderOptionList);

		this.shaderPackList = new ShaderPackSelectionList(this, this.minecraft, this.width, this.height, 32, this.height - 58 - 44, 0, this.width);

		if (PryzmaShaders.getCurrentPack().isPresent() && this.navigation != null) {
			ShaderPack currentPack = PryzmaShaders.getCurrentPack().get();

			this.shaderOptionList = new ShaderPackOptionList(this, this.navigation, currentPack, this.minecraft, this.width, this.height, 32, this.height - 58 - 44, 0, this.width);
			this.navigation.setActiveOptionList(this.shaderOptionList);

			this.shaderOptionList.rebuild();
		} else {
			optionMenuOpen = false;
			this.shaderOptionList = null;
		}

		this.clearWidgets();

		if (!this.guiHidden) {
			if (optionMenuOpen && shaderOptionList != null) {
				this.addRenderableWidget(shaderOptionList);
			} else {
				this.addRenderableWidget(shaderPackList);
			}

			this.addRenderableWidget(ShaderButton.pryzma$builder(CommonComponents.GUI_DONE, button -> onClose(), buttonTransition).bounds(bottomCenter + 104, this.height - 27, 100, 20
			).build());

			this.addRenderableWidget(ShaderButton.pryzma$builder(Component.translatable("options.pryzma.apply"), button -> this.applyChanges(), buttonTransition).bounds(bottomCenter, this.height - 27, 100, 20
			).build());

			this.addRenderableWidget(ShaderButton.pryzma$builder(CommonComponents.GUI_CANCEL, button -> this.dropChangesAndClose(), buttonTransition).bounds(bottomCenter - 104, this.height - 27, 100, 20
			).build());

			this.openFolderButton = ShaderButton.pryzma$builder(Component.translatable("options.pryzma.openShaderPackFolder"), button -> openShaderPackFolder(), buttonTransition).bounds(topCenter - 78, this.height - 51, 152, 20
			).build();
			this.addRenderableWidget(openFolderButton);

			this.screenSwitchButton = this.addRenderableWidget(ShaderButton.pryzma$builder(Component.translatable("options.pryzma.shaderPackList"), button -> {
					this.optionMenuOpen = !this.optionMenuOpen;

					// UX: Apply changes before switching screens to avoid unintuitive behavior
					//
					// Not doing this leads to unintuitive behavior, since selecting a pack in the
					// list (but not applying) would open the settings for the previous pack, rather
					// than opening the settings for the selected (but not applied) pack.
					this.applyChanges();
					setFocused(shaderPackList.getFocused());
					this.init();
				}
				, buttonTransition).bounds(topCenter + 78, this.height - 51, 152, 20
			).build());

			refreshScreenSwitchButton();
		}

		if (inWorld) {
			Component showOrHide = this.guiHidden
				? Component.translatable("options.pryzma.gui.show")
				: Component.translatable("options.pryzma.gui.hide");

			int buttonWidth = Math.max(76, this.font.width(showOrHide) + 16);
			int rightOfDone = this.width / 2 + 156;
			int x = this.width - buttonWidth - 10;
			int y = (this.guiHidden || this.width - buttonWidth - 10 >= rightOfDone)
				? this.height - 27
				: this.height - 51;

			this.showHideButton = Button.builder(showOrHide, button -> {
				this.guiHidden = !this.guiHidden;
				this.init();
			}).bounds(x, y, buttonWidth, 20).build();

			this.addRenderableWidget(showHideButton);
		}

		// NB: Don't let comment remain when exiting options screen
		// upstream issue #1494
		this.hoveredElement = null;
		this.hoveredElementCommentTimer = 0;
	}

	public void refreshForChangedPack() {
		if (PryzmaShaders.getCurrentPack().isPresent()) {
			ShaderPack currentPack = PryzmaShaders.getCurrentPack().get();

			this.navigation = new NavigationController(currentPack.getMenuContainer());

			if (this.shaderOptionList != null) {
				this.shaderOptionList.applyShaderPack(currentPack);
				this.shaderOptionList.rebuild();
			}
		} else {
			this.navigation = null;
		}

		refreshScreenSwitchButton();
	}

	public void refreshScreenSwitchButton() {
		if (this.screenSwitchButton != null) {
			this.screenSwitchButton.setMessage(
				optionMenuOpen ?
					Component.translatable("options.pryzma.shaderPackList")
					: Component.translatable("options.pryzma.shaderPackSettings")
			);
			this.screenSwitchButton.active = optionMenuOpen || shaderPackList.getTopButtonRow().shadersEnabled;
		}
	}

	private void processFixedBlur(float tick) {
		PostChain blurEffect = ((GameRendererAccessor) this.minecraft.gameRenderer).getBlurEffect();
		float g = Math.min(this.minecraft.options.getMenuBackgroundBlurriness(), this.blurTransition.getAsFloat());
		if (blurEffect != null && g >= 1.0F) {
			blurEffect.setUniform("Radius", g);
			blurEffect.process(tick);
		}
	}

	@Override
	protected void renderBlurredBackground(float pScreen0) {
		RenderSystem.disableDepthTest();
		processFixedBlur(pScreen0);
		this.minecraft.getMainRenderTarget().bindWrite(false);
	}

	@Override
	public void tick() {
		super.tick();

		if (this.notificationDialogTimer > 0) {
			this.notificationDialogTimer--;
		}

		if (this.hoveredElement != null) {
			this.hoveredElementCommentTimer++;
		} else {
			this.hoveredElementCommentTimer = 0;
		}
	}

	@Override
	public boolean keyPressed(int key, int j, int k) {
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			if (this.guiHidden) {
				this.guiHidden = false;
				this.init();

				return true;
			} else if (this.navigation != null && this.navigation.hasHistory()) {
				this.navigation.back();

				return true;
			} else if (this.optionMenuOpen) {
				this.optionMenuOpen = false;
				this.init();

				return true;
			}
		} else if (key == GLFW.GLFW_KEY_TAB) {
			if (!optionMenuOpen) {
				shaderPackList.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
			}

			this.optionMenuOpen = !this.optionMenuOpen;

			// UX: Apply changes before switching screens to avoid unintuitive behavior
			//
			// Not doing this leads to unintuitive behavior, since selecting a pack in the
			// list (but not applying) would open the settings for the previous pack, rather
			// than opening the settings for the selected (but not applied) pack.
			this.applyChanges();

			this.init();

			this.setFocused(null);
		} else if (key == GLFW.GLFW_KEY_F1 && this.showHideButton != null) {
			this.guiHidden = !guiHidden;
			this.init();
		}

		return this.guiHidden || super.keyPressed(key, j, k);
	}

	@Override
	public void onFilesDrop(List<Path> paths) {
		if (this.optionMenuOpen) {
			onOptionMenuFilesDrop(paths);
		} else {
			onPackListFilesDrop(paths);
		}
	}

	public void onPackListFilesDrop(List<Path> paths) {
		List<Path> packs = paths.stream().filter(PryzmaShaders::isValidShaderpack).toList();

		for (Path pack : packs) {
			String fileName = pack.getFileName().toString();

			try {
				PryzmaShaders.getShaderpacksDirectoryManager().copyPackIntoDirectory(fileName, pack);
			} catch (FileAlreadyExistsException e) {
				this.notificationDialog = Component.translatable(
					"options.pryzma.shaderPackSelection.copyErrorAlreadyExists",
					fileName
				).withStyle(ChatFormatting.ITALIC, ChatFormatting.RED);

				this.notificationDialogTimer = 100;
				this.shaderPackList.refresh();

				return;
			} catch (IOException e) {
				PryzmaShaders.logger.warn("Error copying dragged shader pack", e);

				this.notificationDialog = Component.translatable(
					"options.pryzma.shaderPackSelection.copyError",
					fileName
				).withStyle(ChatFormatting.ITALIC, ChatFormatting.RED);

				this.notificationDialogTimer = 100;
				this.shaderPackList.refresh();

				return;
			}
		}

		// After copying the relevant files over to the folder, make sure to refresh the shader pack list.
		this.shaderPackList.refresh();

		if (packs.isEmpty()) {
			// If zero packs were added, then notify the user that the files that they added weren't actually shader
			// packs.

			if (paths.size() == 1) {
				// If a single pack could not be added, provide a message with that pack in the file name
				String fileName = paths.getFirst().getFileName().toString();

				this.notificationDialog = Component.translatable(
					"options.pryzma.shaderPackSelection.failedAddSingle",
					fileName
				).withStyle(ChatFormatting.ITALIC, ChatFormatting.RED);
			} else {
				// Otherwise, show a generic message.

				this.notificationDialog = Component.translatable(
					"options.pryzma.shaderPackSelection.failedAdd"
				).withStyle(ChatFormatting.ITALIC, ChatFormatting.RED);
			}

		} else if (packs.size() == 1) {
			// In most cases, users will drag a single pack into the selection menu. So, let's special case it.
			String packName = packs.getFirst().getFileName().toString();

			this.notificationDialog = Component.translatable(
				"options.pryzma.shaderPackSelection.addedPack",
				packName
			).withStyle(ChatFormatting.ITALIC, ChatFormatting.YELLOW);

			// Select the pack that the user just added, since if a user just dragged a pack in, they'll probably want
			// to actually use that pack afterwards.
			this.shaderPackList.select(packName);
		} else {
			// We also support multiple packs being dragged and dropped at a time. Just show a generic success message
			// in that case.
			this.notificationDialog = Component.translatable(
				"options.pryzma.shaderPackSelection.addedPacks",
				packs.size()
			).withStyle(ChatFormatting.ITALIC, ChatFormatting.YELLOW);
		}

		// Show the relevant message for 5 seconds (100 ticks)
		this.notificationDialogTimer = 100;
	}

	public void displayNotification(Component component) {
		this.notificationDialog = component;
		this.notificationDialogTimer = 100;
	}

	public void onOptionMenuFilesDrop(List<Path> paths) {
		// If more than one option file has been dragged, display an error
		// as only one option file should be imported at a time
		if (paths.size() != 1) {
			this.notificationDialog = Component.translatable(
				"options.pryzma.shaderPackOptions.tooManyFiles"
			).withStyle(ChatFormatting.ITALIC, ChatFormatting.RED);
			this.notificationDialogTimer = 100; // 5 seconds (100 ticks)

			return;
		}

		this.importPackOptions(paths.getFirst());
	}

	public void importPackOptions(Path settingFile) {
		try (InputStream in = Files.newInputStream(settingFile)) {
			Properties properties = new Properties();
			properties.load(in);

			PryzmaShaders.queueShaderPackOptionsFromProperties(properties);

			this.notificationDialog = Component.translatable(
				"options.pryzma.shaderPackOptions.importedSettings",
				settingFile.getFileName().toString()
			).withStyle(ChatFormatting.ITALIC, ChatFormatting.YELLOW);
			this.notificationDialogTimer = 100; // 5 seconds (100 ticks)

			if (this.navigation != null) {
				this.navigation.refresh();
			}
		} catch (Exception e) {
			// If the file could not be properly parsed or loaded,
			// log the error and display a message to the user
			PryzmaShaders.logger.error("Error importing shader settings file \"" + settingFile.toString() + "\"", e);

			this.notificationDialog = Component.translatable(
				"options.pryzma.shaderPackOptions.failedImport",
				settingFile.getFileName().toString()
			).withStyle(ChatFormatting.ITALIC, ChatFormatting.RED);
			this.notificationDialogTimer = 100; // 5 seconds (100 ticks)
		}
	}

	@Override
	public void onClose() {
		if (!dropChanges) {
			applyChanges();
		} else {
			discardChanges();
		}

		try {
			shaderPackList.close();
		} catch (IOException e) {
			PryzmaShaders.logger.error("Failed to safely close shaderpack selection!", e);
		}

		this.minecraft.setScreen(parent);
	}

	private void dropChangesAndClose() {
		dropChanges = true;
		onClose();
	}

	public void applyChanges() {
		ShaderPackSelectionList.BaseEntry base = this.shaderPackList.getSelected();
		boolean enabled = this.shaderPackList.getTopButtonRow().shadersEnabled;
		boolean previousShadersEnabled = PryzmaShaders.getShaderConfig().areShadersEnabled();

		if (enabled != previousShadersEnabled) {
			ShaderApi.getInstance().getConfig().setShadersEnabledAndApply(enabled);
		}

		if (!(base instanceof ShaderPackSelectionList.ShaderPackEntry entry)) {
			return;
		}

		this.shaderPackList.setApplied(entry);

		String name = entry.getPackName();

		// If the pack is being changed, clear pending options from the previous pack to
		// avoid possible undefined behavior from applying one pack's options to another pack
		if (!name.equals(PryzmaShaders.getCurrentPackName())) {
			PryzmaShaders.clearShaderPackOptionQueue();
		}

		String previousPackName = PryzmaShaders.getShaderConfig().getShaderPackName().orElse(null);

		// Only reload if the pack would be different from before, or shaders were toggled, or options were changed, or if we're about to reset options.
		if (!name.equals(previousPackName) || !PryzmaShaders.getShaderPackOptionQueue().isEmpty() || PryzmaShaders.shouldResetShaderPackOptionsOnNextReload()) {
			PryzmaShaders.getShaderConfig().setShaderPackName(name);
			ShaderApi.getInstance().getConfig().setShadersEnabledAndApply(enabled);
		}

		refreshForChangedPack();
	}

	private void discardChanges() {
		PryzmaShaders.clearShaderPackOptionQueue();
	}

	private void openShaderPackFolder() {
		CompletableFuture.runAsync(() -> Util.getPlatform().openUri(PryzmaShaders.getShaderpacksDirectoryManager().getDirectoryUri()));
	}

	// Let the screen know if an element is hovered or not, allowing for accurately updating which element is hovered
	public void setElementHoveredStatus(AbstractElementWidget<?> widget, boolean hovered) {
		if (hovered && widget != this.hoveredElement) {
			this.hoveredElement = widget;

			if (widget instanceof CommentedElementWidget) {
				this.hoveredElementCommentTitle = ((CommentedElementWidget<?>) widget).getCommentTitle();

				Optional<Component> commentBody = ((CommentedElementWidget<?>) widget).getCommentBody();
				if (commentBody.isEmpty()) {
					this.hoveredElementCommentBody.clear();
				} else {
					String rawCommentBody = commentBody.get().getString();

					// Strip any trailing "."s
					if (rawCommentBody.endsWith(".")) {
						rawCommentBody = rawCommentBody.substring(0, rawCommentBody.length() - 1);
					}
					// Split comment body into lines by separator ". "
					List<MutableComponent> splitByPeriods = Arrays.stream(rawCommentBody.split("\\. [ ]*")).map(Component::literal).toList();
					// Line wrap
					this.hoveredElementCommentBody = new ArrayList<>();
					for (MutableComponent text : splitByPeriods) {
						this.hoveredElementCommentBody.addAll(this.font.split(text, COMMENT_PANEL_WIDTH - 8));
					}
				}
			} else {
				this.hoveredElementCommentTitle = Optional.empty();
				this.hoveredElementCommentBody.clear();
			}

			this.hoveredElementCommentTimer = 0;
		} else if (!hovered && widget == this.hoveredElement) {
			this.hoveredElement = null;
			this.hoveredElementCommentTitle = Optional.empty();
			this.hoveredElementCommentBody.clear();
			this.hoveredElementCommentTimer = 0;
		}
	}

	public boolean isDisplayingComment() {
		return this.hoveredElementCommentTimer > 20 &&
			this.hoveredElementCommentTitle.isPresent() &&
			!this.hoveredElementCommentBody.isEmpty();
	}

	public Button getBottomRowOption() {
		return openFolderButton;
	}
}
