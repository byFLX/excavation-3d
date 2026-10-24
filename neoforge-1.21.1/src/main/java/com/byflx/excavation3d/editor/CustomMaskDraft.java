package com.byflx.excavation3d.editor;

import com.byflx.excavation3d.shape.Cell;
import com.byflx.excavation3d.shape.ShapeLimits;
import com.byflx.excavation3d.shape.ShapeMask;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.Objects;

/**
 * Mutable layer-editor draft of a custom mask (plan §6.5). Only the editor holds one; it becomes an
 * immutable {@link ShapeMask} through {@link #toMask()}. Edits use the owning EditorDraft history,
 * or this grid's own history when used on its own.
 *
 * <p>Painting sets or clears cells instead of toggling them, so a drag that passes a cell twice
 * cannot flip it back. A stroke records a single undo step, taken lazily at its first real change.
 */
public final class CustomMaskDraft {
    public static final int MAX_UNDO = 64;

    private record Snapshot(int width, int height, int depth, BitSet bits, Cell anchor) {
    }

    /** Outcome of checking a resize before applying it. */
    public record ResizeCheck(int width, int height, int depth, int lostCells, boolean anchorInside) {
        public boolean allowedWithoutConfirmation() {
            return anchorInside && lostCells == 0;
        }
    }

    private int width;
    private int height;
    private int depth;
    private BitSet bits;
    private Cell anchor;
    private BitSet clipboard;
    private int clipboardWidth;
    private int clipboardHeight;
    private Runnable editListener;

    /** Lets the owning editor record mask edits in its chronological draft history. */
    void onEdit(Runnable listener) { editListener = listener; }

    private boolean strokeActive;
    private boolean strokeSaved;
    private final Deque<Snapshot> undo = new ArrayDeque<>();
    private final Deque<Snapshot> redo = new ArrayDeque<>();

