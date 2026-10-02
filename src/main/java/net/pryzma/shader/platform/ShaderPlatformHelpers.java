package net.pryzma.shader.platform;

import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.file.Path;

public interface ShaderPlatformHelpers {
	// Pryzma ships one platform; no service lookup.
	ShaderPlatformHelpers INSTANCE = new ShaderForgeHelpers();

	static ShaderPlatformHelpers getInstance() {
		return INSTANCE;
	}

	boolean isModLoaded(String modId);

	String getVersion();

	boolean isDevelopmentEnvironment();

	Path getGameDir();

	Path getConfigDir();

	int compareVersions(String currentVersion, String semanticVersion) throws Exception;

	KeyMapping registerKeyBinding(KeyMapping keyMapping);

	boolean useELS();

    BlockState getBlockAppearance(BlockAndTintGetter level, BlockState state, Direction cullFace, BlockPos pos);
}
