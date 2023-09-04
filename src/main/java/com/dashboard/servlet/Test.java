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

import com.graphhopper.*;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GraphEdgeIdFinder;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.Circle;
import com.graphhopper.util.shapes.GHPoint;
import com.graphhopper.util.shapes.Polygon;

import com.google.gson.Gson;
import org.apache.http.HttpEntity;
import org.json.JSONArray;
import org.json.JSONObject;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.HttpResponse;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;

@Path("/route")
public class Test {
    static String _vehicle = "car";
    final static String _algorithm = Parameters.Algorithms.DIJKSTRA_BI;

    /**
     * API interface method called by Dashboard
     *
     * @param avoidArea FeatureCollection object (in GeoJSON format) containing the areas to avoid in routing calculation
     * @param waypoints Routing lat/lng waypoints separated by ';'
     *
     * @return the Response object expected from GraphHopper Leaflet Routing Machine
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public static Response getRoute(@DefaultValue("car") @QueryParam("vehicle") String vehicle,
                                    @DefaultValue("") @QueryParam("avoid_area") String avoidArea,
                                    @DefaultValue("") @QueryParam("waypoints") String waypoints) {
        _vehicle = vehicle;

        // 1: init GH
        DynamicGH hopper = initGH(_vehicle);

        // 2. If there's an avoidArea, set the weighting to block_area and apply the avoidArea
        if (avoidArea != null && !avoidArea.isEmpty()) {
            hopper.getProfiles().get(0).setWeighting("block_area");

            // extract barriers and apply them
            blockAreaSetup(hopper, avoidArea);
        }

        // TODO: Handle traffic and air quality data

        // 3: extract waypoints
        String[] waypointsArray = waypoints.split(";");

        // 4: perform blocked routing
        GHResponse response = blockedRoute(hopper, waypointsArray);

        // 5: build response
        JSONObject jsonResponse = buildFormattedResponse(hopper, response);
        return Response.ok(jsonResponse.toString()).header("Access-Control-Allow-Origin", "*").build();
    }


    public static void main(String[] args) {
        // Uncomment the following lines to test the routing methods
//        getRoute("bike",
//                "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{\"radius\":148.31828400014956},\"geometry\":{\"type\":\"Point\",\"coordinates\":[43.777663,11.268089]}}],\"scenarioName\":\"gia pan - sce01\",\"isPublic\":false}",
//                "43.783860157932395,11.261587142944338;43.76582535876258,11.271286010742188"
//        );
    }

    public static DynamicGH initGH(String _vehicle) {
        // Create EncodingManager for the selected vehicle (car, foot, bike)
        final EncodingManager vehicleManager = EncodingManager.create(_vehicle);

        // create one GraphHopper instance
        DynamicGH hopper = new DynamicGH();
        hopper.setOSMFile("toscana.osm.pbf");
        hopper.setGraphHopperLocation("toscana_map-gh");
        // hopper.clean();
        hopper.setProfiles(new Profile(_vehicle).setVehicle(_vehicle));

        // now this can take minutes if it imports or a few seconds for loading (of course this is dependent on the area you import)
        hopper.importOrLoad();
        return hopper;
    }

    public static void blockAreaSetup(DynamicGH hopper, String avoidArea) {

        JSONObject jsonData = new JSONObject(avoidArea);
        GraphEdgeIdFinder.BlockArea blockArea = new GraphEdgeIdFinder.BlockArea(hopper.getBaseGraph());

        JSONArray features = jsonData.getJSONArray("features");
        for (int i = 0; i < features.length(); i++) {
            JSONObject feature = features.getJSONObject(i);

            JSONArray coords = feature.getJSONObject("geometry").getJSONArray("coordinates");
            String type = feature.getJSONObject("geometry").getString("type");

            if (type.equals("Point")) {
                blockArea.add(new Circle(coords.getDouble(0), coords.getDouble(1), 1));
            }
            if (type.equals("Polygon")) {
                double[] lats = new double[coords.getJSONArray(0).length()];
                double[] lons = new double[coords.getJSONArray(0).length()];
                for (i = 0; i < coords.getJSONArray(0).length(); i++) {
                    lats[i] = coords.getJSONArray(0).getJSONArray(i).getDouble(0);
                    lons[i] = coords.getJSONArray(0).getJSONArray(i).getDouble(1);
                }
                blockArea.add(new Polygon(lats, lons));
            }
            if (type.equals("Point") && feature.getJSONObject("properties").has("radius")) {      // circle
                double radius = feature.getJSONObject("properties").getDouble("radius");
                blockArea.add(new Circle(coords.getDouble(0), coords.getDouble(1), radius));
            }
        }
        hopper.setBlockArea(blockArea);
    }

    // build response json as required by leaflet routing machine
    public static JSONObject buildFormattedResponse(DynamicGH hopper, GHResponse response) {
        JSONObject jsonRsp = new JSONObject();

        // Use all paths
        List<ResponsePath> allPathsList = response.getAll();

        System.out.println("All paths: " + allPathsList.size());

        // paths
        JSONArray pathArray = new JSONArray();
        for (ResponsePath path : allPathsList) {
            // get path geometry information (latitude, longitude and optionally elevation)
            PointList pointList = path.getPoints();
            // get information per turn instruction
            InstructionList instructions = path.getInstructions();

            // get time(milliseconds) and distance(meters) of the path
            double distance = path.getDistance();
            long millis = path.getTime();

            JSONObject jsonPath = new JSONObject();

            // bbox
            BBox box = hopper.getBaseGraph().getBounds();
            String bboxString = "[" + box.minLon + "," + box.minLat + "," + box.maxLon + "," + box.maxLat + "]";
            JSONArray bbox = new JSONArray(bboxString);
            jsonPath.put("bbox", bbox);

            // points
            String encPoints = encodePolyline(pointList, false);
            jsonPath.put("points", encPoints);

            // points_encoded
            jsonPath.put("points_encoded", true);

            // time, distance
            jsonPath.put("distance", distance);
            jsonPath.put("time", millis);

            // instructions
            jsonPath.put("instructions", new JSONArray(serializeInstructions(instructions)));

            pathArray.put(jsonPath);   // Add the path to the array of paths
        }

        jsonRsp.put("paths", pathArray);

        // --info
        JSONObject info = new JSONObject();
        JSONArray copyrights = new JSONArray();
        copyrights.put("GraphHopper");
        copyrights.put("OpenStreetMap contributors");
        info.put("copyrights", copyrights);

        jsonRsp.put("info", info);

        return jsonRsp;
    }

    /**
     * Perform a simple route calculation and print the best path details
     *
     * @param hopper  GraphHopper instance
     * @param latFrom Start latitude
     * @param lonFrom Start longitude
     * @param latTo   End latitude
     * @param lonTo   End longitude
     */
    public static void simpleRoute(GraphHopper hopper, double latFrom, double lonFrom, double latTo, double lonTo) {
        System.out.println("Simple route...");

        GHRequest req = new GHRequest(latFrom, lonFrom, latTo, lonTo)
                .setProfile(_vehicle)
                .setLocale(Locale.ENGLISH)
                .setAlgorithm(_algorithm);

        GHResponse rsp = hopper.route(req);

        printResponseDetails(rsp);
    }

