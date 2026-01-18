package nl.vlettenrally.data.dto.game;

import java.util.UUID;

public record RankingResponse(
    int position,
    UUID teamId,
    String teamName,
    String boatNumber,
    int points
) {}
