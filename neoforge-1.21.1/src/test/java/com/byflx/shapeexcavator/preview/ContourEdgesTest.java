package com.byflx.shapeexcavator.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.byflx.shapeexcavator.preview.ContourEdges.Axis;
import com.byflx.shapeexcavator.preview.ContourEdges.Edge;
import com.byflx.shapeexcavator.shape.Offset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ContourEdgesTest {
    private static List<Offset> box(int w, int h, int d) {
        List<Offset> cells = new ArrayList<>();
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                for (int z = 0; z < d; z++) {
                    cells.add(new Offset(x, y, z));
                }
            }
        }
        return cells;
    }

    @Test
    void singleBlockHasTwelveEdges() {
        assertEquals(12, ContourEdges.of(List.of(Offset.ZERO)).size());
    }

    @Test
    void cuboidHasOnlyItsBoxEdges() {
        // 4 · (W + H + D) unit segments for 3 × 3 × 1, none inside a flat face.
        List<Edge> edges = ContourEdges.of(box(3, 3, 1));
        assertEquals(28, edges.size());
        assertEquals(edges.size(), new HashSet<>(edges).size());
        for (Edge e : edges) {
            boolean onBoxEdge = switch (e.axis()) {
                case X -> (e.y() == 0 || e.y() == 3) && (e.z() == 0 || e.z() == 1);
                case Y -> (e.x() == 0 || e.x() == 3) && (e.z() == 0 || e.z() == 1);
                case Z -> (e.x() == 0 || e.x() == 3) && (e.y() == 0 || e.y() == 3);
            };
            assertTrue(onBoxEdge, e.toString());
        }
        assertEquals(4 * (5 + 4 + 2), ContourEdges.of(box(5, 4, 2)).size());
    }

    @Test
    void plusHasNoEdgeInsideTheCentreFaceButKeepsItsInnerCorners() {
        Set<Edge> edges = new HashSet<>(ContourEdges.of(List.of(new Offset(1, 0, 0), new Offset(0, 1, 0),
                new Offset(1, 1, 0), new Offset(2, 1, 0), new Offset(1, 2, 0))));
        // Lower and upper front edges of the centre cell lie inside the flat front face.
        assertFalse(edges.contains(new Edge(1, 1, 0, Axis.X)));
        assertFalse(edges.contains(new Edge(1, 2, 0, Axis.X)));
        assertFalse(edges.contains(new Edge(1, 1, 0, Axis.Y)));
        assertFalse(edges.contains(new Edge(2, 1, 0, Axis.Y)));
        // The four concave corners run along the depth.
        assertTrue(edges.contains(new Edge(1, 1, 0, Axis.Z)));
        assertTrue(edges.contains(new Edge(2, 1, 0, Axis.Z)));
        assertTrue(edges.contains(new Edge(1, 2, 0, Axis.Z)));
        assertTrue(edges.contains(new Edge(2, 2, 0, Axis.Z)));
    }

    @Test
    void lShapeIncludesTheConcaveEdge() {
        Set<Edge> edges = new HashSet<>(ContourEdges.of(List.of(Offset.ZERO, new Offset(1, 0, 0),
                new Offset(0, 1, 0))));
        assertTrue(edges.contains(new Edge(1, 1, 0, Axis.Z)));
        // The edge between the two lower cells lies inside the flat bottom and front faces.
        assertFalse(edges.contains(new Edge(1, 0, 0, Axis.Y)));
    }

    @Test
    void diagonalNeighboursKeepTheSharedEdge() {
        List<Edge> edges = ContourEdges.of(List.of(Offset.ZERO, new Offset(1, 1, 0)));
        assertTrue(edges.contains(new Edge(1, 1, 0, Axis.Z)));
        assertEquals(23, edges.size());
    }
}
