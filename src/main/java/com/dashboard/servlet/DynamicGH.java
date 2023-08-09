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
import com.graphhopper.routing.ev.DecimalEncodedValueImpl;
import com.graphhopper.routing.ev.SimpleBooleanEncodedValue;
import com.graphhopper.routing.weighting.BlockAreaWeighting;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.GraphEdgeIdFinder;
import com.graphhopper.util.PMap;


public class DynamicGH extends GraphHopper {
    private final PMap hintsMap;
    private GraphEdgeIdFinder.BlockArea blockArea;

    public DynamicGH(PMap hintsMap) {
        super();
        this.hintsMap = hintsMap;
    }

    // Override the createWeighting method of the GraphHopper class to enable BlockAreaWeighting
    @Override
    protected WeightingFactory createWeightingFactory() {
        System.out.println("Create weighting factory: " + this.hintsMap.getString("weighting", ""));
        String weighting = hintsMap.getString("weighting", "");
        if ("block_area".equalsIgnoreCase(weighting)) {
            Weighting w = new FastestWeighting(new SimpleBooleanEncodedValue(""), new DecimalEncodedValueImpl("", 0, 0, false));
            return new WeightingFactory() {
                @Override
                public Weighting createWeighting(Profile profile, PMap pMap, boolean b) {
                    return new BlockAreaWeighting(w, blockArea);
                }
            };
        } else {
            return super.createWeightingFactory();
        }
    }

    public void setBlockArea(GraphEdgeIdFinder.BlockArea ba) {
        blockArea = new GraphEdgeIdFinder.BlockArea(this.getBaseGraph());
        blockArea = ba;
    }
}