    private CustomMaskDraft(int width, int height, int depth, BitSet bits, Cell anchor) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.bits = bits;
        this.anchor = anchor;
    }

    public static CustomMaskDraft empty(int width, int height, int depth) {
        checkAxes(width, height, depth);
        return new CustomMaskDraft(width, height, depth, new BitSet(), new Cell((width - 1) / 2, (height - 1) / 2, 0));
    }

    public static CustomMaskDraft fromMask(ShapeMask mask) {
        Objects.requireNonNull(mask, "mask");
        return new CustomMaskDraft(mask.width(), mask.height(), mask.depth(), mask.toBitSet(), mask.anchor());
    }

    /** Restores a speculative preview click, including history displaced at the undo limit. */
    Runnable previewRollback() {
        Snapshot before = snapshot();
        boolean previousStrokeActive = strokeActive, previousStrokeSaved = strokeSaved;
        Deque<Snapshot> previousUndo = new ArrayDeque<>(undo);
        Deque<Snapshot> previousRedo = new ArrayDeque<>(redo);
        return () -> {
            restore(before);
            strokeActive = previousStrokeActive;
            strokeSaved = previousStrokeSaved;
            undo.clear();
            undo.addAll(previousUndo);
            redo.clear();
            redo.addAll(previousRedo);
        };
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int depth() {
        return depth;
    }

    public Cell anchor() {
        return anchor;
    }

    public int count() {
        return bits.cardinality();
    }

    public boolean get(int x, int y, int z) {
        return inBox(x, y, z) && bits.get(index(x, y, z));
    }

    /** Starts a paint stroke: all changes until {@link #endStroke()} form one undo step. */
    public void beginStroke() {
        strokeActive = true;
        strokeSaved = false;
    }

    public void endStroke() {
        strokeActive = false;
    }

    /** Sets one cell. Returns whether anything changed. Outside the box nothing happens. */
    public boolean set(int x, int y, int z, boolean selected) {
        if (!selected && anchor.equals(new Cell(x, y, z))) return false;
        if (!inBox(x, y, z) || bits.get(index(x, y, z)) == selected) {
            return false;
        }
        if (!strokeActive || !strokeSaved) {
            pushUndo();
            strokeSaved = strokeActive;
        }
        bits.set(index(x, y, z), selected);
        return true;
    }

    public boolean setAnchor(int x, int y, int z) {
        if (!inBox(x, y, z) || anchor.equals(new Cell(x, y, z))) {
            return false;
        }
        pushUndo();
        anchor = new Cell(x, y, z);
        return true;
    }

    /** Selects or clears every cell of layer {@code z}. Returns whether anything changed. */
    public boolean fillLayer(int z, boolean selected) {
        if (z < 0 || z >= depth) {
            return false;
        }
        int from = index(0, 0, z);
        int to = from + width * height;
        BitSet layer = bits.get(from, to);
        if (!selected && anchor.z() == z) layer.clear(anchor.y() * width + anchor.x());
        boolean unchanged = selected ? layer.cardinality() == width * height : layer.isEmpty();
        if (unchanged) {
            return false;
        }
        pushUndo();
        boolean keepAnchor = get(anchor.x(), anchor.y(), anchor.z());
        bits.set(from, to, selected);
        if (keepAnchor) bits.set(index(anchor.x(), anchor.y(), anchor.z()));
        return true;
    }

    public void copyLayer(int z) {
        if (z < 0 || z >= depth) {
            return;
        }
        int from = index(0, 0, z);
        clipboard = bits.get(from, from + width * height);
        clipboardWidth = width;
        clipboardHeight = height;
    }

    public boolean hasClipboard() {
        return clipboard != null;
    }

    /**
     * Pastes the copied layer into layer {@code z}, cell by cell at the same x/y. Cells that do not
     * fit a smaller grid are dropped, uncovered cells are cleared. Returns whether anything changed.
     */
    public boolean pasteLayer(int z) {
        if (clipboard == null || z < 0 || z >= depth) {
            return false;
        }
        BitSet next = (BitSet) bits.clone();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean value = x < clipboardWidth && y < clipboardHeight && clipboard.get(y * clipboardWidth + x);
                next.set(index(x, y, z), value);
            }
        }
        if (get(anchor.x(), anchor.y(), anchor.z())) next.set(index(anchor.x(), anchor.y(), anchor.z()));
        if (next.equals(bits)) {
            return false;
        }
        pushUndo();
        bits = next;
        return true;
    }

    /** What a resize would cost. Growing keeps every cell at its coordinates; nothing is scaled. */
    public ResizeCheck checkResize(int newWidth, int newHeight, int newDepth) {
        checkAxes(newWidth, newHeight, newDepth);
        int lost = 0;
        for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1)) {
            int x = i % width;
            int rest = i / width;
            int y = rest % height;
            int z = rest / height;
            if (x >= newWidth || y >= newHeight || z >= newDepth) {
                lost++;
            }
        }
        boolean anchorInside = anchor.x() < newWidth && anchor.y() < newHeight && anchor.z() < newDepth;
        return new ResizeCheck(newWidth, newHeight, newDepth, lost, anchorInside);
    }

    /**
     * Resizes the grid. Refused (returns false) when the anchor would fall outside; cells outside
     * the new box are dropped, so callers must confirm {@link ResizeCheck#lostCells()} first.
     */
    public boolean resize(int newWidth, int newHeight, int newDepth) {
        ResizeCheck check = checkResize(newWidth, newHeight, newDepth);
        if (!check.anchorInside()) {
            return false;
        }
        if (newWidth == width && newHeight == height && newDepth == depth) {
            return true;
        }
        BitSet next = new BitSet(newWidth * newHeight * newDepth);
        for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1)) {
            int x = i % width;
            int rest = i / width;
            int y = rest % height;
            int z = rest / height;
            if (x < newWidth && y < newHeight && z < newDepth) {
                next.set((z * newHeight + y) * newWidth + x);
            }
        }
        pushUndo();
        width = newWidth;
        height = newHeight;
        depth = newDepth;
        bits = next;
        return true;
    }

    /**
     * Replaces the whole grid and anchor with {@code mask} as one undoable step, e.g. when a template
     * becomes the custom shape. Undo brings the previous grid back.
     */
    public void replaceWith(ShapeMask mask) {
        Objects.requireNonNull(mask, "mask");
        pushUndo();
        width = mask.width();
        height = mask.height();
        depth = mask.depth();
        bits = mask.toBitSet();
        anchor = mask.anchor();
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public boolean undo() {
        if (undo.isEmpty()) {
            return false;
        }
        redo.push(snapshot());
        restore(undo.pop());
        return true;
    }

    public boolean redo() {
        if (redo.isEmpty()) {
            return false;
        }
        undo.push(snapshot());
        restore(redo.pop());
        return true;
    }

    /** An immutable copy; may be empty or have an unselected anchor, which the validator reports. */
    public ShapeMask toMask() {
        return ShapeMask.of(width, height, depth, bits, anchor);
    }

    private void pushUndo() {
        if (editListener != null) {
            editListener.run();
            return;
        }
        undo.push(snapshot());
        while (undo.size() > MAX_UNDO) {
            undo.removeLast();
        }
        redo.clear();
    }

    private Snapshot snapshot() {
        return new Snapshot(width, height, depth, (BitSet) bits.clone(), anchor);
    }

    private void restore(Snapshot snapshot) {
        width = snapshot.width();
        height = snapshot.height();
        depth = snapshot.depth();
        bits = (BitSet) snapshot.bits().clone();
        anchor = snapshot.anchor();
    }

    private int index(int x, int y, int z) {
        return (z * height + y) * width + x;
    }

    private boolean inBox(int x, int y, int z) {
        return x >= 0 && x < width && y >= 0 && y < height && z >= 0 && z < depth;
    }

    private static void checkAxes(int width, int height, int depth) {
        int max = ShapeLimits.TECHNICAL_MAX_AXIS;
        if (width < 1 || width > max || height < 1 || height > max || depth < 1 || depth > max) {
            throw new IllegalArgumentException("grid size must be 1.." + max + " per axis: "
                    + width + "x" + height + "x" + depth);
        }
    }
}
