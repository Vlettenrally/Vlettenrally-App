package nl.vlettenrally.client.dto.legendstracking;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@RegisterForReflection
@NoArgsConstructor
@Getter
@Setter
public class EventParticipant {
    @JsonProperty("event_participants_id")
    private int eventParticipantsId;

    @JsonProperty("event_id")
    private int eventId;

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("last_name")
    private String lastName;

    @JsonProperty("country")
    private String country;

    @JsonProperty("gender")
    private String gender;

    @JsonProperty("bib")
    private Integer bib;

    @JsonProperty("bib_label")
    private String bibLabel;

    @JsonProperty("start_date")
    private String startDate;
}
