package nl.vlettenrally.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * External event from the pre-registration API.
 */
public class ExternalEvent {
    @JsonProperty("event_id")
    private int eventId;

    @JsonProperty("post_id")
    private int postId;

    @JsonProperty("event_name")
    private String eventName;

    @JsonProperty("booking_count")
    private int bookingCount;

    public int getEventId() {
        return eventId;
    }

    public void setEventId(int eventId) {
        this.eventId = eventId;
    }

    public int getPostId() {
        return postId;
    }

    public void setPostId(int postId) {
        this.postId = postId;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public int getBookingCount() {
        return bookingCount;
    }

    public void setBookingCount(int bookingCount) {
        this.bookingCount = bookingCount;
    }
}