    /**
     * Perform a simple route calculation with alternatives and print all paths
     *
     * @param hopper  GraphHopper instance
     * @param latFrom Start latitude
     * @param lonFrom Start longitude
     * @param latTo   End latitude
     * @param lonTo   End longitude
     */
    public static void simpleRouteAlt(GraphHopper hopper, double latFrom, double lonFrom, double latTo, double lonTo) {
        System.out.println("Simple route with alternatives...");

        GHRequest req = new GHRequest(latFrom, lonFrom, latTo, lonTo)
                .setProfile(_vehicle)
                .setLocale(Locale.ENGLISH)
                .setAlgorithm(_algorithm);

        GHResponse rsp = hopper.route(req);

        printAlternativeDetails(rsp);
    }

    /**
     * Perform a blocked route calculation and print the best path details
     *
     * @param hopper         GraphHopper instance with the blockArea set
     * @param waypointsArray Array of waypoints (lat, lon)
     */
    public static GHResponse blockedRoute(GraphHopper hopper, String[] waypointsArray) {
        System.out.println("Blocked route...");

        GHRequest req = new GHRequest();
        for (String s : waypointsArray) {
            double curLat = Double.parseDouble(s.split(",")[0]);
            double curLon = Double.parseDouble(s.split(",")[1]);

            req.addPoint(new GHPoint(curLat, curLon));
        }

        req.setProfile(_vehicle).setLocale(Locale.ENGLISH);

        // GH does not allow alt routes with > 2 waypoints, so we manage this case disabling alt route for >2 waypoints
        if (waypointsArray.length > 2)
            req.setAlgorithm(_algorithm);
        else
            req.setAlgorithm(Parameters.Algorithms.ALT_ROUTE);

        return hopper.route(req);
    }

