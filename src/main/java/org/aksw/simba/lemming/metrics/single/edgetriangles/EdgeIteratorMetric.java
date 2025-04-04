package org.aksw.simba.lemming.metrics.single.edgetriangles;

import com.carrotsearch.hppc.cursors.IntCursor;
import grph.Grph;
import org.aksw.simba.lemming.ColouredGraph;
import org.aksw.simba.lemming.metrics.AbstractMetric;
import org.aksw.simba.lemming.metrics.single.TriangleMetric;
import toools.set.IntSet;
import toools.set.IntSets;

import java.util.*;

/**
 * @author DANISH AHMED on 7/3/2018
 */
public class EdgeIteratorMetric extends AbstractMetric implements TriangleMetric {

    public EdgeIteratorMetric() {
        super("#edgetriangles");
    }

    @Override
    public double apply(ColouredGraph graph) {
        return countTriangles(graph);
    }

    protected double countTriangles(ColouredGraph graph) {
        HashSet<org.aksw.simba.lemming.metrics.single.nodetriangles.EdgeIteratorMetric.Triangle> visitedV = new HashSet<>();
        IntSet[] edges = new IntSet[graph.getGraph().getNumberOfEdges()];
        IntSet[] vertexEdges = new IntSet[graph.getGraph().getNumberOfVertices()];
        IntSet[] vertexNeighbors = new IntSet[graph.getGraph().getNumberOfVertices()];

        int triangleCount = 0;
        Grph grph = graph.getGraph();

        for (int i = 0; i < edges.length; ++i) {
            edges[i] = grph.getVerticesIncidentToEdge(i);
        }

        for (int i = 0; i < vertexNeighbors.length; i++) {
            vertexNeighbors[i] = grph.getInNeighbors(i);
            vertexNeighbors[i].addAll(grph.getOutNeighbors(i));

            vertexEdges[i] = grph.getOutEdges(i);
            vertexEdges[i].addAll(grph.getInEdges(i));
        }

        org.aksw.simba.lemming.metrics.single.nodetriangles.EdgeIteratorMetric.Triangle temp = new org.aksw.simba.lemming.metrics.single.nodetriangles.EdgeIteratorMetric.Triangle(0, 0, 0);
        for (int i = 0; i < edges.length; i++) {
            int[] verticesConnectedToEdge = edges[i].toIntArray();

            // An edge will always have only two vertices
            // In case of loop edge, it will have only 1 vertex
            if (verticesConnectedToEdge.length == 2) {
                IntSet verticesInCommon = IntSets.intersection(
                        vertexNeighbors[verticesConnectedToEdge[0]],
                        vertexNeighbors[verticesConnectedToEdge[1]]);

                for (IntCursor vertex : verticesInCommon) {
                    if (vertex.value == verticesConnectedToEdge[0]
                            || vertex.value == verticesConnectedToEdge[1])
                        continue;
                    temp.set(vertex.value, verticesConnectedToEdge[0], verticesConnectedToEdge[1]);

                    if (visitedV.contains(temp))
                        continue;

                    try {
                        visitedV.add((org.aksw.simba.lemming.metrics.single.nodetriangles.EdgeIteratorMetric.Triangle) temp.clone());
                    } catch (CloneNotSupportedException e) {
                        e.printStackTrace();
                    }
                    triangleCount += IntSets.intersection(vertexEdges[verticesConnectedToEdge[0]], vertexEdges[verticesConnectedToEdge[1]]).size() *
                            IntSets.intersection(vertexEdges[verticesConnectedToEdge[1]], vertexEdges[vertex.value]).size() *
                            IntSets.intersection(vertexEdges[vertex.value], vertexEdges[verticesConnectedToEdge[0]]).size();
                    }
            }
        }
        return triangleCount;
    }

    @Override
    public double calculateComplexity(int edges, int vertices) {
        return (Math.pow(edges, 4) / Math.pow(vertices, 4));
    }
}
