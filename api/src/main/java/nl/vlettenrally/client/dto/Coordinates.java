package nl.vlettenrally.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Geographic coordinates.
 */
public class Coordinates {
    @JsonProperty("x")
    private double longitude;

    @JsonProperty("y")
    private double latitude;

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }
}