    public static void printResponseDetails(GHResponse rsp) {
        // first check for errors
        if (rsp.hasErrors()) {
            System.out.println("Response error: " + rsp.getErrors());
            return;
        }

        // use the best path, see the GHResponse class for more possibilities.
        ResponsePath path = rsp.getBest();

        // points, distance in meters and time in millis of the full path
        PointList pointList = path.getPoints();
        double distance = path.getDistance();
        long timeInMs = path.getTime();

        System.out.printf("Distance: %.2f km\n", distance / 1000);
        System.out.printf("Time: %.2f min\n", (double) timeInMs / 3600000);

        // translation
        TranslationMap trMap = new TranslationMap().doImport();
        Translation itTranslation = trMap.getWithFallBack(Locale.ENGLISH);

        InstructionList il = path.getInstructions();
        // iterate over every turn instruction
        for (Instruction instruction : il) {
            System.out.println(instruction.getTurnDescription(itTranslation));
            //System.out.println(instruction.toString());
        }
    }

    public static void printAlternativeDetails(GHResponse rsp) {
        // first check for errors
        if (rsp.hasErrors()) {
            System.out.println("Response error: " + rsp.getErrors());
            return;
        }

        List<ResponsePath> paths = rsp.getAll();
        for (ResponsePath path : paths) {
            // points, distance in meters and time in millis of the full path
            // PointList pointList = path.getPoints();
            double distance = path.getDistance();
            long timeInMs = path.getTime();

            System.out.printf("Distance: %.2f km\n", distance / 1000);
            System.out.printf("Time: %.2f min\n", (double) timeInMs / 3600000);

            // translation
            TranslationMap trMap = new TranslationMap().doImport();
            Translation itTranslation = trMap.getWithFallBack(Locale.ENGLISH);

            InstructionList il = path.getInstructions();
            // iterate over every turn instruction
            for (Instruction instruction : il) {
                System.out.println(instruction.getTurnDescription(itTranslation));
                //System.out.println(instruction.toString());
            }
            System.out.println("------------------------------------");
        }
    }

    public static String serializeInstructions(InstructionList instructions) {

        List<Map<String, Object>> instrList = new ArrayList<>(instructions.size());
        int pointsIndex = 0;

        int tmpIndex;
        for (Iterator<Instruction> iterator = instructions.iterator(); iterator.hasNext(); pointsIndex = tmpIndex) {
            Instruction instruction = iterator.next();
            Map<String, Object> instrJson = new HashMap<>();
            instrList.add(instrJson);
            instrJson.put("text", Helper.firstBig(instruction.getTurnDescription(instructions.getTr())));
            instrJson.put("street_name", instruction.getName());
            instrJson.put("time", instruction.getTime());
            instrJson.put("distance", Helper.round(instruction.getDistance(), 3));
            instrJson.put("sign", instruction.getSign());
            instrJson.putAll(instruction.getExtraInfoJSON());
            tmpIndex = pointsIndex + instruction.getLength();
            instrJson.put("interval", Arrays.asList(pointsIndex, tmpIndex));
        }

        return new Gson().toJson(instrList.toArray());
//        return new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(instrList);  // Needed because the default Gson().toJson(...) serializer does not serialize NaN and Infinity values
    }

