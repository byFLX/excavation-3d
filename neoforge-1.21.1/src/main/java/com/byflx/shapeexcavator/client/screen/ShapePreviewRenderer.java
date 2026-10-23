package com.byflx.shapeexcavator.client.screen;

import com.byflx.shapeexcavator.preview.LabelPlacement;
import com.byflx.shapeexcavator.preview.PreviewScale;
import com.byflx.shapeexcavator.preview.ShapeDiff;
import com.byflx.shapeexcavator.preview.CustomCanvas;
import com.byflx.shapeexcavator.preview.PreviewPicking;
import com.byflx.shapeexcavator.shape.Cell;
import com.byflx.shapeexcavator.shape.GeneratedShape;
import com.byflx.shapeexcavator.shape.ShapeMask;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * Renders the selected cells of a {@link GeneratedShape} as real cubes in the editor (plan §5):
 * selected cells are solid, so a plus looks like a plus and custom holes stay visible. Faces
 * between two selected cells are skipped. Adds a floor grid, labelled width/height/depth axes, the
 * anchor in gold and a caption explaining the front direction.
 *
 * <p>The block scale stays fixed while the shape changes ({@link PreviewScale}), so a size change
 * reads as one against the 1-block floor grid; added cells flash green and removed cells red for a
 * second. Custom mode also draws the canvas and current-layer ghosts. Dimension labels sit beyond their own axis ends and outside the projected shape bounds;
 * a label is hidden if there is no free space.
 *
 * <p>Local axes: x = width (right), y = height (up), z = depth (into the block, away from the
 * viewer in the front view). The mesh is cached per shape; camera changes only change the matrix.
 */
public final class ShapePreviewRenderer {
    private static final int SELECTED = 0x5DA9E9;
    private static final int ANCHOR = 0xF1C232;
    private static final int ENVIRONMENT = 0x8C8C8C;
    private static final int ADDED = 0x20FF20;
    private static final int REMOVED = 0xFF2020;
    private static final int GRID = 0xFF3A3A3A;
    private static final int GRID_FOOTPRINT = 0xFF5A5A5A;
    private static final int AXIS_WIDTH = 0xFFE05050;
    private static final int AXIS_HEIGHT = 0xFF50D050;
    private static final int AXIS_DEPTH = 0xFF5080FF;
    private static final int FORWARD = 0xFFFFFFFF;
    /** Grid cells drawn beyond the footprint on every side. */
    private static final int GRID_EXTRA = 2;
    private static final long HIGHLIGHT_MS = 1000L;
    /** Distance of the labels from the scene edges; the caller keeps captions and buttons outside the scene. */
    private static final int LABEL_PADDING = 2;
    // Brightness per face: -x, +x, -y, +y, -z (front), +z (back).
    private static final float[] SHADE = {0.78F, 0.78F, 0.5F, 1.0F, 0.9F, 0.6F};

    private GeneratedShape cachedShape;
    private boolean cachedResultView;
    private float[] vertices = new float[0];
    private int[] colors = new int[0];
    private int quads;
    /** Pixels per block before the user's zoom; 0 until the first frame or after a reset. */
    private float baseScale;
    /** Shape and panel size {@link #baseScale} was last decided for. */
    private int scaledWidth;
    private int scaledHeight;
    private int scaledDepth;
    private int scaledPanelWidth;
    private int scaledPanelHeight;

    private GeneratedShape highlightShape;
    private ShapeDiff highlight = ShapeDiff.NONE;
    private long highlightStart;
    private PreviewPicking.Camera pickingCamera;
    private ShapeMask pickingMask;
    private int pickX0, pickY0, pickX1, pickY1;

    public Optional<PreviewPicking.Hit> pick(double x, double y, int layer) {
        if (pickingCamera == null || x < pickX0 || x >= pickX1 || y < pickY0 || y >= pickY1) {
            return Optional.empty();
        }
        return PreviewPicking.pick(pickingCamera, x, y, pickingMask, layer);
    }

    /** Forgets the block scale, so the next frame fits the shape again (double-click reset). */
    public void resetScale() {
        baseScale = 0.0F;
    }

    /**
     * Flashes the cells a size change added (green) and removed (red) while {@code after} is shown.
     * The diff is anchor-relative, see {@link ShapeDiff}.
     */
    public void highlight(ShapeDiff diff, GeneratedShape after) {
        highlight = diff;
        highlightShape = after;
        highlightStart = Util.getMillis();
    }

