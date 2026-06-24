package nl.vlettenrally.data.dto.game;

import java.util.UUID;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record RankingResponse(
    int position,
    UUID teamId,
    String teamName,
    String teamDescription,
    int points
) {}
