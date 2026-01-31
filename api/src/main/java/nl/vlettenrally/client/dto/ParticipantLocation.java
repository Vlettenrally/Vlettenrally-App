package nl.vlettenrally.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Location data for a participant from the tracking API.
 */
public class ParticipantLocation {
    @JsonProperty("event_participants_id")
    private int eventParticipantsId;

    @JsonProperty("event_id")
    private int eventId;

    @JsonProperty("status_id")
    private int statusId;

    @JsonProperty("bib")
    private int bib;

    @JsonProperty("bib_label")
    private String bibLabel;

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("last_name")
    private String lastName;

    @JsonProperty("country")
    private String country;

    @JsonProperty("gender")
    private String gender;

    @JsonProperty("tracker_id")
    private int trackerId;

    @JsonProperty("location_time")
    private String locationTime;

    @JsonProperty("report_time")
    private String reportTime;

    @JsonProperty("location")
    private Coordinates location;

    @JsonProperty("battery_percentage")
    private int batteryPercentage;

    @JsonProperty("gps_accuracy")
    private int gpsAccuracy;

    @JsonProperty("is_sos_report")
    private int isSosReport;

    public int getEventParticipantsId() {
        return eventParticipantsId;
    }

    public void setEventParticipantsId(int eventParticipantsId) {
        this.eventParticipantsId = eventParticipantsId;
    }

    public int getEventId() {
        return eventId;
    }

    public void setEventId(int eventId) {
        this.eventId = eventId;
    }

    public int getStatusId() {
        return statusId;
    }

    public void setStatusId(int statusId) {
        this.statusId = statusId;
    }

    public int getBib() {
        return bib;
    }

    public void setBib(int bib) {
        this.bib = bib;
    }

    public String getBibLabel() {
        return bibLabel;
    }

    public void setBibLabel(String bibLabel) {
        this.bibLabel = bibLabel;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public int getTrackerId() {
        return trackerId;
    }

    public void setTrackerId(int trackerId) {
        this.trackerId = trackerId;
    }

    public String getLocationTime() {
        return locationTime;
    }

    public void setLocationTime(String locationTime) {
        this.locationTime = locationTime;
    }

    public String getReportTime() {
        return reportTime;
    }

    public void setReportTime(String reportTime) {
        this.reportTime = reportTime;
    }

    public Coordinates getLocation() {
        return location;
    }

    public void setLocation(Coordinates location) {
        this.location = location;
    }

    public int getBatteryPercentage() {
        return batteryPercentage;
    }

    public void setBatteryPercentage(int batteryPercentage) {
        this.batteryPercentage = batteryPercentage;
    }

    public int getGpsAccuracy() {
        return gpsAccuracy;
    }

    public void setGpsAccuracy(int gpsAccuracy) {
        this.gpsAccuracy = gpsAccuracy;
    }

    public int getIsSosReport() {
        return isSosReport;
    }

    public void setIsSosReport(int isSosReport) {
        this.isSosReport = isSosReport;
    }
}
