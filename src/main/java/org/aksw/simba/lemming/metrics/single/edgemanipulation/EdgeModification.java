package org.aksw.simba.lemming.metrics.single.edgemanipulation;

import com.carrotsearch.hppc.BitSet;
import com.carrotsearch.hppc.cursors.IntCursor;

import grph.Grph;

import org.aksw.simba.lemming.ColouredGraph;
import org.aksw.simba.lemming.metrics.single.SingleValueMetric;
import org.aksw.simba.lemming.metrics.single.edgetriangles.EdgeTriangleMetric;
import org.aksw.simba.lemming.metrics.single.nodetriangles.NodeTriangleMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import toools.set.IntSet;
import toools.set.IntSets;

/**
 * @author DANISH AHMED on 8/10/2018
 */
public class EdgeModification {
    private ColouredGraph graph;
    private SingleValueMetric nodeMetric;
    private SingleValueMetric edgeMetric;
    private int oldNodeTriangles = 0;
    private int newNodeTriangles;
    private int oldEdgeTriangles = 0;
    private int newEdgeTriangles;
    private int subGraphTrianglesAfterRemovingEdge = 0;

    private static final Logger LOGGER = LoggerFactory.getLogger(EdgeModification.class);

    public EdgeModification(ColouredGraph graph, SingleValueMetric nodeMetric, SingleValueMetric edgeMetric) {
        this.graph = graph;
        this.nodeMetric = nodeMetric;
        this.edgeMetric = edgeMetric;

        this.oldNodeTriangles = (int) getNumberOfNodeTriangles();
        this.oldEdgeTriangles = (int) getNumberOfEdgeTriangles();
    }

    public EdgeModification(ColouredGraph graph, int numberOfNodeTriangles, int numberOfEdgeTriangles) {
        this.graph = graph;

        this.oldNodeTriangles = numberOfNodeTriangles;
        this.oldEdgeTriangles = numberOfEdgeTriangles;
    }

    public ColouredGraph getGraph() {
        return graph;
    }

    public int getOldNodeTriangles() {
        return oldNodeTriangles;
    }

    public int getOldEdgeTriangles() {
        return oldEdgeTriangles;
    }

    public int getNewNodeTriangles() {
        return newNodeTriangles;
    }

    public int getNewEdgeTriangles() {
        return newEdgeTriangles;
    }

    public void setNodeMetric(SingleValueMetric nodeMetric) {
        this.nodeMetric = nodeMetric;
    }

    public void setEdgeMetric(SingleValueMetric edgeMetric) {
        this.edgeMetric = edgeMetric;
    }

    public void setGraph(ColouredGraph graph) {
        this.graph = graph;
    }

    private double getNumberOfNodeTriangles() {
        return nodeMetric.apply(graph);
    }

    private double getNumberOfEdgeTriangles() {
        return edgeMetric.apply(graph);
    }

    void removeEdgeFromGraph(int edgeId) {
        if (newNodeTriangles != 0 && newEdgeTriangles != 0) {
            oldNodeTriangles = newNodeTriangles;
            oldEdgeTriangles = newEdgeTriangles;
        }

//        if (oldNodeTriangles == 0)
//            oldNodeTriangles = (int) getNumberOfNodeTriangles();
//        if (oldEdgeTriangles == 0)
//            oldEdgeTriangles = (int) getNumberOfEdgeTriangles();
//        newNodeTriangles = 0;
//        newEdgeTriangles = 0;

        Grph grph = graph.getGraph();
        IntSet verticesConnectedToRemovingEdge = grph.getVerticesIncidentToEdge(edgeId);
        
        int headId = verticesConnectedToRemovingEdge.size() > 1 ? verticesConnectedToRemovingEdge.toIntArray()[1] 
        		: verticesConnectedToRemovingEdge.toIntArray()[0];
        int tailId = verticesConnectedToRemovingEdge.toIntArray()[0];
        
        int numEdgesBetweenConnectedVertices = IntSets.intersection(grph.getEdgesIncidentTo(tailId),
                grph.getEdgesIncidentTo(headId)).size();

        //LOGGER.info(String.format("Removed edge id:\t%s", edgeId));
        if (numEdgesBetweenConnectedVertices > 1) {
            /* Same number of node triangles */
            newNodeTriangles = oldNodeTriangles;
        } else {
            /* edge size = 1 */
            /* remove number of triangles formed by subgraph*/
            int oldSubGraphNodeTriangles = calculateSubGraphNodeTriangles(edgeId);
            newNodeTriangles = oldNodeTriangles - oldSubGraphNodeTriangles;
            newNodeTriangles = newNodeTriangles >= 0 ? newNodeTriangles: 0;
        }

        int oldSubGraphEdgeTriangles = calculateSubGraphEdgeTriangles(edgeId);
        if (subGraphTrianglesAfterRemovingEdge == 0){
            newEdgeTriangles = oldEdgeTriangles - oldSubGraphEdgeTriangles;
        	newEdgeTriangles = newEdgeTriangles >= 0 ? newEdgeTriangles: 0;
        }
        else {
            int difference = oldSubGraphEdgeTriangles - subGraphTrianglesAfterRemovingEdge;
            newEdgeTriangles = oldEdgeTriangles - difference;
            newEdgeTriangles = newEdgeTriangles >= 0 ? newEdgeTriangles: 0;
        }
        subGraphTrianglesAfterRemovingEdge = 0;

        this.graph.removeEdge(edgeId);
    }

