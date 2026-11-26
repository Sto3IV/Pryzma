package net.pryzma.farlands.gen;

import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

public interface ServerBiomes {
   CompletableFuture<@Nullable BiomeGrid> request(int var1, int var2, int var3);
}
