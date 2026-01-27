package nl.vlettenrally.data.dto.team;

import java.util.List;
import java.util.UUID;

public record TeamCreateRequest(
    UUID gameId,
    String name,
    String description,
    String boatNumber,
    List<String> participants,
    String notes
) {}
