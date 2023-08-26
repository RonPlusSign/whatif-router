package com.dashboard.servlet;

public class TrafficSensorResponse {
    private String sensorId;
    private double latitude, longitude;
    private String address;
    private double averageSpeed;

    public TrafficSensorResponse(String sensorId, double latitude, double longitude, String address, double averageSpeed) {
        this.sensorId = sensorId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.address = address;
        this.averageSpeed = averageSpeed;
    }

    public String getSensorId() {
        return sensorId;
    }

    public void setSensorId(String sensorId) {
        this.sensorId = sensorId;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public double getAverageSpeed() {
        return averageSpeed;
    }

    public void setAverageSpeed(double averageSpeed) {
        this.averageSpeed = averageSpeed;
    }

    @Override
    public String toString() {
        return "TrafficSensorResponse {" +
                "sensorId='" + sensorId + '\'' +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", address='" + address + '\'' +
                ", averageSpeed=" + averageSpeed +
                " }";
    }
}