    /**
     * Make a GET request to the traffic sensor API and parse the response
     *
     * @param sensorId ID of the traffic sensor to query
     *
     * @return TrafficSensorResponse object containing the sensor information and realtime data
     */
    public static TrafficSensorResponse trafficSensorRequest(String sensorId) {
        String url = "https://servicemap.disit.org/WebAppGrafo/api/v1/?serviceUri=http://www.disit.org/km4city/resource/iot/orionUNIFI/DISIT/" + sensorId;

        // Make a GET request to the URL, that returns a JSON object with the sensor information and realtime data
        HttpClient httpClient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(url);

        try {
            // Execute the HTTP GET request
            HttpResponse response = httpClient.execute(httpGet);

            // Check if the response status is OK (HTTP 200)
            if (response.getStatusLine().getStatusCode() == 200) {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    // Parse the JSON response
                    String result = EntityUtils.toString(entity);
                    JSONObject json = new JSONObject(result);

                    // Get the coordinates of the sensor
                    JSONArray coordinates = json.getJSONObject("Service").getJSONArray("features").getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates");
                    JSONObject properties = json.getJSONObject("Service").getJSONArray("features").getJSONObject(0).getJSONObject("properties");
                    String address = properties.getString("address");
                    String name = properties.getString("name");
                    double averageSpeed = json.getJSONObject("realtime").getJSONObject("results").getJSONArray("bindings").getJSONObject(0).getJSONObject("averageSpeed").getDouble("value");
                    double congestionLevel = json.getJSONObject("realtime").getJSONObject("results").getJSONArray("bindings").getJSONObject(0).getJSONObject("congestionLevel").getDouble("value");

                    return new TrafficSensorResponse(sensorId, coordinates.getDouble(0), coordinates.getDouble(1), address, averageSpeed, congestionLevel);
                }
                else { // No entity received as response
                    System.err.println("HTTP GET request failed: no entity");
                }
            }
            else {  // Response status is not OK
                System.err.println("HTTP GET request failed with status code: " + response.getStatusLine().getStatusCode());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // --------------------------------------
    // Other utility methods (for developing)
    // --------------------------------------

    /**
     * Get the closest node/edge from lat, long coordinates
     *
     * @param hopper GraphHopper instance
     * @param lat    latitude of the point
     * @param lon    longitude of the point
     */
    public static int getClosestNode(GraphHopper hopper, double lat, double lon) {
        Snap qr = hopper.getLocationIndex().findClosest(lat, lon, EdgeFilter.ALL_EDGES);
        return qr.getClosestNode();
    }

    /**
     * Get the closest edge from lat, long coordinates
     *
     * @param hopper GraphHopper instance
     * @param lat    latitude of the point
     * @param lon    longitude of the point
     */
    public static EdgeIteratorState getClosestEdge(GraphHopper hopper, double lat, double lon) {
        Snap qr = hopper.getLocationIndex().findClosest(lat, lon, EdgeFilter.ALL_EDGES);
        return qr.getClosestEdge();
    }


    // reverse direction ----------------------
    // @see https://stackoverflow.com/questions/29851245/graphhopper-route-direction-weighting
    public static void printProps(GraphHopper hopper, double lat, double lon) {
        EdgeIteratorState edge = getClosestEdge(hopper, lat, lon);
        int baseNodeId = edge.getBaseNode();
        int adjNodeId = edge.getAdjNode();

        LocationIndex locationindex = hopper.getLocationIndex();
        Snap qr = locationindex.findClosest(lat, lon, EdgeFilter.ALL_EDGES);
        // come fare a assegnare un peso diverso ai due sensi di marcia di un edge (se presenti) ???
    }

    // @see https://stackoverflow.com/questions/29851245/graphhopper-route-direction-weighting
    public static boolean isReverseDirection(GraphHopper hopper, GHPoint target, GHPoint previous) {
        AngleCalc calc = new AngleCalc();
        // Input are two last points of vehicle. Base on two last points, I'm computing angle
        double angle = calc.calcOrientation(previous.lat, previous.lon, target.lat, target.lon);
        // Finding edge in place where is vehicle
        EdgeIteratorState edgeState = getClosestEdge(hopper, target.lat, target.lon);
        PointList pl = edgeState.fetchWayGeometry(FetchMode.ALL);
        // Computing angle of edge based on geometry
        double edgeAngle = calc.calcOrientation(pl.getLat(0), pl.getLon(0),
                pl.getLat(pl.size() - 1), pl.getLon(pl.size() - 1));
        // Comparing two edges
        return (Math.abs(edgeAngle - angle) > 90);
    }

    public static void printTest(GraphHopper hopper, GHResponse rsp) {
        // first check for errors
        if (rsp.hasErrors()) {
            System.out.println("Response error: " + rsp.getErrors());
            return;
        }

        // use the best path, see the GHResponse class for more possibilities.
        ResponsePath path = rsp.getBest();

        // points, distance in meters and time in millis of the full path
        PointList pointList = path.getPoints();

        System.out.println(pointList.toString() + "\n\n");

        for (int i = 0; i < pointList.size() - 1; i++) {
            System.out.println("Da " + pointList.getLat(i) + "," + pointList.getLon(i) + " A " +
                    pointList.getLat(i + 1) + "," + pointList.getLon(i + 1) +
                    " --> " + isReverseDirection(hopper, new GHPoint(pointList.getLat(i), pointList.getLon(i)),
                    new GHPoint(pointList.getLat(i + 1), pointList.getLon(i + 1))) + "\n");
        }
    }

    // polyline utilities

    public static PointList decodePolyline(String encoded, int initCap, boolean is3D) {
        PointList poly = new PointList(initCap, is3D);
        int index = 0;
        int len = encoded.length();
        int lat = 0, lng = 0, ele = 0;
        while (index < len) {
            // latitude
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int deltaLatitude = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += deltaLatitude;

            // longitude
            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int deltaLongitude = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += deltaLongitude;

            if (is3D) {
                // elevation
                shift = 0;
                result = 0;
                do {
                    b = encoded.charAt(index++) - 63;
                    result |= (b & 0x1f) << shift;
                    shift += 5;
                } while (b >= 0x20);
                int deltaElevation = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
                ele += deltaElevation;
                poly.add((double) lat / 1e5, (double) lng / 1e5, (double) ele / 100);
            }
            else
                poly.add((double) lat / 1e5, (double) lng / 1e5);
        }
        return poly;
    }

    public static String encodePolyline(PointList poly) {
        if (poly.isEmpty()) return "";
        return encodePolyline(poly, poly.is3D());
    }

    public static String encodePolyline(PointList poly, boolean includeElevation) {
        return encodePolyline(poly, includeElevation, 1e5);
    }

    public static String encodePolyline(PointList poly, boolean includeElevation, double precision) {
        StringBuilder sb = new StringBuilder();
        int size = poly.size();
        int prevLat = 0;
        int prevLon = 0;
        int prevEle = 0;
        for (int i = 0; i < size; i++) {
            int num = (int) Math.floor(poly.getLat(i) * precision);
            encodeNumber(sb, num - prevLat);
            prevLat = num;
            num = (int) Math.floor(poly.getLon(i) * precision);
            encodeNumber(sb, num - prevLon);
            prevLon = num;
            if (includeElevation) {
                num = (int) Math.floor(poly.getEle(i) * 100);
                encodeNumber(sb, num - prevEle);
                prevEle = num;
            }
        }
        return sb.toString();
    }

    private static void encodeNumber(StringBuilder sb, int num) {
        num = num << 1;
        if (num < 0) {
            num = ~num;
        }
        while (num >= 0x20) {
            int nextValue = (0x20 | (num & 0x1f)) + 63;
            sb.append((char) (nextValue));
            num >>= 5;
        }
        num += 63;
        sb.append((char) (num));
    }
}