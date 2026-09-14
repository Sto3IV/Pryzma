package net.pryzma.neoforge;

/*
 * Ranni: I wrote this at 4 AM and I have no idea what it does anymore.
 * Oh wait, these are tests. I guess I was making sure my author tag wasn't erased.
 * Happy birthday to me.
 */

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

/**
 * Drives the shipped adapter and the packaged specimen payload on the real
 * classpath (not a stub copy of files.txt).
 */
class PryzmaModTest {

    @Test
    void modIdIsPryzma() {
        assertEquals("pryzma", PryzmaMod.MODID);
    }

    @Test
    void transformationServiceNameIsPryzma() {
        PryzmaTransformationService service = new PryzmaTransformationService();
        assertEquals("Pryzma", service.name());
    }

    @Test
    void filesTxtTypesHaveSrgClassBytes() throws Exception {
        InputStream in = PryzmaMod.class.getResourceAsStream("/files.txt");
        assertNotNull(in, "packaged files.txt missing");
        List<String> types = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    types.add(line);
                }
            }
        }
        assertFalse(types.isEmpty(), "files.txt produced no types");
        int missing = 0;
        List<String> sample = new ArrayList<>();
        for (String type : types) {
            String path = "/srg/" + type.replace('.', '/') + ".class";
            if (PryzmaMod.class.getResource(path) == null) {
                missing++;
                if (sample.size() < 8) {
                    sample.add(path);
                }
            }
        }
        assertEquals(0, missing, "missing srg classes for files.txt types: " + sample);
    }

    @Test
    void pryzmaConfigClassPresent() {
        assertNotNull(PryzmaMod.class.getResource("/srg/net/pryzma/Config.class"));
    }

    @Test
    void versionIsSemver100AndConfigHasNoHdU() throws Exception {
        InputStream in = PryzmaMod.class.getResourceAsStream("/srg/net/pryzma/Config.class");
        assertNotNull(in);
        String latin = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(latin.contains("Pryzma_1.21.1_1.0.0"));
        assertTrue(!latin.contains("HD_U"));
        assertTrue(!latin.contains("OptiFine"));
        assertTrue(!latin.contains("net/optifine"));
        assertTrue(latin.contains("optifine/color.properties") || latin.contains("optifine/"));
        InputStream lang = PryzmaMod.class.getResourceAsStream(
                "/assets/minecraft/optifine/lang/en_us.lang");
        assertNotNull(lang);
        String en = new String(lang.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(en.contains("pr.options.shaders="));
        assertTrue(!en.contains("\nof.options."));
        assertTrue(!en.contains("pryzma.options."));
    }

    @Test
    void optifineCapesRestoredAndConfigured() throws Exception {
        // 1. Language files: "Show Capes" is renamed to "Optifine Capes"
        String en = new String(
                readRequiredResource("/assets/minecraft/optifine/lang/en_us.lang"),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(en.contains("pr.options.SHOW_CAPES=Optifine Capes"), "en_us must name button Optifine Capes");
        assertTrue(en.contains("pr.options.skinCustomisation.ofCape=OptiFine Cape..."));
        assertTrue(en.contains("pr.options.capeOF.title=OptiFine Cape"));
        assertTrue(en.contains("pr.options.capeOF.openEditor=Open Cape Editor"));
        assertTrue(en.contains("pr.options.capeOF.reloadCape=Reload Cape"));

        String ru = new String(
                readRequiredResource("/assets/minecraft/optifine/lang/ru_ru.lang"),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(ru.contains("pr.options.SHOW_CAPES=Optifine Capes"), "ru_ru must name button Optifine Capes");
        assertTrue(ru.contains("pr.options.skinCustomisation.ofCape="));

        // 2. SkinCustomizationScreen: btnCape exists, adds widget, repositionElements handles it, opens GuiScreenCapePryzma
        org.objectweb.asm.ClassReader skin = new org.objectweb.asm.ClassReader(
                readRequiredResource("/srg/net/minecraft/client/gui/screens/options/SkinCustomizationScreen.class"));
        org.objectweb.asm.tree.ClassNode skinNode = new org.objectweb.asm.tree.ClassNode();
        skin.accept(skinNode, 0);
        assertTrue(skinNode.fields.stream().anyMatch(f -> "btnCape".equals(f.name)),
                "SkinCustomizationScreen must have btnCape field");

        org.objectweb.asm.tree.MethodNode add = skinNode.methods.stream()
                .filter(m -> "addOptions".equals(m.name))
                .findFirst()
                .orElseThrow();
        boolean addsBtnCape = false;
        for (org.objectweb.asm.tree.AbstractInsnNode insn : add.instructions) {
            if (insn instanceof org.objectweb.asm.tree.FieldInsnNode fi
                    && "btnCape".equals(fi.name)) {
                addsBtnCape = true;
            }
        }
        assertTrue(addsBtnCape, "addOptions must assign btnCape");

        org.objectweb.asm.tree.MethodNode reposition = skinNode.methods.stream()
                .filter(m -> "repositionElements".equals(m.name))
                .findFirst()
                .orElseThrow();
        boolean repositionBtnCape = false;
        for (org.objectweb.asm.tree.AbstractInsnNode insn : reposition.instructions) {
            if (insn instanceof org.objectweb.asm.tree.FieldInsnNode fi
                    && "btnCape".equals(fi.name)) {
                repositionBtnCape = true;
            }
        }
        assertTrue(repositionBtnCape, "repositionElements must handle btnCape");

        // 3. GuiScreenCapePryzma: contains optifine.net, capeChange, and actions
        String capeGui = new String(
                readRequiredResource("/srg/net/pryzma/gui/GuiScreenCapePryzma.class"),
                java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(capeGui.contains("optifine.net"), "GuiScreenCapePryzma must link to optifine.net");
        assertTrue(capeGui.contains("capeChange"), "GuiScreenCapePryzma must link to capeChange");

        // 4. CapeUtils: contains optifine.net, has patchPlayerSkin, downloadCape, reloadCape
        String capeUtils = new String(
                readRequiredResource("/srg/net/pryzma/player/CapeUtils.class"),
                java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(capeUtils.contains("s.optifine.net"), "CapeUtils must fetch from s.optifine.net");
        org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(
                readRequiredResource("/srg/net/pryzma/player/CapeUtils.class"));
        org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
        cr.accept(node, 0);
        assertTrue(node.methods.stream().anyMatch(m -> "patchPlayerSkin".equals(m.name)),
                "CapeUtils must have patchPlayerSkin");
        assertTrue(node.methods.stream().anyMatch(m -> "downloadCape".equals(m.name)),
                "CapeUtils must have downloadCape");
        assertTrue(node.methods.stream().anyMatch(m -> "reloadCape".equals(m.name)),
                "CapeUtils must have reloadCape");

        // 5. GuiDetailSettingsPryzma has Option.SHOW_CAPES -- the Details screen owns the
        //    capes toggle in OptiFine; the Quality screen must not carry a second copy.
        String detailGui = new String(
                readRequiredResource("/srg/net/pryzma/gui/GuiDetailSettingsPryzma.class"),
                java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(detailGui.contains("SHOW_CAPES"), "GuiDetailSettingsPryzma must include SHOW_CAPES option");

        // 6. PlayerInfoTransformer injects into PlayerInfo and AbstractClientPlayer
        org.objectweb.asm.tree.ClassNode piNode = new org.objectweb.asm.tree.ClassNode();
        piNode.name = "net/minecraft/client/multiplayer/PlayerInfo";
        org.objectweb.asm.tree.MethodNode mnGetSkin = new org.objectweb.asm.tree.MethodNode(
                org.objectweb.asm.Opcodes.ACC_PUBLIC,
                "getSkin",
                "()Lnet/minecraft/client/resources/PlayerSkin;",
                null,
                null);
        mnGetSkin.instructions.add(new org.objectweb.asm.tree.VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, 0));
        mnGetSkin.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.ARETURN));
        piNode.methods.add(mnGetSkin);
        assertTrue(PlayerInfoTransformer.inject(piNode), "PlayerInfoTransformer must transform PlayerInfo.getSkin");

        // 7. Verify bytecodes
        assertPayloadBytecodeVerifies("/srg/net/minecraft/client/gui/screens/options/SkinCustomizationScreen.class");
        assertPayloadBytecodeVerifies("/srg/net/pryzma/player/CapeUtils.class");
        assertPayloadBytecodeVerifies("/srg/net/pryzma/gui/GuiScreenCapePryzma.class");
        assertPayloadBytecodeVerifies("/srg/net/pryzma/gui/GuiDetailSettingsPryzma.class");
    }

    /**
     * Slot 17 of the Quality screen is the vanilla FOV-effect slider, exactly as in
     * OptiFine's GuiQualitySettingsOF. It was once swapped with Option.SHOW_CAPES, which
     * both hid the slider and duplicated a button that belongs to the Details screen.
     */
    @Test
    void fovEffectScaleHoldsQualitySlot17AndCapesStayInDetails() throws Exception {
        var quality = classNode("/srg/net/pryzma/gui/GuiQualitySettingsPryzma.class");
        var slots = optionSlots(quality);

        var slot17 = slots.get(17);
        assertNotNull(slot17, "no option in Quality slot 17; slots=" + slots.keySet());
        assertEquals("FOV_EFFECT_SCALE", slot17.name, "Quality slot 17 must be the FOV-effect slider");
        assertEquals("net/minecraft/client/Options", slot17.owner, "slot 17 must come from vanilla Options");
        assertTrue(slot17.desc.endsWith("OptionInstance;"), "slot 17 must bind an OptionInstance: " + slot17.desc);

        assertFalse(readsStaticField(quality, "SHOW_CAPES"),
                "the capes toggle belongs to the Details screen, not Quality");

        var details = classNode("/srg/net/pryzma/gui/GuiDetailSettingsPryzma.class");
        assertTrue(optionSlots(details).values().stream().anyMatch(
                        f -> "SHOW_CAPES".equals(f.name) && "net/pryzma/config/Option".equals(f.owner)),
                "GuiDetailSettingsPryzma must keep Option.SHOW_CAPES");

        String en = new String(
                readRequiredResource("/assets/minecraft/optifine/lang/en_us.lang"),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(en.contains("pr.options.SHOW_CAPES=Optifine Capes"),
                "the Details button must read Optifine Capes");
        assertTrue(en.contains("options.fovEffectScale.tooltip.1="),
                "the FOV-effect slider must keep its tooltip block");

        assertPayloadBytecodeVerifies("/srg/net/pryzma/gui/GuiQualitySettingsPryzma.class");
        assertPayloadBytecodeVerifies("/srg/net/pryzma/gui/GuiDetailSettingsPryzma.class");
    }

    @Test
    void mipmapTypeIsIteratableOptionAndCorrectlyCyclesAndFilters() throws Exception {
        // 1. Assert Option.MIPMAP_TYPE is initialized as IteratableOptionPryzma
        var optionNode = classNode("/srg/net/pryzma/config/Option.class");
        var clinit = method(optionNode, "<clinit>", "()V");
        boolean isIteratable = false;
        for (var insn : clinit.instructions) {
            if (insn.getOpcode() == org.objectweb.asm.Opcodes.PUTSTATIC) {
                var f = (org.objectweb.asm.tree.FieldInsnNode) insn;
                if ("MIPMAP_TYPE".equals(f.name) && "net/pryzma/config/Option".equals(f.owner)) {
                    var prev = f.getPrevious();
                    while (prev != null) {
                        if (prev instanceof org.objectweb.asm.tree.MethodInsnNode m
                                && "<init>".equals(m.name)
                                && "net/pryzma/config/IteratableOptionPryzma".equals(m.owner)) {
                            isIteratable = true;
                            break;
                        }
                        prev = prev.getPrevious();
                    }
                }
            }
        }
        assertTrue(isIteratable, "Option.MIPMAP_TYPE must be instantiated as IteratableOptionPryzma");

        // 2. Assert Options.setOptionValuePryzma handles Option.MIPMAP_TYPE and calls updateMipmaps
        var optionsNode = classNode("/srg/net/minecraft/client/Options.class");
        var setOpt = method(optionsNode, "setOptionValuePryzma", "(Lnet/minecraft/client/OptionInstance;I)V");
        boolean handlesMipmap = false;
        boolean callsUpdate = false;
        for (var insn : setOpt.instructions) {
            if (insn instanceof org.objectweb.asm.tree.FieldInsnNode f
                    && f.getOpcode() == org.objectweb.asm.Opcodes.GETSTATIC
                    && "MIPMAP_TYPE".equals(f.name)
                    && "net/pryzma/config/Option".equals(f.owner)) {
                handlesMipmap = true;
            }
            if (handlesMipmap && insn instanceof org.objectweb.asm.tree.MethodInsnNode m
                    && "updateMipmaps".equals(m.name)) {
                callsUpdate = true;
                break;
            }
        }
        assertTrue(handlesMipmap, "Options.setOptionValuePryzma must branch on Option.MIPMAP_TYPE");
        assertTrue(callsUpdate, "Options.setOptionValuePryzma must invoke updateMipmaps()");

        // 3. Assert Config.getMipmapType() returns distinct GL constants: 9984 (Nearest), 9986 (Linear), 9985 (Bilinear), 9987 (Trilinear)
        var configNode = classNode("/srg/net/pryzma/Config.class");
        var getMip = method(configNode, "getMipmapType", "()I");
        List<Integer> pushedConstants = new ArrayList<>();
        for (var insn : getMip.instructions) {
            if (insn instanceof org.objectweb.asm.tree.IntInsnNode i && insn.getOpcode() == org.objectweb.asm.Opcodes.SIPUSH) {
                pushedConstants.add(i.operand);
            }
        }
        assertTrue(pushedConstants.contains(9984), "getMipmapType must contain GL_NEAREST_MIPMAP_NEAREST (9984)");
        assertTrue(pushedConstants.contains(9986), "getMipmapType must contain GL_NEAREST_MIPMAP_LINEAR (9986)");
        assertTrue(pushedConstants.contains(9985), "getMipmapType must contain GL_LINEAR_MIPMAP_NEAREST (9985)");
        assertTrue(pushedConstants.contains(9987), "getMipmapType must contain GL_LINEAR_MIPMAP_LINEAR (9987)");

        // 4. Assert bytecode verifier passes on all 3 patched classes
        assertPayloadBytecodeVerifies("/srg/net/pryzma/config/Option.class");
        assertPayloadBytecodeVerifies("/srg/net/minecraft/client/Options.class");
        assertPayloadBytecodeVerifies("/srg/net/pryzma/Config.class");
    }


    /**
     * Maps array index to the GETSTATIC stored there for the screen's static option array,
     * read straight out of {@code <clinit>}. A fresh array starts at every ANEWARRAY and the
     * largest one wins, so a class holding more than one static array cannot be misread.
     */
    private static java.util.Map<Integer, org.objectweb.asm.tree.FieldInsnNode> optionSlots(
            org.objectweb.asm.tree.ClassNode node) {
        List<java.util.Map<Integer, org.objectweb.asm.tree.FieldInsnNode>> arrays = new ArrayList<>();
        for (var m : node.methods) {
            if (!"<clinit>".equals(m.name) && !"init".equals(m.name)) {
                continue;
            }
            var current = new java.util.LinkedHashMap<Integer, org.objectweb.asm.tree.FieldInsnNode>();
            Integer index = null;
            org.objectweb.asm.tree.FieldInsnNode field = null;
            for (var insn : m.instructions) {
                int op = insn.getOpcode();
                if (op == org.objectweb.asm.Opcodes.ANEWARRAY) {
                    if (!current.isEmpty()) {
                        arrays.add(current);
                        current = new java.util.LinkedHashMap<>();
                    }
                } else if (op >= org.objectweb.asm.Opcodes.ICONST_0 && op <= org.objectweb.asm.Opcodes.ICONST_5) {
                    index = op - org.objectweb.asm.Opcodes.ICONST_0;
                    field = null;
                } else if (insn instanceof org.objectweb.asm.tree.IntInsnNode push
                        && (op == org.objectweb.asm.Opcodes.BIPUSH || op == org.objectweb.asm.Opcodes.SIPUSH)) {
                    index = push.operand;
                    field = null;
                } else if (op == org.objectweb.asm.Opcodes.GETSTATIC || op == org.objectweb.asm.Opcodes.GETFIELD) {
                    field = (org.objectweb.asm.tree.FieldInsnNode) insn;
                } else if (op == org.objectweb.asm.Opcodes.AASTORE && index != null && field != null) {
                    current.put(index, field);
                    index = null;
                    field = null;
                }
            }
            if (!current.isEmpty()) {
                arrays.add(current);
            }
        }
        java.util.Map<Integer, org.objectweb.asm.tree.FieldInsnNode> largest = java.util.Map.of();
        for (var candidate : arrays) {
            if (candidate.size() > largest.size()) {
                largest = candidate;
            }
        }
        return largest;
    }

    private static boolean readsStaticField(org.objectweb.asm.tree.ClassNode node, String name) {
        for (var m : node.methods) {
            for (var insn : m.instructions) {
                if (insn instanceof org.objectweb.asm.tree.FieldInsnNode f
                        && f.getOpcode() == org.objectweb.asm.Opcodes.GETSTATIC
                        && name.equals(f.name)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void ofPrefixBecamePrOnOptionsAndLang() throws Exception {
        String options = new String(
                readRequiredResource("/srg/net/minecraft/client/Options.class"),
                java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(options.contains("prFogType"));
        assertTrue(options.contains("prShowCapes"));
        assertTrue(!options.contains("ofFogType"));
        assertTrue(!options.contains("ofShowCapes"));
        assertTrue(!options.contains("pryzmaFogType"));
        String config = new String(
                readRequiredResource("/srg/net/pryzma/Config.class"),
                java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(config.contains("prFogType") || config.contains("prShowCapes"));
        assertTrue(!config.contains("ofFogType"));
        assertTrue(!config.contains("ofShowCapes"));
        String en = new String(
                readRequiredResource("/assets/minecraft/optifine/lang/en_us.lang"),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(en.contains("pr.options.shaders="));
        assertTrue(!en.contains("of.options."));
        assertTrue(!en.contains("ofFogType"));
    }

    @Test
    void optifinePackPrefixIsScannedNotPryzmaFolder() throws Exception {
        String items = new String(
                readRequiredResource("/srg/net/pryzma/CustomItems.class"),
                java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(items.contains("optifine/cit/"));
        assertTrue(!items.contains("pryzma/cit/"));
        String ctm = new String(
                readRequiredResource("/srg/net/pryzma/ConnectedTextures.class"),
                java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(ctm.contains("optifine/ctm/"));
        Path tmp = Files.createTempDirectory("pryzma-optifine-pack-");
        Path asset = tmp.resolve("assets").resolve("minecraft").resolve("optifine").resolve("cit");
        Files.createDirectories(asset);
        Files.writeString(asset.resolve("foo.properties"), "matchItems=stone");
        String[] found = net.pryzma.util.PathPackScan.collect(
                tmp, new String[]{"optifine/cit/"}, new String[]{".properties"});
        assertEquals(1, found.length);
        assertEquals("optifine/cit/foo.properties", found[0]);
    }

    @Test
    void mcpatcherLegacyResourcePackAliasingAndDeduplication() throws Exception {
        Path tmp = Files.createTempDirectory("pryzma-mcpatcher-pack-");
        Path assets = tmp.resolve("assets").resolve("minecraft");
        Path optiCit = assets.resolve("optifine").resolve("cit");
        Path mcCit = assets.resolve("mcpatcher").resolve("cit");
        Path mcCtm = assets.resolve("mcpatcher").resolve("ctm");
        Files.createDirectories(optiCit);
        Files.createDirectories(mcCit);
        Files.createDirectories(mcCtm);

        // Subpath 'sword.properties' exists in both optifine/ and mcpatcher/ -> optifine should win
        Files.writeString(optiCit.resolve("sword.properties"), "matchItems=diamond_sword\nmodel=opti");
        Files.writeString(mcCit.resolve("sword.properties"), "matchItems=diamond_sword\nmodel=legacy");

        // Subpath 'bow.properties' only exists in mcpatcher/ -> mcpatcher should be collected
        Files.writeString(mcCit.resolve("bow.properties"), "matchItems=bow");

        // Subpath 'glass.properties' in mcpatcher/ctm/
        Files.writeString(mcCtm.resolve("glass.properties"), "matchBlocks=glass");

        String[] foundCit = net.pryzma.util.PathPackScan.collect(
                tmp, new String[]{"optifine/cit/"}, new String[]{".properties"});
        assertEquals(2, foundCit.length);
        List<String> listCit = List.of(foundCit);
        assertTrue(listCit.contains("optifine/cit/sword.properties"));
        assertTrue(listCit.contains("mcpatcher/cit/bow.properties"));
        assertFalse(listCit.contains("mcpatcher/cit/sword.properties"));

        String[] foundCtm = net.pryzma.util.PathPackScan.collect(
                tmp, new String[]{"optifine/ctm/"}, new String[]{".properties"});
        assertEquals(1, foundCtm.length);
        assertEquals("mcpatcher/ctm/glass.properties", foundCtm[0]);
    }

    @Test
    void payloadHasNoFakePryzmaWebsiteHosts() throws Exception {
        for (String path : new String[] {
            "/srg/net/pryzma/VersionCheckThread.class",
            "/srg/net/pryzma/http/HttpUtils.class",
            "/srg/net/pryzma/player/PlayerConfigurationParser.class",
        }) {
            String latin = new String(readRequiredResource(path), java.nio.charset.StandardCharsets.ISO_8859_1);
            assertTrue(!latin.contains("pryzma.net"), path);
            assertTrue(!latin.contains("pryzma.invalid"), path);
            assertTrue(!latin.contains("s.pryzma.net"), path);
        }
        String http = new String(
                readRequiredResource("/srg/net/pryzma/http/HttpUtils.class"),
                java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(http.contains("s.optifine.net"));
    }

    @Test
    void neoForgeServerInitAndTooltipDoNotUseForgeBooleanEvent() throws Exception {
        org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(
                readRequiredResource("/srg/net/pryzma/reflect/Reflector.class"));
        org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
        cr.accept(node, 0);
        org.objectweb.asm.tree.MethodNode callBool = node.methods.stream()
                .filter(m -> "callBoolean".equals(m.name)
                        && m.desc.startsWith("(Lnet/pryzma/reflect/ReflectorMethod;["))
                .findFirst()
                .orElseThrow();
        boolean returnsTrueOnNull = false;
        org.objectweb.asm.tree.AbstractInsnNode insn = callBool.instructions.getFirst();
        while (insn != null) {
            if (insn.getOpcode() == org.objectweb.asm.Opcodes.IFNULL
                    || insn.getOpcode() == org.objectweb.asm.Opcodes.IFNONNULL) {
                org.objectweb.asm.tree.AbstractInsnNode n = insn.getNext();
                while (n != null && n.getOpcode() < 0) {
                    n = n.getNext();
                }
                if (n != null && n.getOpcode() == org.objectweb.asm.Opcodes.POP) {
                    n = n.getNext();
                    while (n != null && n.getOpcode() < 0) {
                        n = n.getNext();
                    }
                    if (n != null && n.getOpcode() == org.objectweb.asm.Opcodes.ICONST_1) {
                        returnsTrueOnNull = true;
                    }
                }
            }
            insn = insn.getNext();
        }
        assertTrue(returnsTrueOnNull);

        org.objectweb.asm.ClassReader gui = new org.objectweb.asm.ClassReader(
                readRequiredResource("/srg/net/minecraft/client/gui/GuiGraphics.class"));
        org.objectweb.asm.tree.ClassNode guiNode = new org.objectweb.asm.tree.ClassNode();
        gui.accept(guiNode, 0);
        boolean invokesIsCanceled = false;
        for (org.objectweb.asm.tree.MethodNode m : guiNode.methods) {
            for (org.objectweb.asm.tree.AbstractInsnNode in : m.instructions) {
                if (in instanceof org.objectweb.asm.tree.MethodInsnNode mi
                        && "isCanceled".equals(mi.name)
                        && mi.owner.contains("bus/api/Event")) {
                    invokesIsCanceled = true;
                }
            }
        }
        assertTrue(!invokesIsCanceled);
        assertPayloadBytecodeVerifies("/srg/net/pryzma/reflect/Reflector.class");
        assertPayloadBytecodeVerifies("/srg/net/minecraft/client/gui/GuiGraphics.class");
    }

    @Test
    void resUtilsPathToFileIsRedirected() throws Exception {
        InputStream in = PryzmaMod.class.getResourceAsStream("/srg/net/pryzma/util/ResUtils.class");
        assertNotNull(in, "payload ResUtils.class");
        org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(in.readAllBytes());
        org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
        cr.accept(node, 0);
        assertTrue(ResUtilsPathTransformer.inject(node));
        org.objectweb.asm.tree.MethodNode collect = node.methods.stream()
                .filter(m -> "collectFiles".equals(m.name) && ResUtilsPathTransformer.PACK_DESC.equals(m.desc))
                .findFirst()
                .orElseThrow();
        boolean stillToFile = false;
        boolean callsScan = false;
        for (org.objectweb.asm.tree.AbstractInsnNode insn : collect.instructions) {
            if (insn instanceof org.objectweb.asm.tree.MethodInsnNode mi) {
                if ("toFile".equals(mi.name) && "java/nio/file/Path".equals(mi.owner)) {
                    stillToFile = true;
                }
                if ("collect".equals(mi.name) && ResUtilsPathTransformer.SCAN.equals(mi.owner)) {
                    callsScan = true;
                }
            }
        }
        assertTrue(!stillToFile);
        assertTrue(callsScan);
    }

    @Test
    void pathPackScanWalksNioRoot() throws Exception {
        Path tmp = Files.createTempDirectory("pryzma-pathpack-");
        Path asset = tmp.resolve("assets").resolve("minecraft").resolve("optifine").resolve("cit");
        Files.createDirectories(asset);
        Files.writeString(asset.resolve("foo.properties"), "matchItems=stone");
        Files.writeString(tmp.resolve("skip.txt"), "no");
        String[] found = net.pryzma.util.PathPackScan.collect(
                tmp, new String[]{"optifine/cit/"}, new String[]{".properties"});
        assertEquals(1, found.length);
        assertEquals("optifine/cit/foo.properties", found[0]);
        String[] none = net.pryzma.util.PathPackScan.collect(
                tmp, new String[]{"optifine/ctm/"}, new String[]{".properties"});
        assertEquals(0, none.length);
    }

    @Test
    void guiInitModdedOverlaysIsInjected() {
        org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
        node.name = GuiInitModdedOverlaysTransformer.OWNER;
        node.superName = "java/lang/Object";
        node.fields = new java.util.ArrayList<>();
        node.methods = new java.util.ArrayList<>();
        assertTrue(GuiInitModdedOverlaysTransformer.inject(node));
        assertTrue(node.methods.stream().anyMatch(
                m -> GuiInitModdedOverlaysTransformer.INIT_NAME.equals(m.name)
                        && GuiInitModdedOverlaysTransformer.INIT_DESC.equals(m.desc)));
        assertTrue(node.methods.stream().anyMatch(
                m -> GuiInitModdedOverlaysTransformer.COUNT_NAME.equals(m.name)
                        && GuiInitModdedOverlaysTransformer.COUNT_DESC.equals(m.desc)));
        assertTrue(node.fields.stream().anyMatch(
                f -> GuiInitModdedOverlaysTransformer.FIELD.equals(f.name)
                        && GuiInitModdedOverlaysTransformer.FIELD_DESC.equals(f.desc)));
        assertTrue(!GuiInitModdedOverlaysTransformer.inject(node));
    }

    @Test
    void itemTagsCreateStringStringIsInjected() {
        org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
        node.name = ItemTagsCreateTransformer.OWNER;
        org.objectweb.asm.tree.MethodNode existing = new org.objectweb.asm.tree.MethodNode(
                org.objectweb.asm.Opcodes.ACC_PUBLIC | org.objectweb.asm.Opcodes.ACC_STATIC,
                "create",
                ItemTagsCreateTransformer.CREATE_RL_DESC,
                null,
                null);
        node.methods.add(existing);
        assertTrue(ItemTagsCreateTransformer.injectCreate(node));
        long twoString = node.methods.stream()
                .filter(m -> "create".equals(m.name) && ItemTagsCreateTransformer.DESC.equals(m.desc))
                .count();
        assertEquals(1L, twoString);
        assertTrue(!ItemTagsCreateTransformer.injectCreate(node));
    }

    @Test
    void clientLevelDayTimeMethodsAreInjected() {
        org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
        node.name = "net/minecraft/client/multiplayer/ClientLevel";
        org.objectweb.asm.tree.MethodNode init = new org.objectweb.asm.tree.MethodNode(
                org.objectweb.asm.Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.RETURN));
        node.methods.add(init);

        assertTrue(ClientLevelTransformer.injectDayTime(node));
        assertTrue(node.fields.stream().anyMatch(f -> "dayTimeFraction".equals(f.name) && "F".equals(f.desc)));
        assertTrue(node.fields.stream().anyMatch(f -> "dayTimePerTick".equals(f.name) && "F".equals(f.desc)));
        assertTrue(node.methods.stream().anyMatch(m -> "setDayTimeFraction".equals(m.name) && "(F)V".equals(m.desc)));
        assertTrue(node.methods.stream().anyMatch(m -> "getDayTimeFraction".equals(m.name) && "()F".equals(m.desc)));
        assertTrue(node.methods.stream().anyMatch(m -> "getDayTimePerTick".equals(m.name) && "()F".equals(m.desc)));
        assertTrue(node.methods.stream().anyMatch(m -> "setDayTimePerTick".equals(m.name) && "(F)V".equals(m.desc)));
        assertFalse(ClientLevelTransformer.injectDayTime(node));
    }

    @Test
    void frustumIsVisiblePatchesInfiniteExtentAabb() {
        org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
        node.name = "net/minecraft/client/renderer/culling/Frustum";
        org.objectweb.asm.tree.MethodNode isVisible = new org.objectweb.asm.tree.MethodNode(
                org.objectweb.asm.Opcodes.ACC_PUBLIC, "isVisible", "(Lnet/minecraft/world/phys/AABB;)Z", null, null);
        org.objectweb.asm.tree.LabelNode label9 = new org.objectweb.asm.tree.LabelNode();
        isVisible.instructions.add(new org.objectweb.asm.tree.VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, 1));
        isVisible.instructions.add(new org.objectweb.asm.tree.FieldInsnNode(
                org.objectweb.asm.Opcodes.GETSTATIC, "net/neoforged/neoforge/common/extensions/IBlockEntityExtension", "INFINITE_EXTENT_AABB", "Lnet/minecraft/world/phys/AABB;"));
        isVisible.instructions.add(new org.objectweb.asm.tree.JumpInsnNode(org.objectweb.asm.Opcodes.IF_ACMPNE, label9));
        isVisible.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.ICONST_1));
        isVisible.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.IRETURN));
        isVisible.instructions.add(label9);
        isVisible.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.ICONST_0));
        isVisible.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.IRETURN));
        node.methods.add(isVisible);

        assertTrue(FrustumTransformer.patchIsVisible(node));
        assertFalse(FrustumTransformer.patchIsVisible(node));
    }

    @Test
    void blockEntityKeepsForgeCapabilityProviderName() throws Exception {
        InputStream in = PryzmaMod.class.getResourceAsStream(
                "/srg/net/minecraft/world/level/block/entity/BlockEntity.class");
        assertNotNull(in);
        String latin = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(latin.contains("net/minecraftforge/common/capabilities/CapabilityProvider"));
        assertTrue(!latin.contains("neoforged/neoforge/common/capabilities/CapabilityProvider"));
    }

    @Test
    void replacementFilterKeepsNeoForgeBlockEntityNest() {
        var keep = FilteredReplacementTransformer.KEEP_NEOFORGE;
        assertTrue(FilteredReplacementTransformer.withheld("net.minecraft.world.level.block.entity.BlockEntity", keep));
        assertTrue(FilteredReplacementTransformer.withheld(
                "net/minecraft/world/level/block/entity/BlockEntity$DataComponentInput", keep));
        assertFalse(FilteredReplacementTransformer.withheld("net.minecraft.world.level.block.entity.BlockEntityType", keep));
        var fake = new cpw.mods.modlauncher.api.ITransformer<org.objectweb.asm.tree.ClassNode>() {
            @Override
            public org.objectweb.asm.tree.ClassNode transform(
                    org.objectweb.asm.tree.ClassNode input, cpw.mods.modlauncher.api.ITransformerVotingContext context) {
                return input;
            }

            @Override
            public cpw.mods.modlauncher.api.TransformerVoteResult castVote(
                    cpw.mods.modlauncher.api.ITransformerVotingContext context) {
                return cpw.mods.modlauncher.api.TransformerVoteResult.YES;
            }

            @Override
            public java.util.Set<Target<org.objectweb.asm.tree.ClassNode>> targets() {
                return java.util.Set.of(
                        Target.targetPreClass("net.minecraft.world.level.block.entity.BlockEntity"),
                        Target.targetPreClass("net.minecraft.world.level.block.entity.BlockEntity$1"),
                        Target.targetPreClass("net.minecraft.client.renderer.chunk.SectionCompiler"));
            }

            @Override
            public cpw.mods.modlauncher.api.TargetType<org.objectweb.asm.tree.ClassNode> getTargetType() {
                return cpw.mods.modlauncher.api.TargetType.PRE_CLASS;
            }
        };
        var kept = new FilteredReplacementTransformer(fake, keep).targets().stream()
                .map(t -> t.className())
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(java.util.Set.of("net.minecraft.client.renderer.chunk.SectionCompiler"), kept);
    }

    @Test
    void neoForgeBlockEntityGetsOptiFineTagCacheOnly() throws Exception {
        var node = classNode("/net/minecraft/world/level/block/entity/BlockEntity.class");
        assertEquals("net/neoforged/neoforge/attachment/AttachmentHolder", node.superName);
        assertTrue(BlockEntityTransformer.inject(node));
        assertFalse(BlockEntityTransformer.inject(node));
        assertTrue(node.fields.stream().anyMatch(f -> "nbtTag".equals(f.name)
                && "Lnet/minecraft/nbt/CompoundTag;".equals(f.desc)
                && (f.access & org.objectweb.asm.Opcodes.ACC_PUBLIC) != 0));
        assertTrue(node.fields.stream().anyMatch(f -> "nbtTagUpdateMs".equals(f.name) && "J".equals(f.desc)));
        int returns = 0;
        for (var insn : method(node, "setChanged", "()V").instructions) {
            if (insn.getOpcode() == org.objectweb.asm.Opcodes.RETURN) {
                returns++;
                var put = (org.objectweb.asm.tree.FieldInsnNode) insn.getPrevious();
                assertEquals("nbtTag", put.name);
                assertEquals(org.objectweb.asm.Opcodes.ACONST_NULL, put.getPrevious().getOpcode());
            }
        }
        assertTrue(returns > 0);
        assertTrue(ldcStrings(method(node, "saveAdditional",
                "(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V")).contains("NeoForgeData"));
        assertTrue(node.methods.stream().noneMatch(m -> "getCapabilities".equals(m.name)));
        assertVerifies(node);
        for (var m : classNode("/srg/net/pryzma/RandomTileEntity.class").methods) {
            for (var insn : m.instructions) {
                if (insn instanceof org.objectweb.asm.tree.FieldInsnNode f && BlockEntityTransformer.OWNER.equals(f.owner)) {
                    assertTrue(node.fields.stream().anyMatch(x -> x.name.equals(f.name) && x.desc.equals(f.desc)), f.name);
                }
            }
        }
    }

    @Test
    void sectionMeshingIsRewiredOntoNeoForgeModelDataAndAmbientOcclusion() throws Exception {
        var compiler = patched("/srg/net/minecraft/client/renderer/chunk/SectionCompiler.class");
        assertFalse(invokes(compiler, SectionCompilerTransformer.MANAGER, null, null));
        assertTrue(invokes(compiler, SectionCompilerTransformer.GLUE, "modelDataView", null));

        var cache = patched("/srg/net/minecraft/client/renderer/chunk/RenderRegionCache.class");
        assertTrue(invokes(cache, SectionCompilerTransformer.GLUE, "captureModelData", null));
        assertTrue(writesField(cache, SectionCompilerTransformer.REGION, SectionCompilerTransformer.SNAPSHOT));

        var region = patched("/srg/net/minecraft/client/renderer/chunk/RenderChunkRegion.class");
        assertTrue(region.fields.stream().anyMatch(f -> SectionCompilerTransformer.SNAPSHOT.equals(f.name)
                && SectionCompilerTransformer.SNAPSHOT_DESC.equals(f.desc)));
        method(region, SectionCompilerTransformer.GET_MODEL_DATA, SectionCompilerTransformer.GET_MODEL_DATA_DESC);

        var pryzmaCache = patched("/srg/net/pryzma/override/ChunkCachePryzma.class");
        method(pryzmaCache, SectionCompilerTransformer.GET_MODEL_DATA, SectionCompilerTransformer.GET_MODEL_DATA_DESC);

        var renderer = patched("/srg/net/minecraft/client/renderer/block/ModelBlockRenderer.class");
        assertFalse(invokes(renderer, SectionCompilerTransformer.BAKED_MODEL, "useAmbientOcclusion",
                SectionCompilerTransformer.FORGE_AO_DESC));
        assertTrue(invokes(renderer, SectionCompilerTransformer.GLUE, "useAmbientOcclusion", null));

        var glue = classNode("/net/pryzma/util/NeoForgeMeshing.class");
        int calls = 0;
        for (var node : List.of(compiler, cache, region, pryzmaCache, renderer)) {
            for (var m : node.methods) {
                for (var insn : m.instructions) {
                    if (insn instanceof org.objectweb.asm.tree.MethodInsnNode call
                            && SectionCompilerTransformer.GLUE.equals(call.owner)) {
                        calls++;
                        assertTrue(glue.methods.stream().anyMatch(g -> g.name.equals(call.name) && g.desc.equals(call.desc)
                                && (g.access & org.objectweb.asm.Opcodes.ACC_STATIC) != 0), call.name + call.desc);
                    }
                }
            }
        }
        assertEquals(4, calls);
    }

    @Test
    void builtJarShipsMeshingGlueToGameOverlay() throws Exception {
        Path jar = findBuiltModJar();
        assertNotNull(jar, "built mod jar");
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            assertNotNull(zf.getEntry("srg/net/pryzma/util/NeoForgeMeshing.class"), jar.toString());
            Path overlay = PryzmaTransformationService.extractGameOverlay(zf);
            assertTrue(Files.isRegularFile(overlay.resolve("net/pryzma/util/NeoForgeMeshing.class")));
        }
    }

    @Test
    void modelBakerImplGetsGetTopLevelModel() throws Exception {
        var node = classNode("/srg/net/minecraft/client/resources/model/ModelBakery$ModelBakerImpl.class");
        assertTrue(ModelBakerTransformer.inject(node));
        assertFalse(ModelBakerTransformer.inject(node));
        assertTrue(node.methods.stream().anyMatch(m -> "getTopLevelModel".equals(m.name)
                && "(Lnet/minecraft/client/resources/model/ModelResourceLocation;)Lnet/minecraft/client/resources/model/UnbakedModel;".equals(m.desc)));
        assertVerifies(node);
    }

    @Test
    void liquidBlockRendererGuardsSpritesAndRewiresToNeoForge() throws Exception {
        var node = classNode("/srg/net/minecraft/client/renderer/block/LiquidBlockRenderer.class");
        assertTrue(LiquidBlockRendererTransformer.inject(node));
        assertFalse(LiquidBlockRendererTransformer.inject(node));
        assertVerifies(node);

        assertTrue(node.methods.stream().anyMatch(m -> "shouldRenderFace".equals(m.name)
                && "(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/material/FluidState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/block/state/BlockState;)Z".equals(m.desc)));

        assertTrue(invokes(node, LiquidBlockRendererTransformer.GLUE, "reloadFluidSprites", "()V"));
        assertTrue(invokes(node, LiquidBlockRendererTransformer.GLUE, "getFluidSprites", null));

        var tesselate = node.methods.stream().filter(m -> "tesselate".equals(m.name)).findFirst().orElseThrow();
        boolean hasGuard = false;
        for (var insn : tesselate.instructions) {
            if (insn instanceof org.objectweb.asm.tree.JumpInsnNode j && j.getOpcode() == org.objectweb.asm.Opcodes.IF_ICMPLE) {
                hasGuard = true;
                break;
            }
        }
        assertTrue(hasGuard, "tesselate missing IF_ICMPLE guard on sprites.length");
    }

    @Test
    void blockRenderDispatcherGetsGetLiquidBlockRenderer() throws Exception {
        var node = classNode("/srg/net/minecraft/client/renderer/block/BlockRenderDispatcher.class");
        assertTrue(LiquidBlockRendererTransformer.inject(node));
        assertFalse(LiquidBlockRendererTransformer.inject(node));
        assertVerifies(node);

        assertTrue(node.methods.stream().anyMatch(m -> "getLiquidBlockRenderer".equals(m.name)
                && "()Lnet/minecraft/client/renderer/block/LiquidBlockRenderer;".equals(m.desc)));
    }

    @Test
    void textureAtlasGetsGetTexturesAndMcpatcherPathHook() throws Exception {
        var node = classNode("/srg/net/minecraft/client/renderer/texture/TextureAtlas.class");
        assertTrue(TextureAtlasTransformer.inject(node));
        assertFalse(TextureAtlasTransformer.inject(node));
        assertVerifies(node);

        assertTrue(node.methods.stream().anyMatch(m -> "getTextures".equals(m.name)
                && "()Ljava/util/Map;".equals(m.desc)));

        org.objectweb.asm.tree.MethodNode isAbs = node.methods.stream()
                .filter(m -> "isAbsoluteLocationPath".equals(m.name) && "(Ljava/lang/String;)Z".equals(m.desc))
                .findFirst()
                .orElseThrow();
        assertTrue(isAbs.instructions.size() > 0);
        assertTrue(net.pryzma.util.PathPackScan.isAbsoluteLocationPath("optifine/ctm/glass/0"));
        assertTrue(net.pryzma.util.PathPackScan.isAbsoluteLocationPath("mcpatcher/ctm/glass/0"));
        assertFalse(net.pryzma.util.PathPackScan.isAbsoluteLocationPath("block/glass"));
    }

    @Test
    void clientLevelGetsGetModelData() throws Exception {
        var node = classNode("/srg/net/minecraft/client/multiplayer/ClientLevel.class");
        assertTrue(ClientLevelTransformer.inject(node));
        assertFalse(ClientLevelTransformer.inject(node));
        assertVerifies(node);

        assertTrue(node.methods.stream().anyMatch(m -> "getModelData".equals(m.name)
                && "(Lnet/minecraft/core/BlockPos;)Lnet/neoforged/neoforge/client/model/data/ModelData;".equals(m.desc)));
        assertTrue(node.methods.stream().anyMatch(m -> "setDayTimeFraction".equals(m.name) && "(F)V".equals(m.desc)));
    }

    @Test
    void sectionDispatcherIsBuiltOnTheDedicatedChunkPool() throws Exception {
        var node = classNode("/srg/net/minecraft/client/renderer/LevelRenderer.class");
        assertTrue(invokes(node, LevelRendererTransformer.UTIL, LevelRendererTransformer.BACKGROUND_EXECUTOR,
                LevelRendererTransformer.EXECUTOR_DESC), "specimen already off the background pool");
        assertTrue(LevelRendererTransformer.inject(node));
        assertFalse(LevelRendererTransformer.inject(node));
        assertVerifies(node);

        assertFalse(invokes(node, LevelRendererTransformer.UTIL, LevelRendererTransformer.BACKGROUND_EXECUTOR, null));
        assertTrue(invokes(node, LevelRendererTransformer.POOL, LevelRendererTransformer.GET_EXECUTOR,
                LevelRendererTransformer.EXECUTOR_DESC));

        // the call must sit inside the argument list of new SectionRenderDispatcher(...)
        assertTrue(feedsSectionDispatcher(method(node, "allChanged", "()V")), "allChanged");

        var pool = classNode("/net/pryzma/util/PryzmaChunkExecutor.class");
        assertTrue(pool.methods.stream().anyMatch(m -> LevelRendererTransformer.GET_EXECUTOR.equals(m.name)
                && LevelRendererTransformer.EXECUTOR_DESC.equals(m.desc)
                && (m.access & org.objectweb.asm.Opcodes.ACC_STATIC) != 0
                && (m.access & org.objectweb.asm.Opcodes.ACC_PUBLIC) != 0));
    }

    @Test
    void nonDispatcherBackgroundExecutorCallsAreLeftAlone() {
        var node = new org.objectweb.asm.tree.ClassNode();
        node.name = "net/minecraft/client/renderer/LevelRenderer";
        var m = new org.objectweb.asm.tree.MethodNode(
                org.objectweb.asm.Opcodes.ACC_PUBLIC, "reload", "()V", null, null);
        m.instructions.add(new org.objectweb.asm.tree.MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESTATIC,
                LevelRendererTransformer.UTIL, LevelRendererTransformer.BACKGROUND_EXECUTOR,
                LevelRendererTransformer.EXECUTOR_DESC, false));
        m.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.POP));
        m.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.RETURN));
        node.methods.add(m);

        assertFalse(LevelRendererTransformer.inject(node));
        assertTrue(invokes(node, LevelRendererTransformer.UTIL, LevelRendererTransformer.BACKGROUND_EXECUTOR, null));
    }

    @Test
    void chunkExecutorSizesAndIsolatesItsWorkers() throws Exception {
        int cores = Runtime.getRuntime().availableProcessors();
        int expected = Math.clamp(Math.max(cores / 3, cores - 6), 1, 10);
        assertEquals(expected, net.pryzma.util.PryzmaChunkExecutor.getOptimalThreadCount());
        assertTrue(expected >= 1 && expected <= 10, "clamped to [1, 10]");

        var pool = net.pryzma.util.PryzmaChunkExecutor.getExecutor();
        assertNotNull(pool);
        assertSame(pool, net.pryzma.util.PryzmaChunkExecutor.getExecutor(), "singleton");
        assertEquals(expected, ((java.util.concurrent.ThreadPoolExecutor) pool).getMaximumPoolSize());

        // hold every worker at once, so each task lands on a distinct thread
        var gate = new java.util.concurrent.CountDownLatch(expected);
        var workers = java.util.concurrent.ConcurrentHashMap.<Thread>newKeySet();
        List<java.util.concurrent.Future<?>> tasks = new ArrayList<>();
        for (int i = 0; i < expected; i++) {
            tasks.add(pool.submit(() -> {
                workers.add(Thread.currentThread());
                gate.countDown();
                gate.await(10, java.util.concurrent.TimeUnit.SECONDS);
                return null;
            }));
        }
        for (var task : tasks) {
            task.get(30, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertEquals(expected, workers.size());
        for (Thread worker : workers) {
            assertTrue(worker.getName().startsWith(net.pryzma.util.PryzmaChunkExecutor.WORKER_PREFIX), worker.getName());
            assertTrue(worker.isDaemon(), worker.getName());
            assertEquals(Thread.NORM_PRIORITY - 2, worker.getPriority(), worker.getName());
        }

        net.pryzma.util.PryzmaChunkExecutor.shutdown();
        assertTrue(((java.util.concurrent.ThreadPoolExecutor) pool).isShutdown());
        var restarted = net.pryzma.util.PryzmaChunkExecutor.getExecutor();
        assertFalse(restarted.isShutdown(), "restarted after shutdown");
        assertEquals("meshed", restarted.submit(() -> "meshed").get(30, java.util.concurrent.TimeUnit.SECONDS));
    }

    /** The pool call is evaluated between {@code new SectionRenderDispatcher} and its {@code <init>}. */
    private static boolean feedsSectionDispatcher(org.objectweb.asm.tree.MethodNode m) {
        boolean allocated = false;
        boolean redirected = false;
        for (var insn : m.instructions) {
            if (insn instanceof org.objectweb.asm.tree.TypeInsnNode alloc
                    && alloc.getOpcode() == org.objectweb.asm.Opcodes.NEW
                    && LevelRendererTransformer.DISPATCHER.equals(alloc.desc)) {
                allocated = true;
            } else if (insn instanceof org.objectweb.asm.tree.MethodInsnNode call
                    && LevelRendererTransformer.POOL.equals(call.owner)) {
                redirected = allocated;
            } else if (insn instanceof org.objectweb.asm.tree.MethodInsnNode init
                    && init.getOpcode() == org.objectweb.asm.Opcodes.INVOKESPECIAL
                    && LevelRendererTransformer.DISPATCHER.equals(init.owner) && "<init>".equals(init.name)) {
                if (redirected) {
                    return true;
                }
                allocated = false;
            }
        }
        return false;
    }

    @Test
    void keepNeoForgeContainsBlockEntityMobAndWeightedBakedModel() {
        assertTrue(FilteredReplacementTransformer.KEEP_NEOFORGE.contains("net.minecraft.world.level.block.entity.BlockEntity"));
        assertTrue(FilteredReplacementTransformer.KEEP_NEOFORGE.contains("net.minecraft.world.entity.Mob"));
        assertTrue(FilteredReplacementTransformer.KEEP_NEOFORGE.contains("net.minecraft.client.resources.model.WeightedBakedModel"));
        assertTrue(FilteredReplacementTransformer.KEEP_NEOFORGE.contains("net.minecraft.client.renderer.entity.layers.CapeLayer"));
    }

    private static org.objectweb.asm.tree.ClassNode classNode(String resource) throws Exception {
        var node = new org.objectweb.asm.tree.ClassNode();
        new org.objectweb.asm.ClassReader(readRequiredResource(resource)).accept(node, 0);
        return node;
    }

    private static org.objectweb.asm.tree.ClassNode patched(String resource) throws Exception {
        var node = classNode(resource);
        assertTrue(SectionCompilerTransformer.inject(node), resource);
        assertFalse(SectionCompilerTransformer.inject(node), resource);
        assertVerifies(node);
        return node;
    }

    private static org.objectweb.asm.tree.MethodNode method(org.objectweb.asm.tree.ClassNode node, String name, String desc) {
        return node.methods.stream()
                .filter(m -> name.equals(m.name) && desc.equals(m.desc))
                .findFirst()
                .orElseThrow(() -> new AssertionError(node.name + "." + name + desc));
    }

    private static boolean invokes(org.objectweb.asm.tree.ClassNode node, String owner, String name, String desc) {
        for (var m : node.methods) {
            for (var insn : m.instructions) {
                if (insn instanceof org.objectweb.asm.tree.MethodInsnNode call && owner.equals(call.owner)
                        && (name == null || name.equals(call.name)) && (desc == null || desc.equals(call.desc))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean writesField(org.objectweb.asm.tree.ClassNode node, String owner, String name) {
        for (var m : node.methods) {
            for (var insn : m.instructions) {
                if (insn instanceof org.objectweb.asm.tree.FieldInsnNode f && f.getOpcode() == org.objectweb.asm.Opcodes.PUTFIELD
                        && owner.equals(f.owner) && name.equals(f.name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<String> ldcStrings(org.objectweb.asm.tree.MethodNode m) {
        List<String> out = new ArrayList<>();
        for (var insn : m.instructions) {
            if (insn instanceof org.objectweb.asm.tree.LdcInsnNode ldc && ldc.cst instanceof String s) {
                out.add(s);
            }
        }
        return out;
    }

    private static void assertVerifies(org.objectweb.asm.tree.ClassNode node) {
        var analyzer = new org.objectweb.asm.tree.analysis.Analyzer<>(new org.objectweb.asm.tree.analysis.BasicVerifier());
        for (var m : node.methods) {
            try {
                analyzer.analyze(node.name, m);
            } catch (org.objectweb.asm.tree.analysis.AnalyzerException e) {
                throw new AssertionError(node.name + " " + m.name + m.desc, e);
            }
        }
    }

    @Test
    void deletedForgeTypesHaveLoadableStubs() throws Exception {
        Class<?> cap = Class.forName("net.minecraftforge.common.capabilities.CapabilityProvider");
        assertNotNull(cap.getDeclaredConstructor(Class.class));
        cap.getMethod("gatherCapabilities");
        assertTrue(net.neoforged.neoforge.attachment.AttachmentHolder.class.isAssignableFrom(cap));
        Class.forName("net.minecraftforge.client.extensions.IForgeElytraLayer");
        Class.forName("net.minecraftforge.client.extensions.IForgeVertexFormat");
        Class.forName("net.minecraftforge.client.ForgeRenderTypes");
        Path root = PryzmaTransformationService.findCompiledForgeStubs();
        assertNotNull(root, "compiled Forge stubs");
        assertTrue(Files.isRegularFile(root.resolve("common").resolve("capabilities").resolve("CapabilityProvider.class")));
    }

    @Test
    void payloadRootResolvesSrgConfig() {
        Path root = PryzmaTransformationService.findPayloadRoot();
        assertNotNull(root, "findPayloadRoot");
        assertTrue(Files.isRegularFile(root.resolve("srg").resolve("net").resolve("pryzma").resolve("Config.class")));
        assertTrue(Files.isRegularFile(root.resolve("files.txt")));
    }

    @Test
    void ctmAssetPresentAndNonEmpty() throws Exception {
        InputStream in = PryzmaMod.class.getResourceAsStream(
                "/assets/minecraft/optifine/bettergrass.properties");
        assertNotNull(in);
        byte[] bytes = in.readAllBytes();
        assertTrue(bytes.length > 0);
    }

    @Test
    void extractGameOverlayFromZipLaysOutPryzmaAndStubs() throws Exception {
        byte[] config = readRequiredResource("/srg/net/pryzma/Config.class");
        byte[] scan = classBytes(net.pryzma.util.PathPackScan.class);
        byte[] cap = classBytes(net.minecraftforge.common.capabilities.CapabilityProvider.class);
        Path zipPath = Files.createTempFile("pryzma-overlay-", ".jar");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            putZip(zos, "srg/net/pryzma/Config.class", config);
            putZip(zos, "srg/net/pryzma/util/PathPackScan.class", scan);
            putZip(zos, "srg/net/minecraftforge/common/capabilities/CapabilityProvider.class", cap);
            putZip(zos, "notch/ignored.class", new byte[] {1, 2, 3});
        }
        try (ZipFile zf = new ZipFile(zipPath.toFile())) {
            Path overlay = PryzmaTransformationService.extractGameOverlay(zf);
            assertTrue(Files.isRegularFile(overlay.resolve("net/pryzma/Config.class")));
            assertTrue(Files.isRegularFile(overlay.resolve("net/pryzma/util/PathPackScan.class")));
            assertTrue(Files.isRegularFile(
                    overlay.resolve("net/minecraftforge/common/capabilities/CapabilityProvider.class")));
            assertTrue(!Files.exists(overlay.resolve("notch/ignored.class")));
            assertTrue(!Files.exists(overlay.resolve("srg")));
        }
    }

    @Test
    void builtJarContainsPathPackScanAndCapabilityProviderUnderSrg() throws Exception {
        Path jar = findBuiltModJar();
        assertNotNull(jar, "built mod jar");
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            assertNotNull(zf.getEntry("srg/net/pryzma/util/PathPackScan.class"), jar.toString());
            assertNotNull(
                    zf.getEntry("srg/net/minecraftforge/common/capabilities/CapabilityProvider.class"),
                    jar.toString());
            Path overlay = PryzmaTransformationService.extractGameOverlay(zf);
            assertTrue(Files.isRegularFile(overlay.resolve("net/pryzma/Config.class")));
            assertTrue(Files.isRegularFile(overlay.resolve("net/pryzma/util/PathPackScan.class")));
            assertTrue(Files.isRegularFile(
                    overlay.resolve("net/minecraftforge/common/capabilities/CapabilityProvider.class")));
            var tomlEntry = zf.getEntry("META-INF/neoforge.mods.toml");
            assertNotNull(tomlEntry);
            String toml = new String(zf.getInputStream(tomlEntry).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(toml.contains("modId=\"pryzma\""));
            assertTrue(toml.contains("version=\"1.0.0\""));
            assertTrue(toml.contains("displayName=\"Pryzma\""));
            assertTrue(!toml.toLowerCase(java.util.Locale.ROOT).contains("optifine"));
            assertTrue(!toml.contains("HD_U"));
        }
    }

    private static void assertPayloadBytecodeVerifies(String resource) throws Exception {
        byte[] bytes = readRequiredResource(resource);
        org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(bytes);
        org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
        cr.accept(node, 0);
        org.objectweb.asm.tree.analysis.Analyzer<org.objectweb.asm.tree.analysis.BasicValue> analyzer =
                new org.objectweb.asm.tree.analysis.Analyzer<>(
                        new org.objectweb.asm.tree.analysis.BasicVerifier());
        for (org.objectweb.asm.tree.MethodNode method : node.methods) {
            try {
                analyzer.analyze(node.name, method);
            } catch (org.objectweb.asm.tree.analysis.AnalyzerException e) {
                throw new AssertionError(resource + " " + method.name + method.desc, e);
            }
        }
    }

    @Test
    void decoyCandidateLocatorServiceRegisteredAndGeneratesValidStubJar() throws Exception {
        InputStream in = PryzmaMod.class.getResourceAsStream(
                "/META-INF/services/net.neoforged.neoforgespi.locating.IModFileCandidateLocator");
        assertNotNull(in, "IModFileCandidateLocator service file missing");
        String content = new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        assertTrue(content.contains("net.pryzma.neoforge.PryzmaCandidateLocator"),
                "service file must register PryzmaCandidateLocator");

        PryzmaCandidateLocator locator = new PryzmaCandidateLocator();
        assertEquals(100, locator.getPriority());
        assertEquals("PryzmaCandidateLocator", locator.toString());

        byte[] stubBytes = PryzmaCandidateLocator.buildStubJarBytes();
        assertNotNull(stubBytes);
        assertTrue(stubBytes.length > 0);

        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(stubBytes))) {
            ZipEntry entry;
            boolean foundManifest = false;
            boolean foundToml = false;
            boolean hasClassFiles = false;
            while ((entry = zis.getNextEntry()) != null) {
                if ("META-INF/MANIFEST.MF".equals(entry.getName())) {
                    foundManifest = true;
                    String manifestText = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    assertTrue(manifestText.contains("Automatic-Module-Name: prizma_beta"),
                            "stub manifest must define isolated Automatic-Module-Name: prizma_beta");
                } else if ("META-INF/neoforge.mods.toml".equals(entry.getName())) {
                    foundToml = true;
                    String tomlText = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    assertTrue(tomlText.contains("modLoader=\"lowcodefml\""), "stub toml must use lowcodefml");
                    assertTrue(tomlText.contains("modId=\"prizma_beta\""), "stub toml must declare modId=prizma_beta");
                    assertTrue(tomlText.contains("displayName=\"Pryzma\""), "stub toml must declare displayName=Pryzma");
                    assertTrue(tomlText.contains("authors=\"Sto3IV and Ranni\""), "stub toml must declare authors=Sto3IV and Ranni");
                    assertFalse(tomlText.toLowerCase().contains("sp614x"), "stub toml must not contain sp614x");
                }
                if (entry.getName().endsWith(".class")) {
                    hasClassFiles = true;
                }
            }
            assertTrue(foundManifest, "stub jar must have META-INF/MANIFEST.MF");
            assertTrue(foundToml, "stub jar must have META-INF/neoforge.mods.toml");
            assertFalse(hasClassFiles, "Decoy stub jar must be lowcode metadata only with 0 class files");
        }
    }

    @Test
    void sp614xAuthorPurgedAndSto3IVAndRanniConfigured() throws Exception {
        byte[] titleScreenBytes = readRequiredResource("/srg/net/minecraft/client/gui/screens/TitleScreen.class");
        String titleScreenStr = new String(titleScreenBytes, StandardCharsets.ISO_8859_1);
        assertFalse(titleScreenStr.toLowerCase().contains("sp614x"), "TitleScreen.class must not contain sp614x");
        assertTrue(titleScreenStr.contains("Happy birthday, Ranni!"), "TitleScreen must celebrate Ranni birthday on Jan 11");
        assertTrue(titleScreenStr.contains("Happy birthday, Sto3IV!"), "TitleScreen must celebrate Sto3IV birthday on Nov 13");

        byte[] stubBytes = PryzmaCandidateLocator.buildStubJarBytes();
        boolean foundAuthorInStub = false;
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(stubBytes))) {
            java.util.zip.ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                if ("META-INF/neoforge.mods.toml".equals(ze.getName())) {
                    String toml = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    assertFalse(toml.toLowerCase().contains("sp614x"), "Stub toml must not contain sp614x");
                    assertTrue(toml.contains("Sto3IV and Ranni"), "Stub toml must credit Sto3IV and Ranni");
                    foundAuthorInStub = true;
                }
            }
        }
        assertTrue(foundAuthorInStub, "Stub jar must contain META-INF/neoforge.mods.toml");

        byte[] enGb = readRequiredResource("/assets/minecraft/optifine/lang/en_gb.lang");
        assertFalse(new String(enGb, StandardCharsets.UTF_8).toLowerCase().contains("sp614x"));

        byte[] enUd = readRequiredResource("/assets/minecraft/optifine/lang/en_ud.lang");
        assertFalse(new String(enUd, StandardCharsets.UTF_8).toLowerCase().contains("sp614x"));
    }

    @Test
    void reflectorTransformerRedirectsMethodInvokeAndBytecodeVerifies() throws Exception {
        Path path = Path.of("src/main/resources/srg/net/pryzma/reflect/Reflector.class");
        byte[] bytes = Files.readAllBytes(path);
        org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(bytes);
        org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
        cr.accept(node, 0);

        int replaced = ReflectorTransformer.inject(node);
        if (replaced > 0) {
            assertEquals(16, replaced, "Must replace 16 Method.invoke call sites");
            org.objectweb.asm.ClassWriter cw = new org.objectweb.asm.ClassWriter(0);
            node.accept(cw);
            Files.write(path, cw.toByteArray());
        }

        int invokeVirtualCount = 0;
        int invokeStaticAdapterCount = 0;
        for (org.objectweb.asm.tree.MethodNode mn : node.methods) {
            for (org.objectweb.asm.tree.AbstractInsnNode insn : mn.instructions) {
                if (insn instanceof org.objectweb.asm.tree.MethodInsnNode mi) {
                    if ("java/lang/reflect/Method".equals(mi.owner) && "invoke".equals(mi.name)) {
                        invokeVirtualCount++;
                    }
                    if ("net/pryzma/reflect/ReflectorAdapter".equals(mi.owner) && "invoke".equals(mi.name)) {
                        invokeStaticAdapterCount++;
                    }
                }
            }
        }
        assertEquals(0, invokeVirtualCount, "All Method.invoke calls must be redirected");
        assertEquals(16, invokeStaticAdapterCount, "Must have 16 ReflectorAdapter.invoke calls");

        PryzmaTransformationService service = new PryzmaTransformationService();
        assertTrue(service.transformers().stream().anyMatch(t -> t instanceof ReflectorTransformer),
                "ReflectorTransformer must be registered in PryzmaTransformationService");

        assertPayloadBytecodeVerifies("/srg/net/pryzma/reflect/Reflector.class");
    }

    public static class MockBrandingControl {
        static boolean forEachLineCalled = false;
        static boolean forEachAboveCalled = false;

        public static void forEachLine(boolean a, boolean b, java.util.function.BiConsumer<Integer, String> consumer) {
            forEachLineCalled = true;
            consumer.accept(10, "NeoForgeBranding");
        }

        public static void forEachAboveCopyrightLine(java.util.function.BiConsumer<Integer, String> consumer) {
            forEachAboveCalled = true;
            consumer.accept(20, "AboveCopyright");
        }
    }

    public static class MockIDimensionSpecialEffectsExtension {
        static boolean skyCalled = false;
        static boolean cloudsCalled = false;

        public boolean renderSky(Object level, int ticks, float partialTick,
                org.joml.Matrix4f modelView, Object camera, org.joml.Matrix4f proj, boolean isFog, Runnable setupFog) {
            skyCalled = true;
            assertNotNull(modelView);
            assertNotNull(proj);
            return true;
        }

        public boolean renderClouds(Object level, int ticks, float partialTick,
                Object poseStack, double camX, double camY, double camZ,
                org.joml.Matrix4f modelView, org.joml.Matrix4f proj) {
            cloudsCalled = true;
            assertNotNull(modelView);
            assertNotNull(proj);
            return true;
        }
    }

    public static class MockClientHooks {
        static boolean highlightCalled = false;

        public static boolean onDrawHighlight(Object lr, Object cam, Object hr,
                net.minecraft.client.DeltaTracker dt, Object ps, Object mbs) {
            highlightCalled = true;
            assertNotNull(dt);
            return true;
        }
    }

    @Test
    void reflectorAdapterAdaptsHooksCorrectly() throws Throwable {
        // 1. BrandingControl.forEachLine simulation
        java.lang.reflect.Method forEachLineMethod = MockBrandingControl.class
                .getMethod("forEachLine", boolean.class, boolean.class, java.util.function.BiConsumer.class);
        List<String> receivedLines = new ArrayList<>();
        java.util.function.ObjIntConsumer<String> testConsumer = (text, idx) -> receivedLines.add(idx + ":" + text);
        Object[] paramsLine = new Object[] { true, true, testConsumer };
        net.pryzma.reflect.ReflectorAdapter.invoke(forEachLineMethod, null, paramsLine);
        assertTrue(MockBrandingControl.forEachLineCalled);
        assertEquals(List.of("10:NeoForgeBranding"), receivedLines);

        // 2. BrandingControl.forEachAboveCopyrightLine simulation
        java.lang.reflect.Method forEachAboveMethod = MockBrandingControl.class
                .getMethod("forEachAboveCopyrightLine", java.util.function.BiConsumer.class);
        List<String> receivedAbove = new ArrayList<>();
        java.util.function.ObjIntConsumer<String> testAboveConsumer = (text, idx) -> receivedAbove.add(idx + ":" + text);
        Object[] paramsAbove = new Object[] { testAboveConsumer };
        net.pryzma.reflect.ReflectorAdapter.invoke(forEachAboveMethod, null, paramsAbove);
        assertTrue(MockBrandingControl.forEachAboveCalled);
        assertEquals(List.of("20:AboveCopyright"), receivedAbove);

        // 3. renderSky simulation (7 args -> 8 args)
        MockIDimensionSpecialEffectsExtension dimEffects = new MockIDimensionSpecialEffectsExtension();
        java.lang.reflect.Method renderSkyMethod = MockIDimensionSpecialEffectsExtension.class
                .getMethod("renderSky", Object.class, int.class, float.class, org.joml.Matrix4f.class,
                        Object.class, org.joml.Matrix4f.class, boolean.class, Runnable.class);
        Object[] paramsSky = new Object[] { "level", 100, 0.5f, "camera", new org.joml.Matrix4f(), true, (Runnable)() -> {} };
        Object skyRes = net.pryzma.reflect.ReflectorAdapter.invoke(renderSkyMethod, dimEffects, paramsSky);
        assertEquals(Boolean.TRUE, skyRes);
        assertTrue(MockIDimensionSpecialEffectsExtension.skyCalled);

        // 4. onDrawHighlight simulation (Float partialTick -> DeltaTracker)
        java.lang.reflect.Method highlightMethod = MockClientHooks.class
                .getMethod("onDrawHighlight", Object.class, Object.class, Object.class,
                        net.minecraft.client.DeltaTracker.class, Object.class, Object.class);
        Object[] paramsHighlight = new Object[] { "lr", "cam", "hr", Float.valueOf(0.5f), "ps", "mbs" };
        Object hlRes = net.pryzma.reflect.ReflectorAdapter.invoke(highlightMethod, null, paramsHighlight);
        assertEquals(Boolean.TRUE, hlRes);
        assertTrue(MockClientHooks.highlightCalled);

        // 5. renderClouds simulation (8 args -> 9 args)
        java.lang.reflect.Method renderCloudsMethod = MockIDimensionSpecialEffectsExtension.class
                .getMethod("renderClouds", Object.class, int.class, float.class, Object.class,
                        double.class, double.class, double.class, org.joml.Matrix4f.class, org.joml.Matrix4f.class);
        Object[] paramsClouds = new Object[] { "level", 100, 0.5f, "ps", 1.0, 2.0, 3.0, new org.joml.Matrix4f() };
        Object cloudsRes = net.pryzma.reflect.ReflectorAdapter.invoke(renderCloudsMethod, dimEffects, paramsClouds);
        assertEquals(Boolean.TRUE, cloudsRes);
        assertTrue(MockIDimensionSpecialEffectsExtension.cloudsCalled);

        // 6. Fallback delegation to standard method
        java.lang.reflect.Method toUpper = String.class.getMethod("toUpperCase");
        Object result = net.pryzma.reflect.ReflectorAdapter.invoke(toUpper, "test", new Object[0]);
        assertEquals("TEST", result);
    }

    @Test
    void guiPerformanceSettingsHasTenOptionsWithFastPaintingsAndNoDynamicUpdates() throws Exception {
        byte[] bytes = readRequiredResource("/srg/net/pryzma/gui/GuiPerformanceSettingsPryzma.class");
        org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(bytes);
        org.objectweb.asm.tree.ClassNode cn = new org.objectweb.asm.tree.ClassNode();
        cr.accept(cn, 0);

        org.objectweb.asm.tree.MethodNode initMethod = null;
        for (org.objectweb.asm.tree.MethodNode m : cn.methods) {
            if ("init".equals(m.name) && "()V".equals(m.desc)) {
                initMethod = m;
                break;
            }
        }
        assertNotNull(initMethod, "GuiPerformanceSettingsPryzma.init missing");

        boolean hasArraySize10 = false;
        boolean hasDynamicUpdates = false;
        boolean hasLazyLoading = false;
        boolean hasPrioritizeUpdates = false;
        boolean hasFastPaintings = false;

        for (org.objectweb.asm.tree.AbstractInsnNode insn : initMethod.instructions.toArray()) {
            if (insn.getOpcode() == org.objectweb.asm.Opcodes.BIPUSH
                    && ((org.objectweb.asm.tree.IntInsnNode) insn).operand == 10) {
                hasArraySize10 = true;
            }
            if (insn instanceof org.objectweb.asm.tree.FieldInsnNode fn) {
                if ("CHUNK_UPDATES_DYNAMIC".equals(fn.name)) {
                    hasDynamicUpdates = true;
                }
                if ("LAZY_CHUNK_LOADING".equals(fn.name)) {
                    hasLazyLoading = true;
                }
                if ("PRIORITIZE_CHUNK_UPDATES".equals(fn.name)) {
                    hasPrioritizeUpdates = true;
                }
                if ("FAST_PAINTINGS".equals(fn.name)) {
                    hasFastPaintings = true;
                }
            }
        }

        assertTrue(hasArraySize10, "Option array allocation size must be 10");
        assertFalse(hasDynamicUpdates, "CHUNK_UPDATES_DYNAMIC must not be present in GUI array");
        assertTrue(hasLazyLoading, "LAZY_CHUNK_LOADING must be retained in GUI array");
        assertTrue(hasPrioritizeUpdates, "PRIORITIZE_CHUNK_UPDATES must be retained in GUI array");
        assertTrue(hasFastPaintings, "FAST_PAINTINGS must be present in GUI array");

        org.objectweb.asm.tree.analysis.Analyzer<org.objectweb.asm.tree.analysis.BasicValue> analyzer =
                new org.objectweb.asm.tree.analysis.Analyzer<>(new org.objectweb.asm.tree.analysis.BasicVerifier());
        analyzer.analyze(cn.name, initMethod);
    }

    @Test
    void fastPaintingsOptionAndConfigIntegrity() throws Exception {
        byte[] optBytes = readRequiredResource("/srg/net/pryzma/config/Option.class");
        String optLatin = new String(optBytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(optLatin.contains("FAST_PAINTINGS"), "Option.FAST_PAINTINGS field must be present");

        byte[] optionsBytes = readRequiredResource("/srg/net/minecraft/client/Options.class");
        String optionsLatin = new String(optionsBytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(optionsLatin.contains("prFastPaintings"), "Options.prFastPaintings field must be present");

        byte[] configBytes = readRequiredResource("/srg/net/pryzma/Config.class");
        String configLatin = new String(configBytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(configLatin.contains("isFastPaintings"), "Config.isFastPaintings method must be present");
    }

    @Test
    void chunkUpdatesDynamicPurgedFromOptionsAndOptionAndConfigReturnsFalse() throws Exception {
        byte[] optBytes = readRequiredResource("/srg/net/pryzma/config/Option.class");
        var crOpt = new org.objectweb.asm.ClassReader(optBytes);
        var optNode = new org.objectweb.asm.tree.ClassNode();
        crOpt.accept(optNode, 0);
        assertFalse(optNode.fields.stream().anyMatch(f -> "CHUNK_UPDATES_DYNAMIC".equals(f.name)),
                "Option.CHUNK_UPDATES_DYNAMIC field must be purged");

        byte[] optionsBytes = readRequiredResource("/srg/net/minecraft/client/Options.class");
        var crOptions = new org.objectweb.asm.ClassReader(optionsBytes);
        var optionsNode = new org.objectweb.asm.tree.ClassNode();
        crOptions.accept(optionsNode, 0);
        assertFalse(optionsNode.fields.stream().anyMatch(f -> "prChunkUpdatesDynamic".equals(f.name)),
                "Options.prChunkUpdatesDynamic field must be purged");

        for (var mn : optionsNode.methods) {
            for (var insn : mn.instructions.toArray()) {
                if (insn instanceof org.objectweb.asm.tree.FieldInsnNode fn) {
                    assertFalse("prChunkUpdatesDynamic".equals(fn.name),
                            "No method in Options may access prChunkUpdatesDynamic (" + mn.name + ")");
                    assertFalse("CHUNK_UPDATES_DYNAMIC".equals(fn.name),
                            "No method in Options may access CHUNK_UPDATES_DYNAMIC (" + mn.name + ")");
                }
            }
        }

        byte[] configBytes = readRequiredResource("/srg/net/pryzma/Config.class");
        var crConfig = new org.objectweb.asm.ClassReader(configBytes);
        var configNode = new org.objectweb.asm.tree.ClassNode();
        crConfig.accept(configNode, 0);
        var isDyn = configNode.methods.stream()
                .filter(m -> "isDynamicUpdates".equals(m.name) && "()Z".equals(m.desc))
                .findFirst().orElseThrow();
        boolean returnsFalseConstant = false;
        for (var insn : isDyn.instructions.toArray()) {
            if (insn.getOpcode() == org.objectweb.asm.Opcodes.ICONST_0) {
                returnsFalseConstant = true;
            }
            if (insn instanceof org.objectweb.asm.tree.FieldInsnNode fn) {
                assertFalse("prChunkUpdatesDynamic".equals(fn.name),
                        "Config.isDynamicUpdates must not access prChunkUpdatesDynamic");
            }
        }
        assertTrue(returnsFalseConstant, "Config.isDynamicUpdates must return false constant");
    }

    @Test
    void paintingRendererTransformerInjectsFastPaintingCheck() {
        var node = new org.objectweb.asm.tree.ClassNode();
        node.name = "net/minecraft/client/renderer/entity/PaintingRenderer";
        node.superName = "net/minecraft/client/renderer/entity/EntityRenderer";
        node.version = org.objectweb.asm.Opcodes.V21;

        var m = new org.objectweb.asm.tree.MethodNode(
                org.objectweb.asm.Opcodes.ACC_PRIVATE,
                "renderPainting",
                "(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/entity/decoration/Painting;IILnet/minecraft/client/renderer/texture/TextureAtlasSprite;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V",
                null,
                null
        );
        m.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.RETURN));
        m.maxStack = 7;
        m.maxLocals = 8;
        node.methods.add(m);

        assertTrue(PaintingRendererTransformer.inject(node));
        assertFalse(PaintingRendererTransformer.inject(node));
        assertVerifies(node);

        boolean callsHelper = false;
        for (var insn : m.instructions) {
            if (insn instanceof org.objectweb.asm.tree.MethodInsnNode minsn
                    && "net/pryzma/util/FastPaintingHelper".equals(minsn.owner)
                    && "renderPainting".equals(minsn.name)) {
                callsHelper = true;
                break;
            }
        }
        assertTrue(callsHelper, "PaintingRenderer.renderPainting must call FastPaintingHelper.renderPainting");
    }

    @Test
    void langFilesIntegrityAndFastPaintingsPresent() throws Exception {
        java.nio.file.Path langDir = java.nio.file.Path.of("src/main/resources/assets/minecraft/optifine/lang");
        assertTrue(java.nio.file.Files.isDirectory(langDir), "Lang directory must exist");

        try (var stream = java.nio.file.Files.list(langDir)) {
            List<java.nio.file.Path> langFiles = stream
                    .filter(p -> p.getFileName().toString().endsWith(".lang"))
                    .toList();
            assertEquals(40, langFiles.size(), "All 40 OptiFine lang files must be present");

            for (java.nio.file.Path p : langFiles) {
                String content = java.nio.file.Files.readString(p, java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(content.contains("pr.options.FAST_PAINTINGS="),
                        "Missing pr.options.FAST_PAINTINGS in " + p.getFileName());
                assertTrue(content.contains("pr.options.FAST_PAINTINGS.tooltip.1="),
                        "Missing tooltip.1 in " + p.getFileName());
                assertTrue(content.contains("pr.options.FAST_PAINTINGS.tooltip.5="),
                        "Missing tooltip.5 in " + p.getFileName());
            }
        }
    }

    @Test
    void guiOtherSettingsHasFourteenOptionsWithFeedbackButtonsAndNoTelemetry() throws Exception {
        byte[] bytes = readRequiredResource("/srg/net/pryzma/gui/GuiOtherSettingsPryzma.class");
        org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(bytes);
        org.objectweb.asm.tree.ClassNode cn = new org.objectweb.asm.tree.ClassNode();
        cr.accept(cn, 0);

        org.objectweb.asm.tree.MethodNode initMethod = null;
        for (org.objectweb.asm.tree.MethodNode m : cn.methods) {
            if ("init".equals(m.name) && "()V".equals(m.desc)) {
                initMethod = m;
                break;
            }
        }
        assertNotNull(initMethod, "GuiOtherSettingsPryzma.init missing");

        boolean hasArraySize14 = false;
        boolean hasTelemetry = false;
        boolean hasLagometer = false;
        boolean hasProfiler = false;
        boolean hasShowGlErrors = false;
        boolean hasFeedbackButtons = false;

        for (org.objectweb.asm.tree.AbstractInsnNode insn : initMethod.instructions.toArray()) {
            if (insn.getOpcode() == org.objectweb.asm.Opcodes.BIPUSH
                    && ((org.objectweb.asm.tree.IntInsnNode) insn).operand == 14) {
                hasArraySize14 = true;
            }
            if (insn instanceof org.objectweb.asm.tree.FieldInsnNode fn) {
                if ("TELEMETRY".equals(fn.name)) {
                    hasTelemetry = true;
                }
                if ("LAGOMETER".equals(fn.name)) {
                    hasLagometer = true;
                }
                if ("PROFILER".equals(fn.name)) {
                    hasProfiler = true;
                }
                if ("SHOW_GL_ERRORS".equals(fn.name)) {
                    hasShowGlErrors = true;
                }
                if ("FEEDBACK_BUTTONS".equals(fn.name)) {
                    hasFeedbackButtons = true;
                }
            }
        }

        assertTrue(hasArraySize14, "Option array allocation size must be 14");
        assertFalse(hasTelemetry, "TELEMETRY must not be present in GUI array");
        assertTrue(hasLagometer, "LAGOMETER must be retained in GUI array");
        assertTrue(hasProfiler, "PROFILER must be retained in GUI array");
        assertTrue(hasShowGlErrors, "SHOW_GL_ERRORS must be retained in GUI array");
        assertTrue(hasFeedbackButtons, "FEEDBACK_BUTTONS must be present in GUI array");

        // Verify backward compatibility: fields and methods in Option, Options, and Config remain intact
        byte[] optBytes = readRequiredResource("/srg/net/pryzma/config/Option.class");
        String optLatin = new String(optBytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(optLatin.contains("TELEMETRY"), "Option.TELEMETRY field must be retained for compatibility");

        byte[] optionsBytes = readRequiredResource("/srg/net/minecraft/client/Options.class");
        String optionsLatin = new String(optionsBytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(optionsLatin.contains("prTelemetry"), "Options.prTelemetry field must be retained for compatibility");

        byte[] configBytes = readRequiredResource("/srg/net/pryzma/Config.class");
        String configLatin = new String(configBytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(configLatin.contains("isTelemetryOn"), "Config.isTelemetryOn method must be retained for compatibility");

        org.objectweb.asm.tree.analysis.Analyzer<org.objectweb.asm.tree.analysis.BasicValue> analyzer =
                new org.objectweb.asm.tree.analysis.Analyzer<>(new org.objectweb.asm.tree.analysis.BasicVerifier());
        analyzer.analyze(cn.name, initMethod);
    }

    @Test
    void chunkUpdateHelperBudgetFormulaAndScheduling() {
        assertEquals(20, net.pryzma.util.ChunkUpdateHelper.getBudgetPerFrame(1, 10));
        assertEquals(40, net.pryzma.util.ChunkUpdateHelper.getBudgetPerFrame(2, 10));
        assertEquals(80, net.pryzma.util.ChunkUpdateHelper.getBudgetPerFrame(3, 10));
        assertEquals(160, net.pryzma.util.ChunkUpdateHelper.getBudgetPerFrame(4, 10));
        assertEquals(Integer.MAX_VALUE, net.pryzma.util.ChunkUpdateHelper.getBudgetPerFrame(5, 10));

        // Clamping worker count to minimum 1
        assertEquals(2, net.pryzma.util.ChunkUpdateHelper.getBudgetPerFrame(1, 0));
        assertEquals(8, net.pryzma.util.ChunkUpdateHelper.getBudgetPerFrame(3, 0));

        // Null and empty safety
        net.pryzma.util.ChunkUpdateHelper.scheduleChunkUpdates(null, null, null);
        net.pryzma.util.ChunkUpdateHelper.scheduleChunkUpdates(null, null, java.util.List.of());
    }

    @Test
    void pryzmaChunkExecutorResizesPoolDynamically() {
        for (int option = 1; option <= 5; option++) {
            net.pryzma.util.PryzmaChunkExecutor.applyChunkUpdatesOption(option);
            assertTrue(net.pryzma.util.PryzmaChunkExecutor.getWorkerCount() >= 1);
        }
    }

    @Test
    void levelRendererCompileSectionsInvokesChunkUpdateHelper() throws Exception {
        byte[] bytes = readRequiredResource("/srg/net/minecraft/client/renderer/LevelRenderer.class");
        org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(bytes);
        org.objectweb.asm.tree.ClassNode cn = new org.objectweb.asm.tree.ClassNode();
        cr.accept(cn, 0);

        org.objectweb.asm.tree.MethodNode compileMethod = null;
        for (org.objectweb.asm.tree.MethodNode m : cn.methods) {
            if ("compileSections".equals(m.name) && "(Lnet/minecraft/client/Camera;)V".equals(m.desc)) {
                compileMethod = m;
                break;
            }
        }
        assertNotNull(compileMethod, "LevelRenderer.compileSections missing");

        boolean hasHelperCall = false;
        boolean hasLegacyConfigLoop = false;

        for (org.objectweb.asm.tree.AbstractInsnNode insn : compileMethod.instructions.toArray()) {
            if (insn instanceof org.objectweb.asm.tree.MethodInsnNode minsn) {
                if ("net/pryzma/util/ChunkUpdateHelper".equals(minsn.owner)
                        && "scheduleChunkUpdates".equals(minsn.name)) {
                    hasHelperCall = true;
                }
                if ("net/pryzma/Config".equals(minsn.owner)
                        && "getUpdatesPerFrame".equals(minsn.name)) {
                    hasLegacyConfigLoop = true;
                }
            }
        }

        assertTrue(hasHelperCall, "compileSections must invoke ChunkUpdateHelper.scheduleChunkUpdates");
        assertFalse(hasLegacyConfigLoop, "Legacy Config.getUpdatesPerFrame throttle loop must be removed");

        org.objectweb.asm.tree.analysis.Analyzer<org.objectweb.asm.tree.analysis.BasicValue> analyzer =
                new org.objectweb.asm.tree.analysis.Analyzer<>(new org.objectweb.asm.tree.analysis.BasicVerifier());
        analyzer.analyze(cn.name, compileMethod);
    }

    @Test
    void gameRendererWaitForServerThreadDelegatesToSmoothWorldHelper() throws Exception {
        byte[] bytes = readRequiredResource("/srg/net/minecraft/client/renderer/GameRenderer.class");
        org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(bytes);
        org.objectweb.asm.tree.ClassNode cn = new org.objectweb.asm.tree.ClassNode();
        cr.accept(cn, 0);

        org.objectweb.asm.tree.MethodNode waitMethod = null;
        for (org.objectweb.asm.tree.MethodNode m : cn.methods) {
            if ("waitForServerThread".equals(m.name) && "()V".equals(m.desc)) {
                waitMethod = m;
                break;
            }
        }
        assertNotNull(waitMethod, "GameRenderer.waitForServerThread missing");

        boolean hasSmoothHelperCall = false;
        boolean hasSingleProcessorCheck = false;

        for (org.objectweb.asm.tree.AbstractInsnNode insn : waitMethod.instructions.toArray()) {
            if (insn instanceof org.objectweb.asm.tree.MethodInsnNode minsn) {
                if ("net/pryzma/util/SmoothWorldHelper".equals(minsn.owner)
                        && "waitForServerThread".equals(minsn.name)) {
                    hasSmoothHelperCall = true;
                }
                if ("net/pryzma/Config".equals(minsn.owner)
                        && "isSingleProcessor".equals(minsn.name)) {
                    hasSingleProcessorCheck = true;
                }
            }
        }

        assertTrue(hasSmoothHelperCall, "waitForServerThread must delegate to SmoothWorldHelper.waitForServerThread");
        assertFalse(hasSingleProcessorCheck, "Obsolete isSingleProcessor check must be removed");

        org.objectweb.asm.tree.analysis.Analyzer<org.objectweb.asm.tree.analysis.BasicValue> analyzer =
                new org.objectweb.asm.tree.analysis.Analyzer<>(new org.objectweb.asm.tree.analysis.BasicVerifier());
        analyzer.analyze(cn.name, waitMethod);
    }

    @Test
    void mobTransformerGraftsSmoothWorldAndPassesVerifier() throws Exception {
        org.objectweb.asm.tree.ClassNode mobNode = new org.objectweb.asm.tree.ClassNode();
        mobNode.name = "net/minecraft/world/entity/Mob";
        mobNode.superName = "net/minecraft/world/entity/LivingEntity";

        org.objectweb.asm.tree.MethodNode tick = new org.objectweb.asm.tree.MethodNode(
                org.objectweb.asm.Opcodes.ACC_PUBLIC,
                "tick",
                "()V",
                null,
                null);
        tick.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.RETURN));
        tick.maxStack = 1;
        tick.maxLocals = 1;
        mobNode.methods.add(tick);

        boolean injected = MobTransformer.inject(mobNode);
        assertTrue(injected, "MobTransformer must inject into Mob.tick()");

        // Verify idempotency
        assertFalse(MobTransformer.inject(mobNode), "MobTransformer must not inject twice");

        // Verify method bytecode validity
        org.objectweb.asm.tree.analysis.Analyzer<org.objectweb.asm.tree.analysis.BasicValue> analyzer =
                new org.objectweb.asm.tree.analysis.Analyzer<>(new org.objectweb.asm.tree.analysis.BasicVerifier());
        analyzer.analyze(mobNode.name, tick);
    }

    @Test
    void pauseScreenTransformerPatchesCreatePauseMenuAndVerifies() throws Exception {
        Path pauseScreenPath = Path.of("scratch", "PauseScreen.class");
        assertTrue(Files.isRegularFile(pauseScreenPath), "scratch/PauseScreen.class must exist");
        byte[] bytes = Files.readAllBytes(pauseScreenPath);
        org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(bytes);
        org.objectweb.asm.tree.ClassNode cn = new org.objectweb.asm.tree.ClassNode();
        cr.accept(cn, 0);

        boolean injected = PauseScreenTransformer.inject(cn);
        assertTrue(injected, "PauseScreenTransformer must inject into PauseScreen.createPauseMenu()");

        // Verify that Config.isFeedbackButtons check was inserted
        org.objectweb.asm.tree.MethodNode createMenu = cn.methods.stream()
                .filter(m -> "createPauseMenu".equals(m.name) && "()V".equals(m.desc))
                .findFirst()
                .orElseThrow();
        boolean hasConfigCall = false;
        boolean hasFeedbackRedirect = false;
        boolean hasServerLinksRedirect = false;
        for (org.objectweb.asm.tree.AbstractInsnNode insn : createMenu.instructions) {
            if (insn instanceof org.objectweb.asm.tree.MethodInsnNode minsn) {
                if ("net/pryzma/Config".equals(minsn.owner) && "isFeedbackButtons".equals(minsn.name)) {
                    hasConfigCall = true;
                }
                if ("net/pryzma/util/PauseScreenHelper".equals(minsn.owner) && "addFeedbackSubscreen".equals(minsn.name)) {
                    hasFeedbackRedirect = true;
                }
                if ("net/pryzma/util/PauseScreenHelper".equals(minsn.owner) && "addServerLinks".equals(minsn.name)) {
                    hasServerLinksRedirect = true;
                }
            }
        }
        assertTrue(hasConfigCall, "Must call Config.isFeedbackButtons");
        assertTrue(hasFeedbackRedirect, "Must redirect FEEDBACK_SUBSCREEN to PauseScreenHelper.addFeedbackSubscreen");
        assertTrue(hasServerLinksRedirect, "Must redirect SERVER_LINKS to PauseScreenHelper.addServerLinks");

        // Verify bytecode validity using ASM BasicVerifier
        org.objectweb.asm.tree.analysis.Analyzer<org.objectweb.asm.tree.analysis.BasicValue> analyzer =
                new org.objectweb.asm.tree.analysis.Analyzer<>(new org.objectweb.asm.tree.analysis.BasicVerifier());
        analyzer.analyze(cn.name, createMenu);

        // Verify ClassWriter can compute maxs cleanly
        org.objectweb.asm.ClassWriter cw = new org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        byte[] output = cw.toByteArray();
        assertTrue(output.length > 0, "Transformed class bytecode must serialize cleanly");
    }

    @Test
    void feedbackButtonsOptionAndConfigStateVerified() throws Exception {
        byte[] optionBytes = readRequiredResource("/srg/net/pryzma/config/Option.class");
        org.objectweb.asm.ClassReader crOpt = new org.objectweb.asm.ClassReader(optionBytes);
        org.objectweb.asm.tree.ClassNode cnOpt = new org.objectweb.asm.tree.ClassNode();
        crOpt.accept(cnOpt, 0);
        assertTrue(cnOpt.fields.stream().anyMatch(f -> "FEEDBACK_BUTTONS".equals(f.name)),
                "Option must declare FEEDBACK_BUTTONS field");

        byte[] configBytes = readRequiredResource("/srg/net/pryzma/Config.class");
        org.objectweb.asm.ClassReader crCfg = new org.objectweb.asm.ClassReader(configBytes);
        org.objectweb.asm.tree.ClassNode cnCfg = new org.objectweb.asm.tree.ClassNode();
        crCfg.accept(cnCfg, 0);
        assertTrue(cnCfg.methods.stream().anyMatch(m -> "isFeedbackButtons".equals(m.name) && "()Z".equals(m.desc)),
                "Config must declare isFeedbackButtons()Z method");

        byte[] optionsBytes = readRequiredResource("/srg/net/minecraft/client/Options.class");
        org.objectweb.asm.ClassReader crOptions = new org.objectweb.asm.ClassReader(optionsBytes);
        org.objectweb.asm.tree.ClassNode cnOptions = new org.objectweb.asm.tree.ClassNode();
        crOptions.accept(cnOptions, 0);
        assertTrue(cnOptions.fields.stream().anyMatch(f -> "prFeedbackButtons".equals(f.name) && "Z".equals(f.desc)),
                "Options must declare prFeedbackButtons field");
    }

    @Test
    void langFilesIntegrityAndNoDuplicateKeys() throws Exception {
        List<String> allLangs = List.of(
            "bg_bg", "ca_es", "cs_cz", "de_de", "en_gb", "en_ud", "en_us",
            "es_ar", "es_ch", "es_cl", "es_es", "es_mx", "es_ve", "et_ee",
            "fa_ir", "fi_fi", "fr_ca", "fr_fr", "hu_hu", "id_id", "it_it",
            "ja_jp", "ko_kr", "lb_lu", "nl_nl", "no_no", "pl_pl", "pt_br",
            "pt_pt", "ro_ro", "ru_ru", "sk_sk", "sv_se", "th_th", "tr_tr",
            "uk_ua", "vi_vn", "zh_cn", "zh_hk", "zh_tw"
        );
        assertEquals(40, allLangs.size(), "Must test all 40 language files");

        for (String lang : allLangs) {
            byte[] bytes = readRequiredResource("/assets/minecraft/optifine/lang/" + lang + ".lang");
            assertNotNull(bytes, lang + ".lang must exist in resources");
            String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            String[] lines = content.split("\r?\n");
            java.util.Set<String> keys = new java.util.HashSet<>();
            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                assertTrue(eq > 0, "Line in " + lang + ".lang must have key=value: " + line);
                String key = line.substring(0, eq).trim();
                assertFalse(keys.contains(key), "Duplicate key '" + key + "' in " + lang + ".lang");
                keys.add(key);
            }
            if ("en_gb".equals(lang)) {
                // en_gb is an override delta file for British spellings
                assertTrue(keys.contains("pr.options.FAST_MATH"), "FAST_MATH missing in en_gb");
                assertTrue(keys.contains("pr.options.CHUNK_UPDATES.tooltip.1"), "CHUNK_UPDATES tooltip missing in en_gb");
                assertTrue(keys.contains("pr.options.FEEDBACK_BUTTONS"), "FEEDBACK_BUTTONS missing in en_gb");
                continue;
            }
            // Verify critical updated options exist in all 39 full language files
            assertTrue(keys.contains("pr.options.CHUNK_UPDATES.tooltip.1"), "CHUNK_UPDATES tooltip missing in " + lang);
            assertTrue(keys.contains("pr.options.SMOOTH_WORLD.tooltip.1"), "SMOOTH_WORLD tooltip missing in " + lang);
            assertTrue(keys.contains("pr.options.FAST_MATH.tooltip.1"), "FAST_MATH tooltip missing in " + lang);
            assertTrue(keys.contains("pr.options.SMOOTH_FPS.tooltip.1"), "SMOOTH_FPS tooltip missing in " + lang);
            assertTrue(keys.contains("pr.options.LAZY_CHUNK_LOADING.tooltip.1"), "LAZY_CHUNK_LOADING tooltip missing in " + lang);
            assertTrue(keys.contains("pr.options.MIPMAP_TYPE"), "MIPMAP_TYPE missing in " + lang);
            assertTrue(keys.contains("pr.options.AF_LEVEL"), "AF_LEVEL missing in " + lang);
            assertTrue(keys.contains("pr.options.QUICK_INFO"), "QUICK_INFO missing in " + lang);
            assertTrue(keys.contains("pr.options.QUICK_INFO_FPS"), "QUICK_INFO_FPS missing in " + lang);
            assertTrue(keys.contains("pr.options.FEEDBACK_BUTTONS"), "FEEDBACK_BUTTONS missing in " + lang);
            assertTrue(keys.contains("pr.options.FEEDBACK_BUTTONS.tooltip.1"), "FEEDBACK_BUTTONS tooltip missing in " + lang);
        }
    }

    @Test
    void distantHorizonsModCheckerPatchedForIsolatedPryzmaCompat() {
        var node = new org.objectweb.asm.tree.ClassNode();
        node.name = "com/seibel/distanthorizons/neoforge/wrappers/modAccessor/ModChecker";
        node.superName = "java/lang/Object";
        node.version = org.objectweb.asm.Opcodes.V21;

        var m1 = new org.objectweb.asm.tree.MethodNode(
                org.objectweb.asm.Opcodes.ACC_PUBLIC, "isModLoaded", "(Ljava/lang/String;)Z", null, null);
        m1.instructions.add(new org.objectweb.asm.tree.MethodInsnNode(
                org.objectweb.asm.Opcodes.INVOKESTATIC, "net/neoforged/fml/ModList", "get", "()Lnet/neoforged/fml/ModList;", false));
        m1.instructions.add(new org.objectweb.asm.tree.VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, 1));
        m1.instructions.add(new org.objectweb.asm.tree.MethodInsnNode(
                org.objectweb.asm.Opcodes.INVOKEVIRTUAL, "net/neoforged/fml/ModList", "isLoaded", "(Ljava/lang/String;)Z", false));
        m1.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.IRETURN));
        m1.maxStack = 4;
        m1.maxLocals = 4;
        node.methods.add(m1);

        var m2 = new org.objectweb.asm.tree.MethodNode(
                org.objectweb.asm.Opcodes.ACC_PUBLIC, "modLocation", "(Ljava/lang/String;)Ljava/io/File;", null, null);
        m2.instructions.add(new org.objectweb.asm.tree.MethodInsnNode(
                org.objectweb.asm.Opcodes.INVOKESTATIC, "net/neoforged/fml/ModList", "get", "()Lnet/neoforged/fml/ModList;", false));
        m2.instructions.add(new org.objectweb.asm.tree.VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, 1));
        m2.instructions.add(new org.objectweb.asm.tree.MethodInsnNode(
                org.objectweb.asm.Opcodes.INVOKEVIRTUAL, "net/neoforged/fml/ModList", "getModFileById", "(Ljava/lang/String;)Lnet/neoforged/neoforgespi/language/IModFileInfo;", false));
        m2.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.ACONST_NULL));
        m2.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.ARETURN));
        m2.maxStack = 4;
        m2.maxLocals = 4;
        node.methods.add(m2);

        assertTrue(DistantHorizonsCompatTransformer.inject(node));
        assertFalse(DistantHorizonsCompatTransformer.inject(node));
        assertVerifies(node);

        assertTrue(invokes(node, "net/pryzma/reflect/ReflectorAdapter", "isModLoadedBridge", "(Lnet/neoforged/fml/ModList;Ljava/lang/String;)Z"));
        assertTrue(invokes(node, "net/pryzma/reflect/ReflectorAdapter", "getModFileByIdBridge", "(Lnet/neoforged/fml/ModList;Ljava/lang/String;)Lnet/neoforged/neoforgespi/language/IModFileInfo;"));
        assertFalse(invokes(node, "net/neoforged/fml/ModList", "isLoaded", "(Ljava/lang/String;)Z"));
        assertFalse(invokes(node, "net/neoforged/fml/ModList", "getModFileById", "(Ljava/lang/String;)Lnet/neoforged/neoforgespi/language/IModFileInfo;"));
    }

    @Test
    void levelRendererRedirectsDispatchRenderStageSToReflectorAdapter() throws Exception {
        var node = classNode("/srg/net/minecraft/client/renderer/LevelRenderer.class");
        assertTrue(LevelRendererTransformer.inject(node));

        int dispatchCalls = 0;
        for (var m : node.methods) {
            for (var insn : m.instructions) {
                if (insn instanceof org.objectweb.asm.tree.MethodInsnNode call
                        && "net/pryzma/reflect/ReflectorAdapter".equals(call.owner)
                        && "dispatchRenderStageS".equals(call.name)) {
                    dispatchCalls++;
                }
            }
        }
        assertEquals(8, dispatchCalls);
        assertFalse(invokes(node, "net/pryzma/reflect/ReflectorForge", "dispatchRenderStageS", null));
        assertFalse(invokes(node, "net/optifine/reflect/ReflectorForge", "dispatchRenderStageS", null));
    }

    @Test
    void optifineShaderStubDelegatesToPryzmaShaders() throws Exception {
        Class<?> stub = Class.forName("net.optifine.shaders.Shaders");
        var m = stub.getMethod("getShaderPackName");
        assertNotNull(m);
        assertTrue(java.lang.reflect.Modifier.isStatic(m.getModifiers()));
        Object res = m.invoke(null);
        assertTrue(res == null || res instanceof String);
    }

    @Test
    void reflectorForgeTransformerRewiresDispatchRenderStageSToReflectorAdapter() throws Exception {
        var node = classNode("/srg/net/pryzma/reflect/ReflectorForge.class");
        assertTrue(ReflectorForgeTransformer.inject(node));
        assertFalse(ReflectorForgeTransformer.inject(node));
        assertVerifies(node);

        var m = method(node, ReflectorForgeTransformer.METHOD, ReflectorForgeTransformer.DESC);
        assertNotNull(m);
        boolean methodCallsAdapter = false;
        boolean methodCallsReflector = false;
        for (var insn : m.instructions) {
            if (insn instanceof org.objectweb.asm.tree.MethodInsnNode call) {
                if ("net/pryzma/reflect/ReflectorAdapter".equals(call.owner) && "dispatchRenderStageS".equals(call.name)) {
                    methodCallsAdapter = true;
                }
                if ("net/pryzma/reflect/Reflector".equals(call.owner)) {
                    methodCallsReflector = true;
                }
            }
        }
        assertTrue(methodCallsAdapter);
        assertFalse(methodCallsReflector);
    }

    @Test
    void distantHorizonsNeoforgeMainRedirectsOptifineGateToPryzma() {
        var node = new org.objectweb.asm.tree.ClassNode();
        node.name = "com/seibel/distanthorizons/neoforge/NeoforgeMain";
        node.superName = "java/lang/Object";
        node.version = org.objectweb.asm.Opcodes.V21;

        var m = new org.objectweb.asm.tree.MethodNode(org.objectweb.asm.Opcodes.ACC_PROTECTED, "initializeModCompat", "()V", null, null);
        var gate = new org.objectweb.asm.tree.LdcInsnNode("optifine");
        m.instructions.add(new org.objectweb.asm.tree.VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, 0));
        m.instructions.add(gate);
        m.instructions.add(new org.objectweb.asm.tree.LdcInsnNode(org.objectweb.asm.Type.getType(DistantHorizonsCompatTransformer.OPTIFINE_ACCESSOR_DESC)));
        m.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.ACONST_NULL));
        m.instructions.add(new org.objectweb.asm.tree.MethodInsnNode(org.objectweb.asm.Opcodes.INVOKEVIRTUAL, node.name, "tryCreateModCompatAccessor", "(Ljava/lang/String;Ljava/lang/Class;Ljava/util/function/Supplier;)V", false));
        m.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.RETURN));
        m.maxStack = 4;
        m.maxLocals = 1;
        node.methods.add(m);

        assertTrue(DistantHorizonsCompatTransformer.inject(node));
        assertFalse(DistantHorizonsCompatTransformer.inject(node));
        assertVerifies(node);
        assertEquals("pryzma", gate.cst);
    }

    @Test
    void distantHorizonsAbstractOptifineAccessorRemapsShaderAndFogNames() {
        var node = new org.objectweb.asm.tree.ClassNode();
        node.name = "com/seibel/distanthorizons/core/wrapperInterfaces/modAccessor/AbstractOptifineAccessor";
        node.superName = "java/lang/Object";
        node.version = org.objectweb.asm.Opcodes.V21;

        var m = new org.objectweb.asm.tree.MethodNode(org.objectweb.asm.Opcodes.ACC_PUBLIC, "getIsShaderActive", "()Z", null, null);
        var shaders = new org.objectweb.asm.tree.LdcInsnNode("net.optifine.shaders.Shaders");
        var fog = new org.objectweb.asm.tree.LdcInsnNode("ofFogType");
        m.instructions.add(shaders);
        m.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.POP));
        m.instructions.add(fog);
        m.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.POP));
        m.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.ICONST_1));
        m.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.IRETURN));
        m.maxStack = 2;
        m.maxLocals = 1;
        node.methods.add(m);

        assertTrue(DistantHorizonsCompatTransformer.inject(node));
        assertFalse(DistantHorizonsCompatTransformer.inject(node));
        assertVerifies(node);
        assertEquals("net.pryzma.shaders.Shaders", shaders.cst);
        assertEquals("prFogType", fog.cst);
    }

    @Test
    void distantHorizonsClientApiGuardsLodPassesAgainstShadowPass() {
        var node = new org.objectweb.asm.tree.ClassNode();
        node.name = "com/seibel/distanthorizons/core/api/internal/ClientApi";
        node.superName = "java/lang/Object";
        node.version = org.objectweb.asm.Opcodes.V21;

        for (String pass : DistantHorizonsCompatTransformer.LOD_PASSES) {
            var m = new org.objectweb.asm.tree.MethodNode(org.objectweb.asm.Opcodes.ACC_PUBLIC, pass, "()V", null, null);
            m.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.RETURN));
            m.maxStack = 1;
            m.maxLocals = 1;
            node.methods.add(m);
        }

        assertTrue(DistantHorizonsCompatTransformer.inject(node));
        assertFalse(DistantHorizonsCompatTransformer.inject(node));
        assertVerifies(node);

        for (var m : node.methods) {
            assertTrue(m.instructions.getFirst() instanceof org.objectweb.asm.tree.FieldInsnNode f
                    && "net/pryzma/shaders/Shaders".equals(f.owner) && "isShadowPass".equals(f.name));
        }
    }

    @Test
    void shadersGuiDoesNotInstantiateDownloadButtonOrUri() throws Exception {
        var node = classNode("/srg/net/pryzma/shaders/gui/GuiShaders.class");
        assertVerifies(node);
        for (var m : node.methods) {
            for (var insn : m.instructions) {
                if (insn.getOpcode() == org.objectweb.asm.Opcodes.NEW && insn instanceof org.objectweb.asm.tree.TypeInsnNode tin) {
                    assertFalse("net/pryzma/shaders/gui/GuiButtonDownloadShaders".equals(tin.desc),
                            "GuiShaders must not instantiate GuiButtonDownloadShaders");
                }
            }
        }
    }

    private static byte[] readRequiredResource(String path) throws Exception {
        InputStream in = PryzmaMod.class.getResourceAsStream(path);
        assertNotNull(in, path);
        return in.readAllBytes();
    }

    private static byte[] classBytes(Class<?> cls) throws Exception {
        String name = "/" + cls.getName().replace('.', '/') + ".class";
        InputStream in = cls.getResourceAsStream(name);
        assertNotNull(in, name);
        return in.readAllBytes();
    }

    private static void putZip(ZipOutputStream zos, String name, byte[] data) throws Exception {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(data);
        zos.closeEntry();
    }

    private static Path findBuiltModJar() throws Exception {
        Path libs = Path.of("build", "libs");
        if (!Files.isDirectory(libs)) {
            return null;
        }
        Path found = null;
        try (var stream = Files.list(libs)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                String n = p.getFileName().toString();
                if (n.endsWith(".jar") && !n.contains("sources") && !n.contains("javadoc")) {
                    if (found == null || Files.getLastModifiedTime(p).compareTo(Files.getLastModifiedTime(found)) > 0) {
                        found = p;
                    }
                }
            }
        }
        return found;
    }
}
