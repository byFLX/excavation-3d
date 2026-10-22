package com.byflx.shapeexcavator.preview;

import com.byflx.shapeexcavator.shape.Cell;
import com.byflx.shapeexcavator.shape.ShapeMask;
import java.util.Optional;

/** Orthographic preview camera and ray picking, independent of Minecraft and JOML. */
public final class PreviewPicking {
    public static final double DRAG_THRESHOLD = 4.0;

    public record Vec(double x, double y, double z) {
        Vec plus(Vec v) { return new Vec(x + v.x, y + v.y, z + v.z); }
        Vec times(double n) { return new Vec(x * n, y * n, z * n); }
    }

    public enum Face {
        WEST(-1, 0, 0), EAST(1, 0, 0), DOWN(0, -1, 0), UP(0, 1, 0), FRONT(0, 0, -1), BACK(0, 0, 1);
        final int x, y, z;
        Face(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        public Cell neighbour(Cell c) { return new Cell(c.x() + x, c.y() + y, c.z() + z); }
    }

    public record Ray(Vec origin, Vec direction) { }
    public record Hit(Cell cell, Face face, boolean selected, double distance) { }

    /** Rotation matches Rx(-elevation) * Ry(yaw) * scale(1,-1,-1) in GUI space. */
    public record Camera(double centerX, double centerY, double scale, double yaw, double elevation,
                         int width, int height, int depth) {
        private Vec rotate(Vec p) {
            double cy = Math.cos(Math.toRadians(yaw)), sy = Math.sin(Math.toRadians(yaw));
            double ce = Math.cos(Math.toRadians(elevation)), se = Math.sin(Math.toRadians(elevation));
            double x = cy * p.x - sy * p.z;
            double z = -sy * p.x - cy * p.z;
            return new Vec(x, -ce * p.y + se * z, se * p.y + ce * z);
        }

        public Vec project(Vec p) {
            Vec v = rotate(new Vec(p.x - width / 2.0, p.y - height / 2.0, p.z - depth / 2.0));
            return new Vec(centerX + scale * v.x, centerY + scale * v.y, 400 + scale * v.z);
        }

        /** Column-major affine matrix used directly by the renderer. */
        public float[] matrix() {
            Vec x = rotate(new Vec(1, 0, 0)).times(scale);
            Vec y = rotate(new Vec(0, 1, 0)).times(scale);
            Vec z = rotate(new Vec(0, 0, 1)).times(scale);
            Vec o = project(new Vec(0, 0, 0));
            return new float[] {(float)x.x, (float)x.y, (float)x.z, 0,
                    (float)y.x, (float)y.y, (float)y.z, 0, (float)z.x, (float)z.y, (float)z.z, 0,
                    (float)o.x, (float)o.y, (float)o.z, 1};
        }

        private Vec inverseRotation(Vec p) {
            Vec x = rotate(new Vec(1, 0, 0)), y = rotate(new Vec(0, 1, 0)), z = rotate(new Vec(0, 0, 1));
            return new Vec(dot(x, p), dot(y, p), dot(z, p));
        }

        public Ray ray(double screenX, double screenY) {
            // Larger GUI z is in front. Start beyond any corner, then travel into the scene.
            double reach = width + height + depth + 4.0;
            Vec start = inverseRotation(new Vec((screenX - centerX) / scale, (screenY - centerY) / scale, reach));
            return new Ray(start.plus(new Vec(width / 2.0, height / 2.0, depth / 2.0)),
                    inverseRotation(new Vec(0, 0, -1)));
        }
    }

    private PreviewPicking() { }

    private static double dot(Vec a, Vec b) { return a.x * b.x + a.y * b.y + a.z * b.z; }

    public static boolean isDrag(double startX, double startY, double x, double y) {
        return Math.hypot(x - startX, y - startY) >= DRAG_THRESHOLD;
    }

    /** Selected cubes take precedence over transparent ghosts; otherwise pick the current layer or floor. */
    public static Optional<Hit> pick(Camera camera, double mx, double my, ShapeMask mask, int layer) {
        Ray ray = camera.ray(mx, my);
        Hit best = null;
        for (Cell cell : mask.positions()) {
            Hit hit = intersect(ray, cell, true);
            if (hit != null && (best == null || hit.distance < best.distance)) best = hit;
        }
        if (best != null) return Optional.of(best);
        for (Cell cell : CustomCanvas.ghosts(mask, layer)) {
            Hit hit = intersect(ray, cell, false);
            if (hit != null && (best == null || hit.distance < best.distance)) best = hit;
        }
        if (best != null) return Optional.of(best);
        if (Math.abs(ray.direction.y) > 1e-9) {
            double t = -ray.origin.y / ray.direction.y;
            Vec p = ray.origin.plus(ray.direction.times(t));
            Cell cell = new Cell((int)Math.floor(p.x), 0, (int)Math.floor(p.z));
            if (t >= 0 && inside(mask, cell)) return Optional.of(new Hit(cell, Face.UP, false, t));
        }
        return Optional.empty();
    }

    public static Optional<Cell> addTarget(Hit hit, ShapeMask mask) {
        Cell c = hit.selected ? hit.face.neighbour(hit.cell) : hit.cell;
        return inside(mask, c) && !mask.contains(c.x(), c.y(), c.z()) ? Optional.of(c) : Optional.empty();
    }

    private static boolean inside(ShapeMask mask, Cell c) {
        return c.x() >= 0 && c.x() < mask.width() && c.y() >= 0 && c.y() < mask.height()
                && c.z() >= 0 && c.z() < mask.depth();
    }

    /** Slab intersection. Ties at edges consistently prefer x, then y, then z. */
    public static Hit intersect(Ray ray, Cell cell, boolean selected) {
        double near = Double.NEGATIVE_INFINITY, far = Double.POSITIVE_INFINITY;
        Face face = Face.FRONT;
        for (int axis = 0; axis < 3; axis++) {
            double o = axis == 0 ? ray.origin.x : axis == 1 ? ray.origin.y : ray.origin.z;
            double d = axis == 0 ? ray.direction.x : axis == 1 ? ray.direction.y : ray.direction.z;
            int min = axis == 0 ? cell.x() : axis == 1 ? cell.y() : cell.z();
            if (Math.abs(d) < 1e-9) {
                if (o < min || o >= min + 1) return null;
                continue;
            }
            double a = (min - o) / d, b = (min + 1 - o) / d;
            double entry = Math.min(a, b);
            if (entry > near) {
                near = entry;
                face = switch (axis) {
                    case 0 -> d > 0 ? Face.WEST : Face.EAST;
                    case 1 -> d > 0 ? Face.DOWN : Face.UP;
                    default -> d > 0 ? Face.FRONT : Face.BACK;
                };
            }
            far = Math.min(far, Math.max(a, b));
            if (near > far) return null;
        }
        return far < 0 ? null : new Hit(cell, face, selected, Math.max(0, near));
    }
}