    /* Get number of triangles that were formed by utilizing this edge
     * you need a sub graph of the vertices that are in common with edge's vertices */
    private int calculateSubGraphNodeTriangles(int edgeId) {
        Grph grph = graph.getGraph();
        IntSet verticesConnectedToRemovingEdge = grph.getVerticesIncidentToEdge(edgeId);

        int headId = verticesConnectedToRemovingEdge.size() > 1 ? verticesConnectedToRemovingEdge.toIntArray()[1] 
        		: verticesConnectedToRemovingEdge.toIntArray()[0];
        int tailId = verticesConnectedToRemovingEdge.toIntArray()[0];
        
        return getVerticesInCommon(tailId, headId).size();
    }

    private IntSet getVerticesInCommon(int v1, int v2) {
        Grph grph = graph.getGraph();
        IntSet[] neighborsOfConnectedVertices = new IntSet[2];

        neighborsOfConnectedVertices[0] = grph.getInNeighbors(v1);
        neighborsOfConnectedVertices[0].addAll(grph.getOutNeighbors(v1));

        if (neighborsOfConnectedVertices[0].contains(v1))
            neighborsOfConnectedVertices[0].remove(v1);
        if (neighborsOfConnectedVertices[0].contains(v2))
            neighborsOfConnectedVertices[0].remove(v2);

        neighborsOfConnectedVertices[1] = grph.getInNeighbors(v2);
        neighborsOfConnectedVertices[1].addAll(grph.getOutNeighbors(v2));

        if (neighborsOfConnectedVertices[1].contains(v1))
            neighborsOfConnectedVertices[1].remove(v1);
        if (neighborsOfConnectedVertices[1].contains(v2))
            neighborsOfConnectedVertices[1].remove(v2);

        return IntSets.intersection(neighborsOfConnectedVertices[0], neighborsOfConnectedVertices[1]);
    }

    public int calculateSubGraphEdgeTriangles(int edgeId) {
        int oldSubGraphEdgeTriangles = 0;

        Grph grph = graph.getGraph();
        IntSet verticesConnectedToRemovingEdge = grph.getVerticesIncidentToEdge(edgeId);
        
        int headId = verticesConnectedToRemovingEdge.size() > 1 ? verticesConnectedToRemovingEdge.toIntArray()[1] 
        		: verticesConnectedToRemovingEdge.toIntArray()[0];
        int tailId = verticesConnectedToRemovingEdge.toIntArray()[0];
        
        int numEdgesBetweenConnectedVertices = IntSets.intersection(grph.getEdgesIncidentTo(tailId),
                grph.getEdgesIncidentTo(headId)).size();
        IntSet verticesFormingTriangle = getVerticesInCommon(tailId, headId);

        for (IntCursor vertex : verticesFormingTriangle) {
            int numEdgesV1ToTriangleVertex = IntSets.intersection(grph.getEdgesIncidentTo(tailId),
            		grph.getEdgesIncidentTo(vertex.value)).size();
            int numEdgesV2ToTriangleVertex = IntSets.intersection(grph.getEdgesIncidentTo(headId),
                    grph.getEdgesIncidentTo(vertex.value)).size();
            int mul = numEdgesV1ToTriangleVertex * numEdgesV2ToTriangleVertex;
            oldSubGraphEdgeTriangles += (mul * numEdgesBetweenConnectedVertices);
            subGraphTrianglesAfterRemovingEdge += (mul * (numEdgesBetweenConnectedVertices - 1));
        }

        return oldSubGraphEdgeTriangles;
    }

