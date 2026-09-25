package net.pryzma.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.pryzma.PryzmaConfig;
import net.pryzma.mixin.LevelRendererStatsAccessor;

/**
 * The Pryzma Quick Info HUD: frame rate, chunks, entities and position on the left, memory and
 * the targeted block, fluid and entity on the right, in place of the F3 screen. Lines are rebuilt
 * ten times a second, as 1.x cached them.
 */
public final class PrQuickInfo {
    private static final int TEXT = 0xFFE0E0E0;
    private static final int BACKGROUND = 0x90505050;
    private static final long REBUILD_NS = 100_000_000L;

    private static List<String> left = List.of();
    private static List<String> right = List.of();
    private static long builtAt;

    private PrQuickInfo() {
    }

    public static void onFrameStart(RenderFrameEvent.Pre event) {
        if (PryzmaConfig.prQuickInfo && PryzmaConfig.prQuickInfoGpu) {
            PrQuickInfoStats.onFrameStart();
        }
    }

    public static void onFrameEnd(RenderFrameEvent.Post event) {
        if (PryzmaConfig.prQuickInfo) {
            PrQuickInfoStats.onFrameRendered();
            PrQuickInfoStats.onFrameEnd();
        }
    }

    /** GUI layer: drawn where the debug screen goes, while it is closed. */
    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!PryzmaConfig.prQuickInfo || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()
                || mc.level == null || mc.getCameraEntity() == null) {
            return;
        }
        long now = System.nanoTime();
        if (now - builtAt >= REBUILD_NS) {
            builtAt = now;
            left = buildLeft(mc);
            right = buildRight(mc);
        }
        draw(g, mc, left, false);
        draw(g, mc, right, true);
    }

    private static void draw(GuiGraphics g, Minecraft mc, List<String> lines, boolean alignRight) {
        if (lines.isEmpty()) {
            return;
        }
        if (PryzmaConfig.prQuickInfoBackground) {
            int y = 2;
            for (String s : lines) {
                int w = mc.font.width(s);
                int x = alignRight ? g.guiWidth() - 2 - w : 2;
                g.fill(x - 1, y - 1, x + w + 1, y + 8, BACKGROUND);
                y += 9;
            }
        }
        int y = 2;
        for (String s : lines) {
            int w = mc.font.width(s);
            g.drawString(mc.font, s, alignRight ? g.guiWidth() - 2 - w : 2, y, TEXT, false);
            y += 9;
        }
    }

    private static List<String> buildLeft(Minecraft mc) {
        List<String> lines = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean full = PryzmaConfig.prQuickInfoLabels != PryzmaConfig.VALUE_COMPACT;
        boolean detailed = PryzmaConfig.prQuickInfoLabels == PryzmaConfig.VALUE_DETAILED;
        if (PryzmaConfig.prQuickInfoFps != PryzmaConfig.VALUE_OFF) {
            next(sb).append(mc.getFps());
            if (PryzmaConfig.prQuickInfoFps == PryzmaConfig.VALUE_FULL) {
                sb.append('/').append(PrQuickInfoStats.fpsMin());
            }
            sb.append(" fps");
        }
        if (PryzmaConfig.prQuickInfoChunks) {
            next(sb).append(full ? "Chunks: " : "C: ").append(mc.levelRenderer.countRenderedSections());
        }
        if (PryzmaConfig.prQuickInfoEntities) {
            next(sb).append(full ? "Entities: " : "E: ")
                    .append(((LevelRendererStatsAccessor) mc.levelRenderer).prGetRenderedEntities())
                    .append('+').append(PrQuickInfoStats.blockEntitiesRendered);
        }
        if (full) {
            newLine(lines, sb);
        }
        if (PryzmaConfig.prQuickInfoParticles) {
            next(sb).append(full ? "Particles: " : "P: ").append(mc.particleEngine.countParticles());
        }
        if (PryzmaConfig.prQuickInfoUpdates) {
            next(sb).append(full ? "Updates: " : "U: ").append(PrQuickInfoStats.updatesPerSecond());
        }
        if (PryzmaConfig.prQuickInfoGpu) {
            next(sb).append(full ? "GPU: " : "G: ").append(Mth.clamp((int) Math.round(PrQuickInfoStats.gpuLoad() * 100.0), 0, 100)).append('%');
        }
        newLine(lines, sb);

        Entity camera = mc.getCameraEntity();
        BlockPos pos = camera.blockPosition();
        boolean reduced = mc.showOnlyReducedInfo();
        if (PryzmaConfig.prQuickInfoPos != PryzmaConfig.VALUE_OFF) {
            boolean relative = PryzmaConfig.prQuickInfoPos == PryzmaConfig.VALUE_FULL;
            if (!reduced || relative) {
                next(sb).append(full ? "Position: " : "Pos: ");
            }
            if (!reduced) {
                if (detailed) {
                    sb.append(" (").append(decimal3(camera.getX())).append(", ").append(decimal3(camera.getY()))
                            .append(", ").append(decimal3(camera.getZ())).append(')');
                } else {
                    sb.append(pos.getX()).append(", ").append(pos.getY()).append(", ").append(pos.getZ());
                }
            }
            if (relative) {
                sb.append(" [").append(pos.getX() & 15).append(", ").append(pos.getY() & 15).append(", ")
                        .append(pos.getZ() & 15).append(']');
            }
        }
        newLine(lines, sb);
        if (!reduced) {
            if (PryzmaConfig.prQuickInfoFacing != PryzmaConfig.VALUE_OFF) {
                Direction dir = camera.getDirection();
                next(sb).append(full ? "Facing: " : "F: ").append(dir).append(" [").append(axis(dir)).append(']');
                if (PryzmaConfig.prQuickInfoFacing == PryzmaConfig.VALUE_FULL) {
                    float yaw = Mth.wrapDegrees(camera.getYRot());
                    float pitch = Mth.wrapDegrees(camera.getXRot());
                    sb.append(" (").append(detailed ? decimal1(yaw) : Integer.toString(Math.round(yaw))).append('/')
                            .append(detailed ? decimal1(pitch) : Integer.toString(Math.round(pitch))).append(')');
                }
            }
            newLine(lines, sb);
            if (PryzmaConfig.prQuickInfoBiome) {
                String biome = mc.level.getBiome(pos).unwrapKey().map(k -> name(k.location())).orElse("[unregistered]");
                next(sb).append(full ? "Biome: " : "B: ").append(biome);
            }
            if (PryzmaConfig.prQuickInfoLight) {
                int sky = mc.level.getBrightness(LightLayer.SKY, pos);
                int block = mc.level.getBrightness(LightLayer.BLOCK, pos);
                next(sb).append(full ? "Light: " + sky + " sky, " + block + " block" : "L: " + sky + "/" + block);
            }
            newLine(lines, sb);
        }
        return lines;
    }

    private static List<String> buildRight(Minecraft mc) {
        List<String> lines = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean full = PryzmaConfig.prQuickInfoLabels != PryzmaConfig.VALUE_COMPACT;
        boolean detailed = PryzmaConfig.prQuickInfoLabels == PryzmaConfig.VALUE_DETAILED;
        if (PryzmaConfig.prQuickInfoMemory != PryzmaConfig.VALUE_OFF) {
            Runtime rt = Runtime.getRuntime();
            next(sb).append(full ? "Memory: " : "M: ").append(mb(rt.totalMemory() - rt.freeMemory())).append('/')
                    .append(mb(rt.maxMemory())).append(full ? " MB" : "");
            if (PryzmaConfig.prQuickInfoMemory == PryzmaConfig.VALUE_FULL) {
                next(sb).append(full ? "Allocation: " : "A: ").append((int) PrQuickInfoStats.allocationMbPerSecond())
                        .append(full ? " MB/s" : "");
            }
        }
        newLine(lines, sb);
        if (PryzmaConfig.prQuickInfoNativeMemory != PryzmaConfig.VALUE_OFF) {
            next(sb).append(full ? "Native: " : "N: ").append(mb(PrQuickInfoStats.directBytes())).append('/')
                    .append(mb(Runtime.getRuntime().maxMemory())).append('+').append(mb(PrQuickInfoStats.IMAGE_BYTES.get()))
                    .append(full ? " MB" : "");
        }
        newLine(lines, sb);
        if (!mc.showOnlyReducedInfo() && mc.player != null) {
            Entity camera = mc.getCameraEntity();
            double reach = mc.player.blockInteractionRange();
            if (PryzmaConfig.prQuickInfoTargetBlock != PryzmaConfig.VALUE_OFF
                    && camera.pick(reach, 0.0F, false) instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
                BlockPos p = hit.getBlockPos();
                next(sb).append(full ? "Target Block: " : "TB: ")
                        .append(name(BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(p).getBlock())));
                if (PryzmaConfig.prQuickInfoTargetBlock == PryzmaConfig.VALUE_FULL) {
                    sb.append(" (").append(p.getX()).append(", ").append(p.getY()).append(", ").append(p.getZ()).append(')');
                }
            }
            newLine(lines, sb);
            if (PryzmaConfig.prQuickInfoTargetFluid != PryzmaConfig.VALUE_OFF
                    && camera.pick(reach, 0.0F, true) instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
                BlockPos p = hit.getBlockPos();
                Fluid fluid = mc.level.getBlockState(p).getFluidState().getType();
                if (fluid != Fluids.EMPTY) {
                    next(sb).append(full ? "Target Fluid: " : "TF: ").append(name(BuiltInRegistries.FLUID.getKey(fluid)));
                    if (PryzmaConfig.prQuickInfoTargetFluid == PryzmaConfig.VALUE_FULL) {
                        sb.append(" (").append(p.getX()).append(", ").append(p.getY()).append(", ").append(p.getZ()).append(')');
                    }
                }
            }
            newLine(lines, sb);
            Entity target = mc.crosshairPickEntity;
            if (PryzmaConfig.prQuickInfoTargetEntity != PryzmaConfig.VALUE_OFF && target != null) {
                next(sb).append(full ? "Target Entity: " : "TE: ").append(name(BuiltInRegistries.ENTITY_TYPE.getKey(target.getType())));
                if (PryzmaConfig.prQuickInfoTargetEntity == PryzmaConfig.VALUE_FULL) {
                    if (detailed) {
                        sb.append(" (").append(decimal3(target.getX())).append(", ").append(decimal3(target.getY())).append(", ")
                                .append(decimal3(target.getZ())).append(')');
                    } else {
                        BlockPos p = target.blockPosition();
                        sb.append(" (").append(p.getX()).append(", ").append(p.getY()).append(", ").append(p.getZ()).append(')');
                    }
                }
            }
            newLine(lines, sb);
        }
        return lines;
    }

    private static StringBuilder next(StringBuilder sb) {
        if (!sb.isEmpty()) {
            sb.append(", ");
        }
        return sb;
    }

    private static void newLine(List<String> lines, StringBuilder sb) {
        if (!sb.isEmpty()) {
            lines.add(sb.toString());
            sb.setLength(0);
        }
    }

    private static String axis(Direction dir) {
        return switch (dir) {
            case NORTH -> "Z-";
            case SOUTH -> "Z+";
            case WEST -> "X-";
            case EAST -> "X+";
            case UP -> "Y+";
            case DOWN -> "Y-";
        };
    }

    private static String name(ResourceLocation id) {
        return id == null ? "" : id.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE) ? id.getPath() : id.toString();
    }

    private static int mb(long bytes) {
        return (int) (bytes / 1024L / 1024L);
    }

    private static String decimal1(double v) {
        return Double.toString(Math.round(v * 10.0) / 10.0);
    }

    /** Three decimals, padded as 1.x did ("1.5" becomes "1.500"). */
    private static String decimal3(double v) {
        StringBuilder sb = new StringBuilder(Double.toString(Math.round(v * 1000.0) / 1000.0));
        if (sb.charAt(sb.length() - 2) == '.') {
            sb.append('0');
        }
        if (sb.charAt(sb.length() - 3) == '.') {
            sb.append('0');
        }
        return sb.toString();
    }
}
