package net.pryzma.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.pryzma.world.PrPregenTask;

/**
 * Command dispatcher for {@code /pryzma}.
 */
public final class PrCommands {
    private PrCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("pryzma")
                .requires(source -> source.getServer().isSingleplayer() || source.hasPermission(2));

        // Subcommand: pregen
        root.then(Commands.literal("pregen")
                // /pryzma pregen <radius> [mode]
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 256))
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("full");
                                    builder.suggest("lod");
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> startPregen(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"),
                                        StringArgumentType.getString(ctx, "mode"))))
                        .executes(ctx -> startPregen(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"), "full")))
                // /pryzma pregen start <radius> [mode]
                .then(Commands.literal("start")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 256))
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            builder.suggest("full");
                                            builder.suggest("lod");
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> startPregen(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"),
                                                StringArgumentType.getString(ctx, "mode"))))
                                .executes(ctx -> startPregen(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"), "full"))))
                // /pryzma pregen stop
                .then(Commands.literal("stop")
                        .executes(ctx -> stopPregen(ctx.getSource())))
                // /pryzma pregen cancel
                .then(Commands.literal("cancel")
                        .executes(ctx -> stopPregen(ctx.getSource())))
                // /pryzma pregen status
                .then(Commands.literal("status")
                        .executes(ctx -> statusPregen(ctx.getSource())))
                // /pryzma pregen without arguments -> status
                .executes(ctx -> statusPregen(ctx.getSource()))
        );

        // Subcommand: help
        root.then(Commands.literal("help")
                .executes(ctx -> sendHelp(ctx.getSource())));

        // Fallback root executes: help
        root.executes(ctx -> sendHelp(ctx.getSource()));

        dispatcher.register(root);
    }

    private static int startPregen(CommandSourceStack source, int radius, String modeStr) {
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());
        ChunkPos origin = new ChunkPos(pos);
        PrPregenTask.Mode mode = "lod".equalsIgnoreCase(modeStr) || "surface".equalsIgnoreCase(modeStr)
                ? PrPregenTask.Mode.LOD
                : PrPregenTask.Mode.FULL;
        PrPregenTask.start(source, level, origin.x, origin.z, radius, mode);
        return Command.SINGLE_SUCCESS;
    }

    private static int stopPregen(CommandSourceStack source) {
        PrPregenTask.stop(source);
        return Command.SINGLE_SUCCESS;
    }

    private static int statusPregen(CommandSourceStack source) {
        PrPregenTask.status(source);
        return Command.SINGLE_SUCCESS;
    }

    private static int sendHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.empty()
                .append(Component.translatable("pryzma.command.help.header"))
                .append("\n")
                .append(Component.translatable("pryzma.command.help.pregen"))
                .append("\n")
                .append(Component.translatable("pryzma.command.help.stop"))
                .append("\n")
                .append(Component.translatable("pryzma.command.help.status")), false);
        return Command.SINGLE_SUCCESS;
    }
}