    int addEdgeToGraph(int tail, int head, BitSet color) {
        if (newNodeTriangles != 0 && newEdgeTriangles != 0) {
            oldNodeTriangles = newNodeTriangles;
            oldEdgeTriangles = newEdgeTriangles;
        }

//        if (oldNodeTriangles == 0)
//            oldNodeTriangles = (int) getNumberOfNodeTriangles();
//        if (oldEdgeTriangles == 0)
//            oldEdgeTriangles = (int) getNumberOfEdgeTriangles();

        Grph grph = graph.getGraph();
        int numEdgesBetweenVertices = IntSets.intersection(grph.getEdgesIncidentTo(tail),
                grph.getEdgesIncidentTo(head)).size();

        IntSet verticesInCommon = getVerticesInCommon(tail, head);
        int edgeId = 0;

        if (numEdgesBetweenVertices > 0) {
            // number of Node Triangles remains same
            this.newNodeTriangles = oldNodeTriangles;

            int oldSubGraphEdgeTriangles = 0;
            int newSubGraphEdgeTriangles = 0;
            for (IntCursor vertex : verticesInCommon) {
                int numEdgesV1ToTriangleVertex = IntSets.intersection(grph.getEdgesIncidentTo(tail),
                        grph.getEdgesIncidentTo(vertex.value)).size();
                int numEdgesV2ToTriangleVertex = IntSets.intersection(grph.getEdgesIncidentTo(head),
                        grph.getEdgesIncidentTo(vertex.value)).size();
                int mul = numEdgesV1ToTriangleVertex * numEdgesV2ToTriangleVertex;

                oldSubGraphEdgeTriangles += (mul * numEdgesBetweenVertices);
                newSubGraphEdgeTriangles += (mul * (numEdgesBetweenVertices + 1));
            }
            this.newEdgeTriangles = oldEdgeTriangles + (newSubGraphEdgeTriangles - oldSubGraphEdgeTriangles);
            edgeId = graph.addEdge(tail, head, color);
        } else {
            // no connection between vertices
            this.newNodeTriangles = oldNodeTriangles + verticesInCommon.size();
            edgeId = graph.addEdge(tail, head, color);
            numEdgesBetweenVertices += 1;
            int subGraphEdgeTriangles = 0;
            for (IntCursor vertex : verticesInCommon) {
                int numEdgesV1ToTriangleVertex = IntSets.intersection(grph.getEdgesIncidentTo(tail),
                        grph.getEdgesIncidentTo(vertex.value)).size();
                int numEdgesV2ToTriangleVertex = IntSets.intersection(grph.getEdgesIncidentTo(head),
                        grph.getEdgesIncidentTo(vertex.value)).size();
                int mul = numEdgesV1ToTriangleVertex * numEdgesV2ToTriangleVertex;

                subGraphEdgeTriangles += (mul * numEdgesBetweenVertices);
            }
            this.newEdgeTriangles = oldEdgeTriangles + subGraphEdgeTriangles;
        }
        return edgeId;
    }
    
    public void removeEdgeFromGraph(int edgeId, int newNodeTriangles, int newEdgeTriangles) {
        if (this.newNodeTriangles != 0 && this.newEdgeTriangles != 0) {
            oldNodeTriangles = this.newNodeTriangles;
            oldEdgeTriangles = this.newEdgeTriangles;
        }
        this.newNodeTriangles = newNodeTriangles;
        this.newEdgeTriangles = newEdgeTriangles;
        this.graph.removeEdge(edgeId);
    }

   public int addEdgeToGraph(int tail, int head, BitSet color, int newNodeTriangles, int newEdgeTriangles) {
        if (this.newNodeTriangles != 0 && this.newEdgeTriangles != 0) {
            oldNodeTriangles = this.newNodeTriangles;
            oldEdgeTriangles = this.newEdgeTriangles;
        }
        this.newNodeTriangles = newNodeTriangles;
        this.newEdgeTriangles = newEdgeTriangles;
        return graph.addEdge(tail, head, color);
    }
}

