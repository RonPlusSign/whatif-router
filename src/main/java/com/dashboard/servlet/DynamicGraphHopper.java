/* WhatIfRouter
 Copyright (C) 2019 DISIT Lab http://www.disit.org - University of Florence
 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as
 published by the Free Software Foundation, either version 3 of the
 License, or (at your option) any later version.
 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.
 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package com.dashboard.servlet;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.WeightingFactory;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.BlockAreaWeighting;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.storage.GraphEdgeIdFinder;
import com.graphhopper.util.*;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.*;


public class DynamicGraphHopper extends GraphHopper {
    private GraphEdgeIdFinder.BlockArea blockArea;


    // Mapping between the edge ids and way ids
    private Map<Long, List<Integer>> wayToEdgesMap = new HashMap<>();

    // Save mapping between edge id and its way id
    // NOTE: Edge ids are incremental, starting from 0. It means I can use a simple list in order to store the way ids.
    private List<Long> edgeToWayMap = new ArrayList<>();


    public DynamicGraphHopper() {
        super();
    }

    // Override the createWeighting method of the GraphHopper class to enable BlockAreaWeighting
    @Override
    protected WeightingFactory createWeightingFactory() {

        String weighting = this.getProfiles().get(0).getWeighting();

        if ("block_area".equalsIgnoreCase(weighting)) {
            // Get encoded values for the vehicle
            EncodingManager em = this.getEncodingManager();
            BooleanEncodedValue accessEnc = em.getBooleanEncodedValue(VehicleAccess.key(this.getProfiles().get(0).getVehicle()));
            DecimalEncodedValue speedEnc = em.getDecimalEncodedValue(VehicleSpeed.key(this.getProfiles().get(0).getVehicle()));

            // Create a new WeightingFactory, with the createWeighting method that returns a BlockAreaWeighting
            return (Profile profile, PMap hints, boolean disableTurnCosts) -> new BlockAreaWeighting(new FastestWeighting(accessEnc, speedEnc), blockArea);
        }
        else {
            return super.createWeightingFactory();
        }
    }

    public void setBlockArea(GraphEdgeIdFinder.BlockArea ba) {
        blockArea = new GraphEdgeIdFinder.BlockArea(this.getBaseGraph());   // Reset blockArea
        blockArea = ba;
    }

    /**
     * Imports provided data from disc and creates graph.
     * Depending on the settings the resulting graph will be stored to disc so on a second call this method will only load the graph from disc which is usually a lot faster.
     * This will also create and save the mappings between the way and the edges that belong to it (or load them if it isn't the first execution).
     */
    @Override
    public DynamicGraphHopper importOrLoad() {
        if (!load()) {
            // If the graph cannot be loaded, then create it
            process(false);

            // Generate the mappings between the way and the edges that belong to it
            DynamicOSMReader reader = new DynamicOSMReader(GHUtility.newGraph(getBaseGraph()), getEncodingManager(), getOSMParsers(), getReaderConfig());
            reader.setFile(new File(getOSMFile()));
            try {
                reader.readGraph();
                wayToEdgesMap = reader.getWayToEdgesMap();
                edgeToWayMap = reader.getEdgeToWayMap();

                System.out.println("wayToEdgesMap: " + wayToEdgesMap.size());
                System.out.println("edgeToWayMap: " + edgeToWayMap.size());

                serializeMappings();    // Save the mappings between the way and the edges
            } catch (IOException e) {
                System.out.println("Error while reading the graph");
            }
        }
        else {
            deserializeMappings();  // Load the mappings between the way and the edges

            System.out.println("Deserialized wayToEdgesMap: " + wayToEdgesMap.size());
            System.out.println("Deserialized edgeToWayMap: " + edgeToWayMap.size());
        }

        System.out.println("Graph loaded successfully");

        return this;
    }

    /** Serialize the mappings between the way and the edges that belong to it, saving them in a file */
    private void serializeMappings() {
        // Serialize the wayToEdgesMap
        try {
            FileUtils.writeLines(new File(getGraphHopperLocation() + "/wayToEdgesMap.json"), Collections.singleton(new JSONObject(wayToEdgesMap)));
        } catch (IOException e) {
            System.out.println("Error while serializing the wayToEdgesMap");
        }

        // Serialize the edgeToWayMap
        try {
            FileUtils.writeLines(new File(getGraphHopperLocation() + "/edgeToWayMap.json"), Collections.singleton(new JSONArray(edgeToWayMap)));
        } catch (IOException e) {
            System.out.println("Error while serializing the edgeToWayMap");
        }
    }

    /** Read the mappings between the way and the edges that belong to it, saved in a file and deserialize them (recreate the wayToEdgesMap and the edgeToWayMap) */
    private void deserializeMappings() {
        // Deserialize the wayToEdgesMap
        try {
            String json = FileUtils.readFileToString(new File(getGraphHopperLocation() + "/wayToEdgesMap.json"));
            JSONObject jsonObject = new JSONObject(json);
            jsonObject.keys().forEachRemaining(keyStr -> {
                // For each way, get the list of edges that belong to it
                List<Integer> edges = new ArrayList<>();
                JSONArray jsonArray = jsonObject.getJSONArray(keyStr);
                for (int i = 0; i < jsonArray.length(); i++)
                    edges.add(jsonArray.getInt(i));

                // Add the way and the list of edges to the wayToEdgesMap
                wayToEdgesMap.put(Long.parseLong(keyStr), edges);
            });
        } catch (IOException e) {
            System.out.println("Error while deserializing the wayToEdgesMap");
        }

        // Deserialize the edgeToWayMap
        try {
            String json = FileUtils.readFileToString(new File(getGraphHopperLocation() + "/edgeToWayMap.json"));
            JSONArray jsonArray = new JSONArray(json);
            // For each edge, get the way it belongs to
            for (int i = 0; i < jsonArray.length(); i++) {
                edgeToWayMap.add(jsonArray.getLong(i));
            }
        } catch (IOException e) {
            System.out.println("Error while deserializing the edgeToWayMap");
        }
    }
}

