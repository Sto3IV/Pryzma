/**
 * Pryzma's shader pack pipeline: a port of Iris 1.8.14 for Minecraft 1.21.1 (NeoForge), relocated from
 * {@code net.irisshaders.iris}. LGPL-3.0; see {@code META-INF/LICENSE-IRIS} and {@code META-INF/NOTICE-IRIS}.
 *
 * <p>Differences from Iris: no Sodium (terrain uses the vanilla chunk path, completed with block ids and light
 * emission), no Distant Horizons, no update checker, and the vanilla shadow pass collects its sections
 * directly instead of re-running the occlusion graph.
 */
package net.pryzma.iris;
