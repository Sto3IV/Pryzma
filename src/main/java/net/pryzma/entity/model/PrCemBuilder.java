package net.pryzma.entity.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import net.pryzma.core.expr.PrExpr;
import net.pryzma.core.expr.PrExprEnv;
import net.pryzma.core.expr.PrExprException;
import net.pryzma.core.expr.PrExprParser;
import net.pryzma.entity.PrEntityData;

/**
 * Applies a {@link PrJem} to a baked vanilla layer (OptiFine {@code CustomEntityModels.modifyModel}):
 * the tree is rebuilt as {@link PrCemPart}s under the same names, every {@code .jem} entry finds its
 * entity part through the OptiFine part name table, a replacing entry ({@code attach} false) clears
 * that part's own boxes and the children that are not entity parts themselves, and the custom part
 * is added as a child named {@code CEM-<part>}. Animations are compiled against the finished tree.
 */
final class PrCemBuilder {
    private final String name;
    private final Map<String, String> partNames;
    private final Consumer<String> warn;
    private final PrCemPart root;
    private final List<PrCemPart> customTops = new ArrayList<>();

    private PrCemBuilder(String name, ModelPart vanillaRoot, Map<String, String> partNames, Consumer<String> warn) {
        this.name = name;
        this.partNames = partNames;
        this.warn = warn;
        this.root = copyTree(vanillaRoot);
    }

    /** The rebuilt tree for {@code jem}, or the vanilla tree itself when nothing applied. */
    static ModelPart build(String name, PrJem jem, ModelPart vanillaRoot, Map<String, String> partNames, Consumer<String> warn) {
        PrCemBuilder b = new PrCemBuilder(name, vanillaRoot, partNames, warn);
        List<ModelPart> targets = new ArrayList<>();
        for (PrJem.Part part : jem.parts()) {
            ModelPart target = b.entityPart(part.part());
            if (target == null) {
                warn.accept("Model part not found: " + part.part() + " in " + name);
                targets.add(null);
                b.customTops.add(null);
                continue;
            }
            if (!part.attach()) {
                b.clear(target);
            }
            PrCemPart custom = custom(part);
            ((PrCemPart) target).addChild("CEM-" + part.part(), custom);
            targets.add(target);
            b.customTops.add(custom);
        }
        if (b.customTops.stream().allMatch(p -> p == null)) {
            return vanillaRoot;
        }
        List<Runnable> program = new ArrayList<>();
        for (int i = 0; i < jem.parts().size(); i++) {
            PrCemPart custom = b.customTops.get(i);
            if (custom != null) {
                b.compile(jem.parts().get(i), custom, targets.get(i), program);
            }
        }
        PrCemModel model = new PrCemModel(name, program.toArray(new Runnable[0]));
        b.root.getAllParts().forEach(p -> ((PrCemPart) p).model = model);
        return b.root;
    }

    // ------------------------------------------------------------------ tree

    private static PrCemPart copyTree(ModelPart vanilla) {
        PrCemPart copy = PrCemPart.copyOf(vanilla);
        vanilla.children.forEach((childName, child) -> copy.children.put(childName, copyTree(child)));
        return copy;
    }

    private static PrCemPart custom(PrJem.Part p) {
        List<PrCemGeometry.Quad> quads = new ArrayList<>();
        for (PrJem.Box box : p.boxes()) {
            quads.addAll(PrCemGeometry.quads(box, p.textureWidth(), p.textureHeight(), p.mirrorU()));
        }
        PrCemPart part = new PrCemPart(new ArrayList<>(), new LinkedHashMap<>(), quads.toArray(new PrCemGeometry.Quad[0]),
                p.texture(), p.id() == null ? "" : p.id());
        part.setPos(p.x(), p.y(), p.z());
        part.setRotation(p.xRot(), p.yRot(), p.zRot());
        part.xScale = p.scale();
        part.yScale = p.scale();
        part.zScale = p.scale();
        part.setInitialPose(part.storePose());
        int n = 0;
        for (PrJem.Part child : p.children()) {
            part.addChild("MR-" + n++, custom(child));
        }
        return part;
    }

    /** Clears a replaced part: its boxes and every child that is not itself an entity part. */
    private void clear(ModelPart target) {
        target.cubes.clear();
        Set<String> entityParts = new HashSet<>(partNames.values());
        target.children.keySet().removeIf(childName -> !entityParts.contains(childName) && !childName.startsWith("CEM-"));
    }

