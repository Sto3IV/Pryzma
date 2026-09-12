package net.pryzma.neoforge;

/*
 * Celian: Who the hell wrote this?
 * Ranni: Oh wait, it was me.
 */

import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformer.Target;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TargetType;
import cpw.mods.modlauncher.api.TransformerVoteResult;

/**
 * ModLauncher transformer targeting {@code net.minecraft.client.gui.screens.PauseScreen}.
 *
 * <p>Surgically modifies {@code createPauseMenu()V}:
 * <ol>
 *   <li>Conditionally bypasses {@code addFeedbackButtons(Screen, RowHelper)} when
 *       {@code net.pryzma.Config.isFeedbackButtons()} is false, allowing {@code GridLayout}
 *       to naturally collapse the pause menu from 190 px to 166 px without ghost widgets.</li>
 *   <li>Redirects {@code RowHelper.addChild(LayoutElement)} for {@code FEEDBACK_SUBSCREEN} to
 *       {@code net.pryzma.util.PauseScreenHelper.addFeedbackSubscreen}, discarding the button
 *       when feedback buttons are disabled.</li>
 *   <li>Redirects {@code RowHelper.addChild(LayoutElement)} for {@code SERVER_LINKS} to
 *       {@code net.pryzma.util.PauseScreenHelper.addServerLinks}, widening the Server Links
 *       button to span 2 (204 px) when feedback buttons are disabled to prevent empty grid holes.</li>
 * </ol>
 */
public class PauseScreenTransformer implements ITransformer<ClassNode> {
    private static final Logger LOGGER = LoggerFactory.getLogger("Pryzma");
    private static final String TARGET = "net.minecraft.client.gui.screens.PauseScreen";

    private static final String PAUSE_SCREEN = "net/minecraft/client/gui/screens/PauseScreen";
    private static final String ROW_HELPER = "net/minecraft/client/gui/layouts/GridLayout$RowHelper";
    private static final String ADD_FEEDBACK_BUTTONS = "addFeedbackButtons";
    private static final String ADD_FEEDBACK_DESC = "(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/gui/layouts/GridLayout$RowHelper;)V";
    private static final String ADD_CHILD = "addChild";
    private static final String ADD_CHILD_DESC = "(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;";

    private static final String CONFIG = "net/pryzma/Config";
    private static final String IS_FEEDBACK = "isFeedbackButtons";
    private static final String IS_FEEDBACK_DESC = "()Z";

    private static final String HELPER = "net/pryzma/util/PauseScreenHelper";
    private static final String HELPER_FEEDBACK_SUBSCREEN = "addFeedbackSubscreen";
    private static final String HELPER_SERVER_LINKS = "addServerLinks";
    private static final String HELPER_CHILD_DESC = "(Lnet/minecraft/client/gui/layouts/GridLayout$RowHelper;Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;";

    public static boolean inject(ClassNode node) {
        MethodNode createPauseMenu = null;
        for (MethodNode m : node.methods) {
            if ("createPauseMenu".equals(m.name) && "()V".equals(m.desc)) {
                createPauseMenu = m;
                break;
            }
        }
        if (createPauseMenu == null) {
            LOGGER.warn("PauseScreenTransformer: createPauseMenu()V method not found in {}", node.name);
            return false;
        }

        int edits = 0;
        InsnList instructions = createPauseMenu.instructions;

        // 1. Wrap addFeedbackButtons with Config.isFeedbackButtons() check
        for (AbstractInsnNode insn : instructions.toArray()) {
            if (insn.getOpcode() == Opcodes.INVOKESTATIC && insn instanceof MethodInsnNode minsn) {
                if (PAUSE_SCREEN.equals(minsn.owner) && ADD_FEEDBACK_BUTTONS.equals(minsn.name) && ADD_FEEDBACK_DESC.equals(minsn.desc)) {
                    LabelNode lSkip = new LabelNode();
                    LabelNode lEnd = new LabelNode();

                    InsnList wrap = new InsnList();
                    wrap.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CONFIG, IS_FEEDBACK, IS_FEEDBACK_DESC, false));
                    wrap.add(new JumpInsnNode(Opcodes.IFEQ, lSkip));
                    wrap.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PAUSE_SCREEN, ADD_FEEDBACK_BUTTONS, ADD_FEEDBACK_DESC, false));
                    wrap.add(new JumpInsnNode(Opcodes.GOTO, lEnd));
                    wrap.add(lSkip);
                    wrap.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
                    wrap.add(new InsnNode(Opcodes.POP2));
                    wrap.add(lEnd);
                    wrap.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));

                    instructions.insert(insn, wrap);
                    instructions.remove(insn);
                    edits++;
                    LOGGER.info("PauseScreenTransformer: wrapped addFeedbackButtons with Config.isFeedbackButtons check");
                    break;
                }
            }
        }

        // 2. Locate FEEDBACK_SUBSCREEN and SERVER_LINKS and redirect their RowHelper.addChild calls
        AbstractInsnNode feedbackAddChild = findAddChildAfterField(instructions, "FEEDBACK_SUBSCREEN");
        if (feedbackAddChild != null) {
            MethodInsnNode redirectFeedback = new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    HELPER,
                    HELPER_FEEDBACK_SUBSCREEN,
                    HELPER_CHILD_DESC,
                    false
            );
            instructions.set(feedbackAddChild, redirectFeedback);
            edits++;
            LOGGER.info("PauseScreenTransformer: redirected FEEDBACK_SUBSCREEN addChild to PauseScreenHelper.addFeedbackSubscreen");
        }

        AbstractInsnNode serverLinksAddChild = findAddChildAfterField(instructions, "SERVER_LINKS");
        if (serverLinksAddChild != null) {
            MethodInsnNode redirectLinks = new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    HELPER,
                    HELPER_SERVER_LINKS,
                    HELPER_CHILD_DESC,
                    false
            );
            instructions.set(serverLinksAddChild, redirectLinks);
            edits++;
            LOGGER.info("PauseScreenTransformer: redirected SERVER_LINKS addChild to PauseScreenHelper.addServerLinks");
        }

        LOGGER.info("PauseScreenTransformer applied {} edits to {}.createPauseMenu()", edits, node.name);
        return edits > 0;
    }

    private static AbstractInsnNode findAddChildAfterField(InsnList instructions, String fieldName) {
        for (AbstractInsnNode insn = instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == Opcodes.GETSTATIC && insn instanceof FieldInsnNode fn) {
                if (PAUSE_SCREEN.equals(fn.owner) && fieldName.equals(fn.name)) {
                    // Scan forward to find the next RowHelper.addChild(LayoutElement) call
                    for (AbstractInsnNode next = fn.getNext(); next != null; next = next.getNext()) {
                        if (next.getOpcode() == Opcodes.INVOKEVIRTUAL && next instanceof MethodInsnNode minsn) {
                            if (ROW_HELPER.equals(minsn.owner) && ADD_CHILD.equals(minsn.name) && ADD_CHILD_DESC.equals(minsn.desc)) {
                                return next;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    public TargetType<ClassNode> getTargetType() {
        return TargetType.CLASS;
    }

    @Override
    public Set<Target<ClassNode>> targets() {
        return Set.of(Target.targetClass(TARGET));
    }

    @Override
    public TransformerVoteResult castVote(ITransformerVotingContext context) {
        return TransformerVoteResult.YES;
    }

    @Override
    public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
        LOGGER.info("Transforming {} with PauseScreenTransformer", input.name);
        inject(input);
        return input;
    }
}
