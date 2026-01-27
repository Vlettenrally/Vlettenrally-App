package nl.vlettenrally.data.dto.team;

import java.util.List;

public record TeamUpdateRequest(
    String name,
    String description,
    String boatNumber,
    List<String> participants,
    String notes
) {}
