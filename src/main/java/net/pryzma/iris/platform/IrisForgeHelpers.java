package net.pryzma.iris.platform;

import net.pryzma.Pryzma;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.LoadingModList;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class IrisForgeHelpers implements IrisPlatformHelpers {
	/** Iris port version shown next to Pryzma's own. */
	public static final String IRIS_BASE_VERSION = "1.8.14";
	/**
	 * Key mappings created before mods are constructed ({@code Options.load}); Pryzma hands them to
	 * {@code RegisterKeyMappingsEvent}.
	 */
	public static final List<KeyMapping> KEYLIST = new ArrayList<>();

	@Override
	public boolean isModLoaded(String modId) {
		return LoadingModList.get().getModFileById(modId) != null;
	}

	boolean HAS_CAMO = isModLoaded("cable_facades");

	@Override
	public String getVersion() {
		var list = LoadingModList.get();
		var modFile = list != null ? list.getModFileById(Pryzma.MODID) : null;
		String ver = (modFile != null && !modFile.getMods().isEmpty())
				? modFile.getMods().get(0).getVersion().toString()
				: "2.1.0";
		return ver + " (Iris " + IRIS_BASE_VERSION + ")";
	}

	@Override
	public boolean isDevelopmentEnvironment() {
		return !FMLLoader.isProduction();
	}

	@Override
	public Path getGameDir() {
		return FMLPaths.GAMEDIR.get();
	}

	@Override
	public Path getConfigDir() {
		return FMLPaths.CONFIGDIR.get();
	}

	@Override
	public int compareVersions(String currentVersion, String semanticVersion) throws Exception {
		return new DefaultArtifactVersion(currentVersion).compareTo(new DefaultArtifactVersion(semanticVersion));
	}

	@Override
	public KeyMapping registerKeyBinding(KeyMapping keyMapping) {
		KEYLIST.add(keyMapping);
		return keyMapping;
	}

	@Override
	public boolean useELS() {
		return true;
	}

	// TODO find a way to do this without breaking Cable Facades...
	@Override
	public BlockState getBlockAppearance(BlockAndTintGetter level, BlockState state, Direction cullFace, BlockPos pos) {
		return state;
	}
}
