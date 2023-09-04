package com.dashboard.servlet;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.OSMReader;
import com.graphhopper.routing.OSMReaderConfig;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.OSMParsers;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.PointList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DynamicOSMReader extends OSMReader {

    // Save mapping between the edge ids and way ids
    private final Map<Long, List<Integer>> wayToEdgesMap = new HashMap<>();

    // Save mapping between edge id and its way id
    // NOTE: Edge ids are incremental, starting from 0. It means I can use a simple list in order to store the way ids.
    private final List<Long> edgeToWayMap = new ArrayList<>();

    private int nextEdgeId = 0;


    public DynamicOSMReader(BaseGraph baseGraph, EncodingManager encodingManager, OSMParsers osmParsers, OSMReaderConfig config) {
        super(baseGraph, encodingManager, osmParsers, config);
    }

    /**
     * Override the method that adds an edge to the graph, so that I can save the mapping between the way and the edges that belong to it
     *
     * @param fromIndex a unique integer id for the first node of this segment
     * @param toIndex   a unique integer id for the last node of this segment
     * @param pointList coordinates of this segment
     * @param way       the OSM way this segment was taken from
     * @param nodeTags  node tags of this segment if it is an artificial edge, empty otherwise
     */
    @Override
    protected void addEdge(int fromIndex, int toIndex, PointList pointList, ReaderWay way, Map<String, Object> nodeTags) {
        super.addEdge(fromIndex, toIndex, pointList, way, nodeTags);

        // NOTE: This method might be called multiple times for each way.
        // Every time this method is called, it means that a new edge is being added to the graph, with an increasing edgeId.
        // This means that I can count the edges that have already been added to the graph, so that I know the next edge id.
        // I can also save the mapping between the way and the edges that belong to it.

        // Update the mapping between the way and the edges that belong to it
        if (!wayToEdgesMap.containsKey(way.getId())) wayToEdgesMap.put(way.getId(), new ArrayList<>()); // Init the list of edges that belong to this way
        wayToEdgesMap.get(way.getId()).add(nextEdgeId);

        // Update the mapping between the edge and the ways that it belongs to
        edgeToWayMap.add(way.getId());

        // Update the next edge id
        nextEdgeId++;
    }

    /**
     * Get the edge ids that belong to a way
     *
     * @param wayId the id of the way
     *
     * @return the list of edge ids that belong to the way
     */
    public List<Integer> getEdgesFromWay(long wayId) {
        return wayToEdgesMap.get(wayId);
    }

    /**
     * Get the way ids in which an edge belongs
     *
     * @param edgeId the id of the edge
     *
     * @return the way id in which the edge belongs
     */
    public Long getWayFromEdge(int edgeId) {
        return edgeToWayMap.get(edgeId);
    }

    public Map<Long, List<Integer>> getWayToEdgesMap() {
        return wayToEdgesMap;
    }

    public List<Long> getEdgeToWayMap() {
        return edgeToWayMap;
    }
}
