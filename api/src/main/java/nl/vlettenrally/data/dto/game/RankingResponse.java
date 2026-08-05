package nl.vlettenrally.data.dto.game;

import java.util.UUID;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record RankingResponse(
    int position,
    UUID teamId,
    String teamName,
    String teamDescription,
    int points,
    boolean finished,
    /*
      ISO-8601 instant the team was marked finished; null when it has not finished. The client
      derives the finish status (finished / late / DNF) from this plus the game's end time and
      status, so a game that completes flips teams to DNF without a refetch.
     */
    String finishedAt,
    boolean freeClass
) {}
