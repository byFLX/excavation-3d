package com.byflx.shapeexcavator.client.screen;

import com.byflx.shapeexcavator.shape.Cell;
import com.byflx.shapeexcavator.shape.GeneratedShape;
import com.byflx.shapeexcavator.shape.ShapeMask;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.Arrays;
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
 * only selected cells are drawn, so a plus looks like a plus and custom holes stay visible. Faces
 * between two selected cells are skipped. Adds a floor grid, labelled width/height/depth axes, the
 * anchor in gold and an arrow pointing from the front into the block.
 *
 * <p>Local axes: x = width (right), y = height (up), z = depth (into the block, away from the
 * viewer in the front view). The mesh is cached per shape; camera changes only change the matrix.
 */
public final class ShapePreviewRenderer {
    private static final int SELECTED = 0x5DA9E9;
    private static final int ANCHOR = 0xF1C232;
    private static final int ENVIRONMENT = 0x8C8C8C;
    private static final int GRID = 0xFF4A4A4A;
    private static final int AXIS_WIDTH = 0xFFE05050;
    private static final int AXIS_HEIGHT = 0xFF50D050;
    private static final int AXIS_DEPTH = 0xFF5080FF;
    private static final int FORWARD = 0xFFFFFFFF;
    // Brightness per face: -x, +x, -y, +y, -z (front), +z (back).
    private static final float[] SHADE = {0.78F, 0.78F, 0.5F, 1.0F, 0.9F, 0.6F};

    private GeneratedShape cachedShape;
    private boolean cachedResultView;
    private float[] vertices = new float[0];
    private int[] colors = new int[0];
    private int quads;

    /**
     * @param resultView draw the schematic "what remains" view for stairs instead of the selection
     */
    public void render(GuiGraphics graphics, Font font, int x0, int y0, int x1, int y1, GeneratedShape shape,
            PreviewCamera camera, boolean resultView) {
        if (!shape.equals(cachedShape) || resultView != cachedResultView) {
            rebuild(shape, resultView);
            cachedShape = shape;
            cachedResultView = resultView;
        }
        int w = shape.width();
        int h = shape.height();
        int d = shape.depth();
        float diagonal = (float) Math.sqrt((double) w * w + (double) h * h + (double) d * d) + 3.0F;
        float scale = Math.min(x1 - x0, y1 - y0) / diagonal * camera.zoom();

        graphics.enableScissor(x0, y0, x1, y1);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate((x0 + x1) / 2.0F, (y0 + y1) / 2.0F, 400.0F);
        pose.scale(scale, scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(-camera.elevation()));
        pose.mulPose(Axis.YP.rotationDegrees(camera.yaw()));
        // GUI space has y down and z towards the viewer; local space has y up and z into the block.
        pose.scale(1.0F, -1.0F, -1.0F);
        pose.translate(-w / 2.0F, -h / 2.0F, -d / 2.0F);
        Matrix4f matrix = new Matrix4f(pose.last().pose());

        VertexConsumer consumer = graphics.bufferSource().getBuffer(RenderType.gui());
        for (int q = 0; q < quads; q++) {
            quad(consumer, matrix, vertices, q * 12, colors[q]);
        }
        drawGrid(consumer, matrix, w, d);
        drawAxes(consumer, matrix, w, h, d);
        if (!resultView) {
            Cell anchor = shape.anchor();
            float ax = anchor.x() + 0.5F;
            float ay = anchor.y() + 0.5F;
            bar(consumer, matrix, ax, ay, anchor.z() - 1.8F, ax, ay, anchor.z(), 0.06F, FORWARD);
        }
        graphics.flush();
        pose.popPose();
        // The labels must stay readable in front of the cubes.
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

        label(graphics, font, matrix, w + 0.9F, 0, 0, Component.translatable("gui.shapeexcavator.axis.width", w),
                AXIS_WIDTH);
        label(graphics, font, matrix, 0, h + 0.9F, 0, Component.translatable("gui.shapeexcavator.axis.height", h),
                AXIS_HEIGHT);
        label(graphics, font, matrix, 0, 0, d + 0.9F, Component.translatable("gui.shapeexcavator.axis.depth", d),
                AXIS_DEPTH);
        if (!resultView) {
            Cell anchor = shape.anchor();
            label(graphics, font, matrix, anchor.x() + 0.5F, anchor.y() + 0.5F, anchor.z() - 2.2F,
                    Component.translatable("gui.shapeexcavator.axis.front"), FORWARD);
        }
        graphics.disableScissor();
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

    private static void drawGrid(VertexConsumer consumer, Matrix4f matrix, int w, int d) {
        float y = -0.02F;
        for (int x = 0; x <= w; x++) {
            bar(consumer, matrix, x, y, 0, x, y, d, 0.025F, GRID);
        }
        for (int z = 0; z <= d; z++) {
            bar(consumer, matrix, 0, y, z, w, y, z, 0.025F, GRID);
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

    private static void label(GuiGraphics graphics, Font font, Matrix4f matrix, float x, float y, float z,
            Component text, int color) {
        Vector3f screen = matrix.transformPosition(x, y, z, new Vector3f());
        graphics.drawString(font, text, Math.round(screen.x()) - font.width(text) / 2, Math.round(screen.y()) - 4,
                color);
    }
}
