package nl.vlettenrally.data.dto.auth;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;

@RegisterForReflection
public record TeamSelectionResponse(
    String gameId,
    List<TeamOptionResponse> teamOptions
) {}