    /**
     * Draws into the scene rectangle {@code x0..x1, y0..y1}; the caller keeps captions and buttons
     * outside it.
     *
     * @param resultView draw the schematic "what remains" view for stairs instead of the selection
     */
    public void render(GuiGraphics graphics, Font font, int x0, int y0, int x1, int y1, GeneratedShape shape,
            PreviewCamera camera, boolean resultView, boolean custom, int layer, double mouseX, double mouseY,
            boolean anchorMode) {
        // Front gets a dedicated line, never part of a row of dimensions.
        int frontY = y1 - font.lineHeight - 2;
        y1 = frontY - 3;
        if (!shape.equals(cachedShape) || resultView != cachedResultView) {
            rebuild(shape, resultView);
            cachedShape = shape;
            cachedResultView = resultView;
        }
        int w = shape.width();
        int h = shape.height();
        int d = shape.depth();
        // Only a new shape size or panel size may change the block scale; camera zoom never does.
        if (!(baseScale > 0.0F) || w != scaledWidth || h != scaledHeight || d != scaledDepth
                || x1 - x0 != scaledPanelWidth || y1 - y0 != scaledPanelHeight) {
            baseScale = PreviewScale.next(baseScale, camera.zoom(), x1 - x0, y1 - y0, w, h, d);
            scaledWidth = w;
            scaledHeight = h;
            scaledDepth = d;
            scaledPanelWidth = x1 - x0;
            scaledPanelHeight = y1 - y0;
        }
        float scale = baseScale * camera.zoom();

        graphics.enableScissor(x0, y0, x1, y1);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pickingCamera = new PreviewPicking.Camera((x0 + x1) / 2.0, (y0 + y1) / 2.0, scale,
                camera.yaw(), camera.elevation(), w, h, d);
        pickingMask = shape.mask();
        pickX0 = x0; pickY0 = y0; pickX1 = x1; pickY1 = y1;
        pose.mulPose(new Matrix4f().set(pickingCamera.matrix()));
        Matrix4f matrix = new Matrix4f(pose.last().pose());
        ShapeDiff active = resultView ? ShapeDiff.NONE : activeHighlight(shape);

        List<PendingLabel> pending = labels(font, matrix, shape);
        List<LabelPlacement.AxisLabel> anchors = new ArrayList<>(pending.size());
        for (PendingLabel label : pending) {
            anchors.add(label.anchor());
        }
        LabelPlacement.Rect panel = new LabelPlacement.Rect(x0 + LABEL_PADDING, y0 + LABEL_PADDING,
                x1 - LABEL_PADDING, y1 - LABEL_PADDING);
        List<Optional<LabelPlacement.Rect>> layout = LabelPlacement.axisEnds(anchors,
                projectedBounds(matrix, w, h, d, resultView), panel);

        VertexConsumer consumer = graphics.bufferSource().getBuffer(RenderType.gui());
        drawGrid(consumer, matrix, w, d);
        if (custom) {
            outline(consumer, matrix, 0, 0, 0, w, h, d, 0.015F, 0x668DA9C0);
            for (Cell ghost : CustomCanvas.ghosts(shape.mask(), layer)) {
                outline(consumer, matrix, ghost.x(), ghost.y(), ghost.z(),
                        ghost.x() + 1, ghost.y() + 1, ghost.z() + 1, 0.008F, 0x244F6578);
            }
            if (!shape.mask().contains(shape.anchor().x(), shape.anchor().y(), shape.anchor().z())) {
                outlineCell(consumer, matrix, shape.anchor(), 0xFFF1C232);
            }
        }
        for (int q = 0; q < quads; q++) {
            quad(consumer, matrix, vertices, q * 12, colors[q]);
        }
        drawAxes(consumer, matrix, w, h, d);
        if (!resultView) {
            drawHighlight(consumer, matrix, shape, active);
            if (custom) {
                pick(mouseX, mouseY, layer).ifPresent(hit -> {
                    if (anchorMode) {
                        if (hit.selected()) outlineCell(consumer, matrix, hit.cell(), 0xFFF1C232);
                    } else {
                        if (hit.selected() && !hit.cell().equals(shape.anchor())) {
                            outlineCell(consumer, matrix, hit.cell(), 0xFFFF2020);
                        }
                        PreviewPicking.addTarget(hit, shape.mask())
                                .ifPresent(cell -> outlineCell(consumer, matrix, cell, 0xFF20FF20));
                    }
                });
            }
        }
        graphics.flush();
        pose.popPose();
        // The labels must stay readable in front of the cubes.
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

        for (int i = 0; i < pending.size(); i++) {
            if (layout.get(i).isEmpty()) continue;
            LabelPlacement.Rect at = layout.get(i).get();
            graphics.drawString(font, pending.get(i).text(), at.x0(), at.y0(), pending.get(i).color(), true);
        }
        graphics.disableScissor();
        if (!resultView) {
            graphics.enableScissor(x0, frontY, x1, frontY + font.lineHeight);
            graphics.drawCenteredString(font, Component.translatable("gui.shapeexcavator.axis.front"),
                    (x0 + x1) / 2, frontY, FORWARD);
            graphics.disableScissor();
        }
    }