    /** An entity part by OptiFine name ({@code leg1}), vanilla name ({@code right_hind_leg}) or {@code root}. */
    private ModelPart entityPart(String optifineName) {
        if (optifineName == null) {
            return null;
        }
        if (optifineName.equals("root")) {
            return root;
        }
        String vanilla = partNames.getOrDefault(optifineName, optifineName);
        if (vanilla.equals("root")) {
            return root;
        }
        ModelPart found = findChild(root, vanilla);
        return found != null || vanilla.equals(optifineName) ? found : findChild(root, optifineName);
    }

    /** OptiFine {@code getChildModelDeep}: direct children first, then each child's subtree in order. */
    static ModelPart findChild(ModelPart part, String childName) {
        ModelPart direct = part.children.get(childName);
        if (direct != null) {
            return direct;
        }
        for (ModelPart child : part.children.values()) {
            ModelPart deep = findChild(child, childName);
            if (deep != null) {
                return deep;
            }
        }
        return null;
    }

    /** OptiFine {@code getChildDeepById}: a custom part with {@code .jem} id {@code id} below {@code part}. */
    static ModelPart findById(ModelPart part, String id) {
        for (ModelPart child : part.children.values()) {
            if (child instanceof PrCemPart p && id.equals(p.id)) {
                return child;
            }
        }
        for (ModelPart child : part.children.values()) {
            ModelPart deep = findById(child, id);
            if (deep != null) {
                return deep;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ animations

    private void compile(PrJem.Part jemPart, PrCemPart self, ModelPart target, List<Runnable> program) {
        PrExprParser parser = new PrExprParser(n -> variable(n, self, target), PrExprEnv.minecraft());
        for (Map<String, String> block : jemPart.animations()) {
            for (Map.Entry<String, String> assignment : block.entrySet()) {
                try {
                    program.add(assignment(parser, assignment.getKey(), assignment.getValue(), self, target));
                } catch (PrExprException | IllegalArgumentException e) {
                    warn.accept("Animation " + assignment.getKey() + ": " + assignment.getValue() + " in " + name + ": " + e.getMessage());
                }
            }
        }
    }

    private Runnable assignment(PrExprParser parser, String key, String value, PrCemPart self, ModelPart target) throws PrExprException {
        Variable v = writable(key, self, target);
        if (v == null) {
            throw new IllegalArgumentException("unknown target " + key);
        }
        if (v.isBool()) {
            PrExpr.B e = parser.parseBool(value);
            return () -> v.setBool(e.eval());
        }
        PrExpr.F e = parser.parseFloat(value);
        return () -> v.set(e.eval());
    }

    /** A readable variable for expressions: a writable one, or an entity parameter. */
    private PrExpr variable(String n, PrCemPart self, ModelPart target) {
        Variable v = writable(n, self, target);
        if (v != null) {
            return v.isBool() ? (PrExpr.B) v::getBool : (PrExpr.F) v::get;
        }
        return PrCemParams.param(n);
    }

    private Variable writable(String n, PrCemPart self, ModelPart target) {
        int dot = n.indexOf('.');
        if (dot <= 0 || dot != n.lastIndexOf('.')) {
            return null;
        }
        String owner = n.substring(0, dot);
        String field = n.substring(dot + 1);
        return switch (owner) {
            case "var" -> entityVariable(n, false);
            case "varb" -> entityVariable(n, true);
            case "render" -> renderVariable(field);
            default -> {
                ModelPart part = part(owner, self, target);
                yield part == null ? null : partVariable(part, field);
            }
        };
    }

    /** OptiFine {@code ModelResolver.getModelRenderer}, plus vanilla part names as EMF accepts them. */
    private ModelPart part(String partName, PrCemPart self, ModelPart target) {
        if (partName.indexOf(':') >= 0) {
            String[] path = partName.split(":");
            ModelPart current = part(path[0], self, target);
            for (int i = 1; i < path.length && current != null; i++) {
                current = findById(current, path[i]);
            }
            return current;
        }
        if (partName.equals("this")) {
            return self;
        }
        if (partName.equals("part")) {
            return target;
        }
        if (partNames.containsKey(partName) || partName.equals("root")) {
            return entityPart(partName);
        }
        for (PrCemPart top : customTops) {
            if (top == null) {
                continue;
            }
            if (partName.equals(top.id)) {
                return top;
            }
            ModelPart deep = findById(top, partName);
            if (deep != null) {
                return deep;
            }
        }
        return findChild(root, partName);
    }

    /** A model part value ({@code tx ty tz rx ry rz sx sy sz visible visible_boxes}). */
    private static Variable partVariable(ModelPart p, String field) {
        return switch (field) {
            case "tx" -> Variable.of(() -> p.x, v -> p.x = v);
            case "ty" -> Variable.of(() -> p.y, v -> p.y = v);
            case "tz" -> Variable.of(() -> p.z, v -> p.z = v);
            case "rx" -> Variable.of(() -> p.xRot, v -> p.xRot = v);
            case "ry" -> Variable.of(() -> p.yRot, v -> p.yRot = v);
            case "rz" -> Variable.of(() -> p.zRot, v -> p.zRot = v);
            case "sx" -> Variable.of(() -> p.xScale, v -> p.xScale = v);
            case "sy" -> Variable.of(() -> p.yScale, v -> p.yScale = v);
            case "sz" -> Variable.of(() -> p.zScale, v -> p.zScale = v);
            case "visible" -> Variable.ofBool(() -> p.visible, v -> p.visible = v);
            case "visible_boxes" -> Variable.ofBool(() -> !p.skipDraw, v -> p.skipDraw = !v);
            default -> null;
        };
    }

    private static Variable renderVariable(String field) {
        return switch (field) {
            case "shadow_size" -> Variable.of(() -> PrCemContext.renderer == null ? 0.0F : PrCemContext.renderer.shadowRadius,
                    v -> {
                        if (PrCemContext.renderer != null) {
                            PrCemContext.renderer.shadowRadius = v;
                        }
                    });
            case "shadow_opacity" -> Variable.of(() -> PrCemContext.renderer == null ? 0.0F : PrCemContext.renderer.shadowStrength,
                    v -> {
                        if (PrCemContext.renderer != null) {
                            PrCemContext.renderer.shadowStrength = v;
                        }
                    });
            // OptiFine-only renderer fields; kept readable and writable so packs using them still load.
            case "leash_offset_x", "leash_offset_y", "leash_offset_z", "shadow_offset_x", "shadow_offset_z" -> Variable.of(() -> 0.0F, v -> { });
            default -> null;
        };
    }

    /** {@code var.*} and {@code varb.*}: values stored on the entity being rendered. */
    private static Variable entityVariable(String key, boolean bool) {
        int slot = SLOTS.computeIfAbsent(key, k -> SLOTS.size());
        Variable v = Variable.of(() -> read(slot), value -> write(slot, value));
        return bool ? Variable.ofBool(() -> read(slot) != 0.0F, b -> write(slot, b ? 1.0F : 0.0F)) : v;
    }

    private static final Map<String, Integer> SLOTS = new ConcurrentHashMap<>();

    private static float read(int slot) {
        Entity e = PrCemContext.entity;
        if (e == null) {
            return 0.0F;
        }
        float[] values = PrEntityData.of(e).cemVars;
        return values == null || slot >= values.length ? 0.0F : values[slot];
    }

    private static void write(int slot, float value) {
        Entity e = PrCemContext.entity;
        if (e == null) {
            return;
        }
        PrEntityData data = PrEntityData.of(e);
        if (data.cemVars == null || slot >= data.cemVars.length) {
            float[] grown = new float[Math.max(slot + 1, SLOTS.size())];
            if (data.cemVars != null) {
                System.arraycopy(data.cemVars, 0, grown, 0, data.cemVars.length);
            }
            data.cemVars = grown;
        }
        data.cemVars[slot] = value;
    }

    /** A float or boolean value with a getter and setter. */
    interface Variable {
        float get();

        void set(float value);

        boolean isBool();

        boolean getBool();

        void setBool(boolean value);

        interface FloatGetter {
            float get();
        }

        interface FloatSetter {
            void set(float value);
        }

        interface BoolGetter {
            boolean get();
        }

        interface BoolSetter {
            void set(boolean value);
        }

        static Variable of(FloatGetter get, FloatSetter set) {
            return new Variable() {
                public float get() {
                    return get.get();
                }

                public void set(float value) {
                    set.set(value);
                }

                public boolean isBool() {
                    return false;
                }

                public boolean getBool() {
                    return get.get() != 0.0F;
                }

                public void setBool(boolean value) {
                    set.set(value ? 1.0F : 0.0F);
                }
            };
        }

        static Variable ofBool(BoolGetter get, BoolSetter set) {
            return new Variable() {
                public float get() {
                    return get.get() ? 1.0F : 0.0F;
                }

                public void set(float value) {
                    set.set(value != 0.0F);
                }

                public boolean isBool() {
                    return true;
                }

                public boolean getBool() {
                    return get.get();
                }

                public void setBool(boolean value) {
                    set.set(value);
                }
            };
        }
    }
}
