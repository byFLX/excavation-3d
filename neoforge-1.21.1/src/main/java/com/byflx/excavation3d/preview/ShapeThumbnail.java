package com.byflx.excavation3d.preview;

import com.byflx.excavation3d.shape.Cell;
import com.byflx.excavation3d.shape.ShapeMask;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Cached flat-shaded isometric surface quads, normalized to a unit circle for any GUI radius. */
public record ShapeThumbnail(List<Face> faces) {
    public record Point(double x, double y) { }
    public record Face(List<Point> points, int rgb) { }
    private record Projected(List<PreviewPicking.Vec> points, int rgb) {
        double depth() { return points.stream().mapToDouble(PreviewPicking.Vec::z).average().orElse(0); }
    }

    public static ShapeThumbnail of(ShapeMask mask) {
        var camera = new PreviewPicking.Camera(0, 0, 1, 45, 35.26438968,
                mask.width(), mask.height(), mask.depth());
        List<Projected> projected = new ArrayList<>();
        for (Cell c : mask.positions()) {
            int x = c.x(), y = c.y(), z = c.z();
            if (!mask.contains(x - 1, y, z)) add(projected, camera, 0x4984B6,
                    x,y,z, x,y,z+1, x,y+1,z+1, x,y+1,z);
            if (!mask.contains(x, y + 1, z)) add(projected, camera, 0x78BBEF,
                    x,y+1,z, x,y+1,z+1, x+1,y+1,z+1, x+1,y+1,z);
            if (!mask.contains(x, y, z - 1)) add(projected, camera, 0x5DA9E9,
                    x,y,z, x,y+1,z, x+1,y+1,z, x+1,y,z);
        }
        if (projected.isEmpty()) return new ShapeThumbnail(List.of());
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (Projected face : projected) for (var p : face.points()) {
            minX = Math.min(minX, p.x()); maxX = Math.max(maxX, p.x());
            minY = Math.min(minY, p.y()); maxY = Math.max(maxY, p.y());
        }
        double cx = (minX + maxX) / 2, cy = (minY + maxY) / 2;
        double radius = 0;
        for (Projected face : projected) for (var p : face.points()) {
            radius = Math.max(radius, Math.hypot(p.x() - cx, p.y() - cy));
        }
        double fit = radius > 0 ? 1 / radius : 1;
        projected.sort(Comparator.comparingDouble(Projected::depth));
        return new ShapeThumbnail(projected.stream().map(f -> new Face(f.points().stream()
                .map(p -> new Point((p.x() - cx) * fit, (p.y() - cy) * fit)).toList(), f.rgb())).toList());
    }

    private static void add(List<Projected> faces, PreviewPicking.Camera camera, int rgb, int... xyz) {
        List<PreviewPicking.Vec> points = new ArrayList<>(4);
        for (int i = 0; i < xyz.length; i += 3) {
            points.add(camera.project(new PreviewPicking.Vec(xyz[i], xyz[i + 1], xyz[i + 2])));
        }
        faces.add(new Projected(List.copyOf(points), rgb));
    }
}
