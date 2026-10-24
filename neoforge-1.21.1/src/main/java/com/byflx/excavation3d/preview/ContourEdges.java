package com.byflx.excavation3d.preview;

import com.byflx.excavation3d.shape.Offset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The outer contour of a set of unit cells as unit line segments: only the edges where the surface
 * bends, never an edge that lies inside a flat face. An edge is part of the contour when 1 or 3 of
 * the 4 cells around it are occupied, or 2 cells that touch only along that edge (diagonal).
 */
public final class ContourEdges {
    public enum Axis {
        X,
        Y,
        Z
    }

    /** The unit segment from (x, y, z) to one block further along {@code axis}, in block corners. */
    public record Edge(int x, int y, int z, Axis axis) {
    }

    private ContourEdges() {
    }

    public static List<Edge> of(Collection<Offset> cells) {
        Set<Offset> occupied = new HashSet<>(cells);
        Set<Edge> candidates = new LinkedHashSet<>();
        for (Offset c : occupied) {
            for (int a = 0; a <= 1; a++) {
                for (int b = 0; b <= 1; b++) {
                    candidates.add(new Edge(c.x(), c.y() + a, c.z() + b, Axis.X));
                    candidates.add(new Edge(c.x() + a, c.y(), c.z() + b, Axis.Y));
                    candidates.add(new Edge(c.x() + a, c.y() + b, c.z(), Axis.Z));
                }
            }
        }
        List<Edge> contour = new ArrayList<>();
        for (Edge edge : candidates) {
            if (isContour(occupied, edge)) {
                contour.add(edge);
            }
        }
        return contour;
    }

    private static boolean isContour(Set<Offset> occupied, Edge e) {
        // The four cells around the edge, as quadrants (-u -v), (+u -v), (-u +v), (+u +v).
        boolean q00 = around(occupied, e, -1, -1);
        boolean q10 = around(occupied, e, 0, -1);
        boolean q01 = around(occupied, e, -1, 0);
        boolean q11 = around(occupied, e, 0, 0);
        int count = (q00 ? 1 : 0) + (q10 ? 1 : 0) + (q01 ? 1 : 0) + (q11 ? 1 : 0);
        if (count == 1 || count == 3) {
            return true;
        }
        return count == 2 && q00 == q11;
    }

    private static boolean around(Set<Offset> occupied, Edge e, int du, int dv) {
        return switch (e.axis()) {
            case X -> occupied.contains(new Offset(e.x(), e.y() + du, e.z() + dv));
            case Y -> occupied.contains(new Offset(e.x() + du, e.y(), e.z() + dv));
            case Z -> occupied.contains(new Offset(e.x() + du, e.y() + dv, e.z()));
        };
    }
}
