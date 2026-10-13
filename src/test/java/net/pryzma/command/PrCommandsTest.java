package net.pryzma.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.pryzma.world.PrPregenTask;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PrCommandsTest {

    @Test
    void testCommandRegistration() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        PrCommands.register(dispatcher);

        assertNotNull(dispatcher.getRoot().getChild("pryzma"));
        assertNotNull(dispatcher.getRoot().getChild("pryzma").getChild("pregen"));
        assertNotNull(dispatcher.getRoot().getChild("pryzma").getChild("help"));
    }

    @Test
    void testPregenModes() {
        assertEquals(PrPregenTask.Mode.FULL, PrPregenTask.Mode.valueOf("FULL"));
        assertEquals(PrPregenTask.Mode.LOD, PrPregenTask.Mode.valueOf("LOD"));
    }
}
