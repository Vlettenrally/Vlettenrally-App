package nl.vlettenrally.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Team registration from the pre-registration API.
 */
public class Registration {
    @JsonProperty("em_booking_id")
    private int bookingId;

    @JsonProperty("em_event_id")
    private int eventId;

    @JsonProperty("event_name")
    private String eventName;

    @JsonProperty("boat_number")
    private String boatNumber;

    @JsonProperty("scouting_group")
    private String scoutingGroup;

    @JsonProperty("booking_date")
    private String bookingDate;

    public int getBookingId() {
        return bookingId;
    }

    public void setBookingId(int bookingId) {
        this.bookingId = bookingId;
    }

    public int getEventId() {
        return eventId;
    }

    public void setEventId(int eventId) {
        this.eventId = eventId;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public String getBoatNumber() {
        return boatNumber;
    }

    public void setBoatNumber(String boatNumber) {
        this.boatNumber = boatNumber;
    }

    public String getScoutingGroup() {
        return scoutingGroup;
    }

    public void setScoutingGroup(String scoutingGroup) {
        this.scoutingGroup = scoutingGroup;
    }

    public String getBookingDate() {
        return bookingDate;
    }

    public void setBookingDate(String bookingDate) {
        this.bookingDate = bookingDate;
    }
}