    private record PendingLabel(Component text, int color, LabelPlacement.AxisLabel anchor) {
    }

    /** Labels use exactly the same endpoints as the rendered axes. */
    private static List<PendingLabel> labels(Font font, Matrix4f matrix, GeneratedShape shape) {
        int w = shape.width();
        int h = shape.height();
        int d = shape.depth();
        List<PendingLabel> labels = new ArrayList<>(3);
        labels.add(label(font, matrix, w + 0.6F, -0.05F, -0.05F, Component.translatable("gui.shapeexcavator.axis.width", w),
                AXIS_WIDTH));
        labels.add(label(font, matrix, -0.05F, h + 0.6F, -0.05F, Component.translatable("gui.shapeexcavator.axis.height", h),
                AXIS_HEIGHT));
        labels.add(label(font, matrix, -0.05F, -0.05F, d + 0.6F, Component.translatable("gui.shapeexcavator.axis.depth", d),
                AXIS_DEPTH));
        return labels;
    }

    private void rebuild(GeneratedShape shape, boolean resultView) {
        quads = 0;
        ShapeMask mask = shape.mask();
        if (!resultView) {
            for (Cell cell : mask.positions()) {
                int base = cell.equals(mask.anchor()) ? ANCHOR : SELECTED;
                addCube(cell.x(), cell.y(), cell.z(), base, (x, y, z) -> mask.contains(x, y, z));
            }
            return;
        }
        // Schematic result view: the remaining mass of the bounding box plus a floor layer below it.
        Solid environment = (x, y, z) -> x >= 0 && x < mask.width() && z >= 0 && z < mask.depth()
                && (y == -1 || (y >= 0 && y < mask.height() && !mask.contains(x, y, z)));
        for (int z = 0; z < mask.depth(); z++) {
            for (int y = -1; y < mask.height(); y++) {
                for (int x = 0; x < mask.width(); x++) {
                    if (environment.at(x, y, z)) {
                        addCube(x, y, z, ENVIRONMENT, environment);
                    }
                }
            }
        }
    }

    @FunctionalInterface
    private interface Solid {
        boolean at(int x, int y, int z);
    }

    private void addCube(int x, int y, int z, int base, Solid solid) {
        // A slight checkerboard keeps single blocks distinguishable in a flat surface.
        float parity = ((x + y + z) & 1) == 0 ? 1.0F : 0.88F;
        float x1 = x + 1;
        float y1 = y + 1;
        float z1 = z + 1;
        if (!solid.at(x - 1, y, z)) {
            addFace(base, 0, parity, x, y, z, x, y1, z, x, y1, z1, x, y, z1);
        }
        if (!solid.at(x + 1, y, z)) {
            addFace(base, 1, parity, x1, y, z, x1, y, z1, x1, y1, z1, x1, y1, z);
        }
        if (!solid.at(x, y - 1, z)) {
            addFace(base, 2, parity, x, y, z, x, y, z1, x1, y, z1, x1, y, z);
        }
        if (!solid.at(x, y + 1, z)) {
            addFace(base, 3, parity, x, y1, z, x1, y1, z, x1, y1, z1, x, y1, z1);
        }
        if (!solid.at(x, y, z - 1)) {
            addFace(base, 4, parity, x, y, z, x1, y, z, x1, y1, z, x, y1, z);
        }
        if (!solid.at(x, y, z + 1)) {
            addFace(base, 5, parity, x, y, z1, x, y1, z1, x1, y1, z1, x1, y, z1);
        }
    }

    private void addFace(int base, int face, float parity, float... corners) {
        if ((quads + 1) * 12 > vertices.length) {
            int capacity = Math.max(64, quads * 2);
            vertices = Arrays.copyOf(vertices, capacity * 12);
            colors = Arrays.copyOf(colors, capacity);
        }
        System.arraycopy(corners, 0, vertices, quads * 12, 12);
        colors[quads] = shade(base, SHADE[face] * parity);
        quads++;
    }

