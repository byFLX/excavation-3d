package com.byflx.excavation3d.editor;

import com.byflx.excavation3d.preview.CustomCanvas;
import com.byflx.excavation3d.preview.PreviewPicking;
import com.byflx.excavation3d.shape.Cell;
import com.byflx.excavation3d.shape.GeneratedShape;
import com.byflx.excavation3d.shape.NumericInput;
import com.byflx.excavation3d.shape.Orientation;
import com.byflx.excavation3d.shape.ShapeConfig;
import com.byflx.excavation3d.shape.ShapeGenerator;
import com.byflx.excavation3d.shape.ShapeLimits;
import com.byflx.excavation3d.shape.ShapeMask;
import com.byflx.excavation3d.shape.ShapeParams;
import com.byflx.excavation3d.shape.ShapeTemplates;
import com.byflx.excavation3d.shape.ShapeTransform;
import com.byflx.excavation3d.shape.ShapeType;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The editor's draft (plan §2, §4): raw field texts per shape type, direction flags, the Custom transform
 * and the custom layer grid. Texts are kept exactly as typed, so a field may be empty while typing
 * and switching shape type keeps the other types' values. Nothing here is clamped or corrected;
 * {@link #evaluate(ShapeLimits)} reports field problems and shape issues as data.
 */
public final class EditorDraft {
    public static final String WIDTH = "width";
    public static final String HEIGHT = "height";
    public static final String DEPTH = "depth";
    public static final String ARM = "arm";
    public static final String STEPS = "steps";
    public static final String STEP_DEPTH = "stepDepth";
    public static final String STEP_HEIGHT = "stepHeight";
    public static final String CLEARANCE = "clearance";

    /**
     * @param fieldErrors   problems of unparsable fields, in field order; empty when all parse
     * @param resizePending custom grid size fields differ from the grid, awaiting confirmation
     * @param config        the configuration built from the fields, or null when a field is invalid
     * @param result        the generator result for {@code config}, or null when a field is invalid
     */
    public record Evaluation(Map<String, NumericInput.Problem> fieldErrors, boolean resizePending,
            ShapeConfig config, ShapeGenerator.Result result) {
        public boolean isValid() {
            return fieldErrors.isEmpty() && !resizePending && result instanceof ShapeGenerator.Valid;
        }

        public Optional<GeneratedShape> shape() {
            return result instanceof ShapeGenerator.Valid valid ? Optional.of(valid.shape()) : Optional.empty();
        }
    }

    private ShapeType type = ShapeType.CUBOID;
    private final Map<ShapeType, Map<String, String>> texts = new EnumMap<>(ShapeType.class);
    private boolean stairAscending = true;
    private boolean corridorUpward = true;
    private ShapeTransform transform = ShapeTransform.IDENTITY;
    private CustomMaskDraft custom = CustomMaskDraft.empty(3, 3, 1);
    private Orientation.Mode customOrientation = Orientation.Mode.HIT_FACE;
    private final Deque<State> undo = new ArrayDeque<>();
    private final Deque<State> redo = new ArrayDeque<>();
    private boolean editing;
    private boolean editSaved;

    private record State(ShapeType type, Map<ShapeType, Map<String, String>> texts,
            boolean ascending, boolean upward, ShapeTransform transform, Orientation.Mode orientation,
            CustomMaskDraft custom, Runnable restoreCustom) { }

    public enum PreviewEdit { CHANGED, UNCHANGED, INVALID, LIMIT }

    private State snapshot() {
        Map<ShapeType, Map<String, String>> copy = new EnumMap<>(ShapeType.class);
        texts.forEach((key, value) -> copy.put(key, Map.copyOf(value)));
        return new State(type, copy, stairAscending, corridorUpward, transform, customOrientation,
                custom, custom.previewRollback());
    }

    private void restore(State state) {
        type = state.type();
        state.texts().forEach((key, value) -> {
            texts.get(key).clear();
            texts.get(key).putAll(value);
        });
        stairAscending = state.ascending();
        corridorUpward = state.upward();
        transform = state.transform();
        customOrientation = state.orientation();
        custom = state.custom();
        state.restoreCustom().run();
        watchCustom();
    }

    private void watchCustom() {
        custom.onEdit(this::recordChange);
    }

    private void recordChange() {
        if (editing && editSaved) return;
        undo.push(snapshot());
        while (undo.size() > CustomMaskDraft.MAX_UNDO) undo.removeLast();
        redo.clear();
        if (editing) editSaved = true;
    }

    /** Groups an edit and its consequential resize into one history step. */
    public void edit(Runnable action) {
        if (editing) {
            action.run();
            return;
        }
        editing = true;
        editSaved = false;
        try { action.run(); }
        finally { editing = false; }
    }

    /** Restores a speculative first click without losing displaced undo entries or existing redo. */
    public Runnable previewRollback() {
        State before = snapshot();
        Deque<State> previousUndo = new ArrayDeque<>(undo);
        Deque<State> previousRedo = new ArrayDeque<>(redo);
        return () -> {
            restore(before);
            undo.clear();
            undo.addAll(previousUndo);
            redo.clear();
            redo.addAll(previousRedo);
        };
    }

    private EditorDraft() {
        for (ShapeType t : ShapeType.values()) {
            texts.put(t, new HashMap<>());
            load(ShapeTemplates.defaults(t));
        }
        watchCustom();
    }

    public static EditorDraft defaults() {
        return new EditorDraft();
    }

    /** A draft showing {@code config}; the other shape types keep their defaults. */
    public static EditorDraft fromConfig(ShapeConfig config) {
        Objects.requireNonNull(config, "config");
        EditorDraft draft = new EditorDraft();
        draft.load(config.params());
        draft.type = config.type();
        draft.transform = config.type() == ShapeType.CUSTOM ? config.transform() : ShapeTransform.IDENTITY;
        if (config.type() != ShapeType.CUSTOM && !config.transform().isIdentity()
                && ShapeGenerator.generate(config, ShapeLimits.DEFAULT) instanceof ShapeGenerator.Valid valid) {
            // Bake legacy transforms into the mask before template controls can discard them.
            draft.load(new ShapeParams.Custom(valid.shape().mask(), config.orientationMode()));
            draft.type = ShapeType.CUSTOM;
        }
        draft.watchCustom();
        return draft;
    }

    /** Loads a favorite atomically, retaining history and the other types' draft values. */
    public void loadFavorite(ShapeConfig config) {
        EditorDraft loaded = fromConfig(config);
        custom.endStroke();
        recordChange();
        type = loaded.type;
        texts.get(type).clear();
        texts.get(type).putAll(loaded.texts.get(type));
        if (type == ShapeType.STAIR) stairAscending = loaded.stairAscending;
        if (type == ShapeType.CORRIDOR) corridorUpward = loaded.corridorUpward;
        if (type == ShapeType.CUSTOM) {
            custom = loaded.custom;
            transform = loaded.transform;
            customOrientation = loaded.customOrientation;
        }
        watchCustom();
    }

    /** The numeric fields of {@code type}, in display order. */
    public static List<String> fields(ShapeType type) {
        return switch (type) {
            case CUBOID, CUSTOM -> List.of(WIDTH, HEIGHT, DEPTH);
            case PLUS -> List.of(WIDTH, HEIGHT, DEPTH, ARM);
            case STAIR -> List.of(WIDTH, STEPS, STEP_DEPTH, STEP_HEIGHT);
            case CORRIDOR -> List.of(WIDTH, CLEARANCE, STEPS, STEP_DEPTH, STEP_HEIGHT);
        };
    }

    public ShapeType type() {
        return type;
    }

    public void setType(ShapeType type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    public String text(String field) {
        return texts.get(type).getOrDefault(field, "");
    }

    public void setText(String field, String text) {
        String value = text == null ? "" : text;
        if (value.equals(text(field))) return;
        recordChange();
        texts.get(type).put(field, value);
    }

    public boolean stairAscending() {
        return stairAscending;
    }

    public void setStairAscending(boolean ascending) {
        if (stairAscending == ascending) return;
        recordChange();
        stairAscending = ascending;
    }

    public boolean corridorUpward() {
        return corridorUpward;
    }

    public void setCorridorUpward(boolean upward) {
        if (corridorUpward == upward) return;
        recordChange();
        corridorUpward = upward;
    }

    public ShapeTransform transform() {
        return type == ShapeType.CUSTOM ? transform : ShapeTransform.IDENTITY;
    }

    public void rotateClockwise() {
        if (type != ShapeType.CUSTOM) return;
        recordChange();
        transform = transform.rotatedClockwise();
    }

    public void toggleMirror() {
        if (type != ShapeType.CUSTOM) return;
        recordChange();
        transform = transform.mirroredHorizontally();
    }

    public CustomMaskDraft custom() {
        return custom;
    }

    /** Fills the cuboid fields as a surface template: depth 1 (plan §6.1). */
    public void applySurfaceTemplate() {
        recordChange();
        texts.get(ShapeType.CUBOID).put(DEPTH, "1");
    }

    /** Fills the cuboid fields as a line template along the width: height and depth 1 (plan §6.1). */
    public void applyLineTemplate() {
        recordChange();
        texts.get(ShapeType.CUBOID).put(HEIGHT, "1");
        texts.get(ShapeType.CUBOID).put(DEPTH, "1");
    }

    /**
     * The new field text after a plus/minus step, or empty when the current text is not a valid
     * value or the step would leave {@code 1..limits.maxAxis()}. Nothing is clamped.
     */
    public Optional<String> stepped(String field, int delta, ShapeLimits limits) {
        if (NumericInput.parse(text(field), 1, limits.maxAxis()) instanceof NumericInput.Valid valid) {
            long next = (long) valid.value() + delta;
            if (next >= 1 && next <= limits.maxAxis()) {
                return Optional.of(Long.toString(next));
            }
        }
        return Optional.empty();
    }

    public Evaluation evaluate(ShapeLimits limits) {
        Objects.requireNonNull(limits, "limits");
        Map<String, NumericInput.Problem> errors = new LinkedHashMap<>();
        Map<String, Integer> values = parseFields(limits, errors);
        if (!errors.isEmpty()) {
            return new Evaluation(Collections.unmodifiableMap(errors), false, null, null);
        }
        boolean resizePending = type == ShapeType.CUSTOM
                && (values.get(WIDTH) != custom.width()
                        || values.get(HEIGHT) != custom.height()
                        || values.get(DEPTH) != custom.depth());
        ShapeConfig config = ShapeConfig.of(params(values), transform());
        return new Evaluation(Map.of(), resizePending, config, ShapeGenerator.generate(config, limits));
    }

    /**
     * The resize the custom size fields ask for, or empty when they are invalid, already match the
     * grid or the draft is not custom.
     */
    public Optional<CustomMaskDraft.ResizeCheck> requestedCustomResize(ShapeLimits limits) {
        if (type != ShapeType.CUSTOM) {
            return Optional.empty();
        }
        Map<String, NumericInput.Problem> errors = new LinkedHashMap<>();
        Map<String, Integer> values = parseFields(limits, errors);
        if (!errors.isEmpty()) {
            return Optional.empty();
        }
        int w = values.get(WIDTH);
        int h = values.get(HEIGHT);
        int d = values.get(DEPTH);
        if (w == custom.width() && h == custom.height() && d == custom.depth()) {
            return Optional.empty();
        }
        return Optional.of(custom.checkResize(w, h, d));
    }

    /** Writes the custom grid size into the custom size fields, e.g. after undo or a resize. */
    public void syncCustomTexts() {
        Map<String, String> customTexts = texts.get(ShapeType.CUSTOM);
        customTexts.put(WIDTH, Integer.toString(custom.width()));
        customTexts.put(HEIGHT, Integer.toString(custom.height()));
        customTexts.put(DEPTH, Integer.toString(custom.depth()));
    }

    /**
     * The custom shape icon on a template (plan §6.5): the current template's local
     * mask becomes the custom grid and its orientation mode is kept, so the resulting selection is
     * unchanged. The replacement is one draft undo step, so the previous custom shape
     * can be restored. Returns false when the current template is invalid or already custom.
     */
    public boolean convertToCustom(ShapeLimits limits) {
        Optional<ShapeMask> mask = templateMask(limits);
        if (mask.isEmpty()) return false;
        Orientation.Mode mode = Orientation.Mode.forType(type);
        // The icon replaces the saved Custom draft; undo returns to that draft in Custom mode.
        type = ShapeType.CUSTOM;
        recordChange();
        custom = CustomMaskDraft.fromMask(mask.get());
        watchCustom();
        transform = ShapeTransform.IDENTITY;
        customOrientation = mode;
        syncCustomTexts();
        return true;
    }

    /** Shared local conversion for the Custom icon and the first preview edit. */
    private Optional<ShapeMask> templateMask(ShapeLimits limits) {
        if (type == ShapeType.CUSTOM) {
            return Optional.empty();
        }
        Map<String, NumericInput.Problem> errors = new LinkedHashMap<>();
        Map<String, Integer> values = parseFields(limits, errors);
        if (!errors.isEmpty()) {
            return Optional.empty();
        }
        ShapeGenerator.Result local = ShapeGenerator.generate(ShapeConfig.of(params(values)), limits);
        if (!(local instanceof ShapeGenerator.Valid valid)) {
            return Optional.empty();
        }
        return Optional.of(valid.shape().mask());
    }

    /** Converts and edits atomically; rejected clicks never change the type, grid or history. */
    public PreviewEdit editTemplatePreview(PreviewPicking.Hit hit, boolean add, ShapeLimits limits) {
        Optional<ShapeMask> local = templateMask(limits);
        if (local.isEmpty()) return PreviewEdit.INVALID;
        ShapeMask mask = local.get();
        if (!add && !hit.selected()) return PreviewEdit.UNCHANGED;
        Cell shownTarget = add && hit.selected() ? hit.face().neighbour(hit.cell()) : hit.cell();
        Cell target = CustomCanvas.toDraft(shownTarget, mask.width(), mask.height(), transform());
        if (mask.contains(target) == add || (!add && target.equals(mask.anchor()))) {
            return PreviewEdit.UNCHANGED;
        }
        Optional<CustomCanvas.Growth> growth = CustomCanvas.growToInclude(mask, target, limits);
        if (growth.isEmpty() || (add && mask.count() >= limits.maxSelected())) return PreviewEdit.LIMIT;
        CustomCanvas.Growth grown = growth.get();
        CustomMaskDraft edited = CustomMaskDraft.fromMask(grown.mask());
        Cell cell = grown.target();
        if (!edited.set(cell.x(), cell.y(), cell.z(), add)) return PreviewEdit.UNCHANGED;
        recordChange();
        customOrientation = Orientation.Mode.forType(type);
        custom = CustomMaskDraft.fromMask(edited.toMask());
        watchCustom();
        transform = ShapeTransform.IDENTITY;
        syncCustomTexts();
        type = ShapeType.CUSTOM;
        return PreviewEdit.CHANGED;
    }

    /** Restores only the selected type. The tool and favorites are never touched. */
    public void resetDefaults() {
        recordChange();
        load(ShapeTemplates.defaults(type));
        if (type == ShapeType.CUSTOM) {
            transform = ShapeTransform.IDENTITY;
            customOrientation = Orientation.Mode.HIT_FACE;
            watchCustom();
        }
    }

    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }

    public boolean undo() {
        if (!canUndo()) return false;
        custom.endStroke();
        redo.push(snapshot());
        restore(undo.pop());
        custom.endStroke();
        return true;
    }

    public boolean redo() {
        if (!canRedo()) return false;
        custom.endStroke();
        undo.push(snapshot());
        restore(redo.pop());
        custom.endStroke();
        return true;
    }

    private Map<String, Integer> parseFields(ShapeLimits limits, Map<String, NumericInput.Problem> errors) {
        Map<String, Integer> values = new HashMap<>();
        for (String field : fields(type)) {
            NumericInput.Result result = NumericInput.parse(text(field), 1, limits.maxAxis());
            if (result instanceof NumericInput.Valid valid) {
                values.put(field, valid.value());
            } else if (result instanceof NumericInput.Invalid invalid) {
                errors.put(field, invalid.problem());
            }
        }
        return values;
    }

    private ShapeParams params(Map<String, Integer> v) {
        return switch (type) {
            case CUBOID -> new ShapeParams.Cuboid(v.get(WIDTH), v.get(HEIGHT), v.get(DEPTH));
            case PLUS -> new ShapeParams.Plus(v.get(WIDTH), v.get(HEIGHT), v.get(DEPTH), v.get(ARM));
            case STAIR -> new ShapeParams.Stair(v.get(WIDTH), v.get(STEPS), v.get(STEP_DEPTH), v.get(STEP_HEIGHT),
                    stairAscending);
            case CORRIDOR -> new ShapeParams.Corridor(v.get(WIDTH), v.get(CLEARANCE), v.get(STEPS),
                    v.get(STEP_DEPTH), v.get(STEP_HEIGHT), corridorUpward);
            case CUSTOM -> new ShapeParams.Custom(custom.toMask(), customOrientation);
        };
    }

    private void load(ShapeParams params) {
        switch (params) {
            case ShapeParams.Cuboid c -> put(ShapeType.CUBOID, WIDTH, c.width(), HEIGHT, c.height(), DEPTH, c.depth());
            case ShapeParams.Plus p -> {
                put(ShapeType.PLUS, WIDTH, p.width(), HEIGHT, p.height(), DEPTH, p.depth());
                texts.get(ShapeType.PLUS).put(ARM, Integer.toString(p.arm()));
            }
            case ShapeParams.Stair s -> {
                put(ShapeType.STAIR, WIDTH, s.width(), STEPS, s.steps(), STEP_DEPTH, s.stepDepth());
                texts.get(ShapeType.STAIR).put(STEP_HEIGHT, Integer.toString(s.stepHeight()));
                stairAscending = s.ascending();
            }
            case ShapeParams.Corridor c -> {
                put(ShapeType.CORRIDOR, WIDTH, c.width(), CLEARANCE, c.clearance(), STEPS, c.steps());
                put(ShapeType.CORRIDOR, STEP_DEPTH, c.stepDepth(), STEP_HEIGHT, c.stepHeight(), WIDTH, c.width());
                corridorUpward = c.upward();
            }
            case ShapeParams.Custom c -> {
                custom = CustomMaskDraft.fromMask(c.mask());
                customOrientation = c.orientation();
                syncCustomTexts();
            }
        }
    }

    private void put(ShapeType target, String f1, int v1, String f2, int v2, String f3, int v3) {
        Map<String, String> map = texts.get(target);
        map.put(f1, Integer.toString(v1));
        map.put(f2, Integer.toString(v2));
        map.put(f3, Integer.toString(v3));
    }
}
