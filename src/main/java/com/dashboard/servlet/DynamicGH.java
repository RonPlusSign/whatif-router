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
import com.graphhopper.routing.WeightingFactory;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.BlockAreaWeighting;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.storage.GraphEdgeIdFinder;


public class DynamicGH extends GraphHopper {
    private GraphEdgeIdFinder.BlockArea blockArea;

    public DynamicGH() {
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
            return (profile, pMap, b) -> new BlockAreaWeighting(new FastestWeighting(accessEnc, speedEnc), blockArea);

        } else {
            return super.createWeightingFactory();
        }
    }

    public void setBlockArea(GraphEdgeIdFinder.BlockArea ba) {
        blockArea = new GraphEdgeIdFinder.BlockArea(this.getBaseGraph());   // Reset blockArea
        blockArea = ba;
    }
}