    private static int shade(int rgb, float factor) {
        int r = Math.min(255, Math.round(((rgb >> 16) & 0xFF) * factor));
        int g = Math.min(255, Math.round(((rgb >> 8) & 0xFF) * factor));
        int b = Math.min(255, Math.round((rgb & 0xFF) * factor));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** The size-change diff still shown on {@code shape}, or {@link ShapeDiff#NONE} once it faded out. */
    private ShapeDiff activeHighlight(GeneratedShape shape) {
        if (highlight.isEmpty() || !shape.equals(highlightShape)) {
            return ShapeDiff.NONE;
        }
        if (Util.getMillis() - highlightStart >= HIGHLIGHT_MS) {
            highlight = ShapeDiff.NONE;
            highlightShape = null;
        }
        return highlight;
    }

    /** Translucent overlays on added cells and ghost cubes for removed ones, fading out. */
    private void drawHighlight(VertexConsumer consumer, Matrix4f matrix, GeneratedShape shape, ShapeDiff diff) {
        if (diff.isEmpty()) {
            return;
        }
        long elapsed = Util.getMillis() - highlightStart;
        float fade = Math.max(0.0F, 1.0F - (float) elapsed / HIGHLIGHT_MS);
        Cell anchor = shape.anchor();
        overlay(consumer, matrix, diff.added(), anchor, ADDED, 0.95F * fade, 0.035F);
        overlay(consumer, matrix, diff.removed(), anchor, REMOVED, 0.85F * fade, 0.0F);
        for (Cell rel : diff.added()) outlineCell(consumer, matrix,
                new Cell(rel.x() + anchor.x(), rel.y() + anchor.y(), rel.z() + anchor.z()),
                (Math.round(255 * fade) << 24) | ADDED);
        for (Cell rel : diff.removed()) outlineCell(consumer, matrix,
                new Cell(rel.x() + anchor.x(), rel.y() + anchor.y(), rel.z() + anchor.z()),
                (Math.round(255 * fade) << 24) | REMOVED);
    }

    private static void outlineCell(VertexConsumer c, Matrix4f m, Cell p, int color) {
        outline(c, m, p.x() - 0.04F, p.y() - 0.04F, p.z() - 0.04F,
                p.x() + 1.04F, p.y() + 1.04F, p.z() + 1.04F, 0.025F, color);
    }

    private static void outline(VertexConsumer c, Matrix4f m, float x, float y, float z,
            float x1, float y1, float z1, float t, int color) {
        for (float yy : new float[] {y, y1}) {
            for (float zz : new float[] {z, z1}) bar(c, m, x, yy, zz, x1, yy, zz, t, color);
        }
        for (float xx : new float[] {x, x1}) {
            for (float zz : new float[] {z, z1}) bar(c, m, xx, y, zz, xx, y1, zz, t, color);
            for (float yy : new float[] {y, y1}) bar(c, m, xx, yy, z, xx, yy, z1, t, color);
        }
    }

    /** Outer faces of the anchor-relative {@code cells}, placed at the shown shape's anchor. */
    private static void overlay(VertexConsumer consumer, Matrix4f matrix, Set<Cell> cells, Cell anchor, int rgb,
            float alpha, float grow) {
        int argb = (Math.round(alpha * 255) << 24) | rgb;
        for (Cell rel : cells) {
            int x = rel.x() + anchor.x();
            int y = rel.y() + anchor.y();
            int z = rel.z() + anchor.z();
            float x0 = x - grow;
            float y0 = y - grow;
            float z0 = z - grow;
            float x1 = x + 1 + grow;
            float y1 = y + 1 + grow;
            float z1 = z + 1 + grow;
            if (!cells.contains(new Cell(rel.x() - 1, rel.y(), rel.z()))) {
                quad(consumer, matrix, new float[] {x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1}, argb);
            }
            if (!cells.contains(new Cell(rel.x() + 1, rel.y(), rel.z()))) {
                quad(consumer, matrix, new float[] {x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0}, argb);
            }
            if (!cells.contains(new Cell(rel.x(), rel.y() - 1, rel.z()))) {
                quad(consumer, matrix, new float[] {x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0}, argb);
            }
            if (!cells.contains(new Cell(rel.x(), rel.y() + 1, rel.z()))) {
                quad(consumer, matrix, new float[] {x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1}, argb);
            }
            if (!cells.contains(new Cell(rel.x(), rel.y(), rel.z() - 1))) {
                quad(consumer, matrix, new float[] {x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0}, argb);
            }
            if (!cells.contains(new Cell(rel.x(), rel.y(), rel.z() + 1))) {
                quad(consumer, matrix, new float[] {x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1}, argb);
            }
        }
    }

    /** A 1-block floor grid reaching a little beyond the footprint; the footprint lines are brighter. */
    private static void drawGrid(VertexConsumer consumer, Matrix4f matrix, int w, int d) {
        float y = -0.02F;
        int from = -GRID_EXTRA;
        for (int x = from; x <= w + GRID_EXTRA; x++) {
            int color = x >= 0 && x <= w ? GRID_FOOTPRINT : GRID;
            bar(consumer, matrix, x, y, from, x, y, d + GRID_EXTRA, 0.02F, color);
        }
        for (int z = from; z <= d + GRID_EXTRA; z++) {
            int color = z >= 0 && z <= d ? GRID_FOOTPRINT : GRID;
            bar(consumer, matrix, from, y, z, w + GRID_EXTRA, y, z, 0.02F, color);
        }
    }

    private static void drawAxes(VertexConsumer consumer, Matrix4f matrix, int w, int h, int d) {
        float o = -0.05F;
        bar(consumer, matrix, o, o, o, w + 0.6F, o, o, 0.05F, AXIS_WIDTH);
        bar(consumer, matrix, o, o, o, o, h + 0.6F, o, 0.05F, AXIS_HEIGHT);
        bar(consumer, matrix, o, o, o, o, o, d + 0.6F, 0.05F, AXIS_DEPTH);
    }

    /** An axis-aligned thin bar from a to b, drawn as two crossing quads so it shows from any angle. */
    private static void bar(VertexConsumer consumer, Matrix4f matrix, float ax, float ay, float az, float bx,
            float by, float bz, float t, int argb) {
        if (ax != bx) {
            quad(consumer, matrix, new float[] {ax, ay - t, az, bx, ay - t, az, bx, ay + t, az, ax, ay + t, az}, argb);
            quad(consumer, matrix, new float[] {ax, ay, az - t, bx, ay, az - t, bx, ay, az + t, ax, ay, az + t}, argb);
        } else if (ay != by) {
            quad(consumer, matrix, new float[] {ax - t, ay, az, ax + t, ay, az, ax + t, by, az, ax - t, by, az}, argb);
            quad(consumer, matrix, new float[] {ax, ay, az - t, ax, ay, az + t, ax, by, az + t, ax, by, az - t}, argb);
        } else {
            quad(consumer, matrix, new float[] {ax - t, ay, az, ax + t, ay, az, ax + t, ay, bz, ax - t, ay, bz}, argb);
            quad(consumer, matrix, new float[] {ax, ay - t, az, ax, ay + t, az, ax, ay + t, bz, ax, ay - t, bz}, argb);
        }
    }

    /** Emits the quad in both windings, so face culling never hides it whatever the camera angle. */
    private static void quad(VertexConsumer consumer, Matrix4f matrix, float[] c, int argb) {
        quad(consumer, matrix, c, 0, argb);
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix, float[] c, int offset, int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        for (int i = 0; i < 4; i++) {
            int v = offset + i * 3;
            consumer.addVertex(matrix, c[v], c[v + 1], c[v + 2]).setColor(r, g, b, a);
        }
        for (int i = 3; i >= 0; i--) {
            int v = offset + i * 3;
            consumer.addVertex(matrix, c[v], c[v + 1], c[v + 2]).setColor(r, g, b, a);
        }
    }

    private static LabelPlacement.Rect projectedBounds(Matrix4f matrix, int w, int h, int d, boolean result) {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        for (int x : new int[] {0, w}) for (int y : new int[] {result ? -1 : 0, h}) {
            for (int z : new int[] {0, d}) {
                Vector3f p = matrix.transformPosition(x, y, z, new Vector3f());
                minX = Math.min(minX, p.x()); minY = Math.min(minY, p.y());
                maxX = Math.max(maxX, p.x()); maxY = Math.max(maxY, p.y());
            }
        }
        return new LabelPlacement.Rect((int) Math.floor(minX) - 3, (int) Math.floor(minY) - 3,
                (int) Math.ceil(maxX) + 3, (int) Math.ceil(maxY) + 3);
    }

    private static PendingLabel label(Font font, Matrix4f matrix, float x, float y, float z,
            Component text, int color) {
        Vector3f screen = matrix.transformPosition(x, y, z, new Vector3f());
        Vector3f origin = matrix.transformPosition(-0.05F, -0.05F, -0.05F, new Vector3f());
        var label = new LabelPlacement.Label(Math.round(screen.x()), Math.round(screen.y()),
                font.width(text) + 1, font.lineHeight + 1);
        return new PendingLabel(text, color, new LabelPlacement.AxisLabel(label,
                Math.round(origin.x()), Math.round(origin.y())));
    }
}
