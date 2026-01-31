package nl.vlettenrally.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Details about a Socialrun event.
 */
public class SocialrunDetail {
    @JsonProperty("id")
    private int id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("start_date")
    private String startDate;

    @JsonProperty("actual_start_date")
    private String actualStartDate;

    @JsonProperty("end_date")
    private String endDate;

    @JsonProperty("center")
    private Coordinates center;

    @JsonProperty("zoom")
    private int zoom;

    @JsonProperty("event_distance_category_id")
    private int eventDistanceCategoryId;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getActualStartDate() {
        return actualStartDate;
    }

    public void setActualStartDate(String actualStartDate) {
        this.actualStartDate = actualStartDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public Coordinates getCenter() {
        return center;
    }

    public void setCenter(Coordinates center) {
        this.center = center;
    }

    public int getZoom() {
        return zoom;
    }

    public void setZoom(int zoom) {
        this.zoom = zoom;
    }

    public int getEventDistanceCategoryId() {
        return eventDistanceCategoryId;
    }

    public void setEventDistanceCategoryId(int eventDistanceCategoryId) {
        this.eventDistanceCategoryId = eventDistanceCategoryId;
    }
}
