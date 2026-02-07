package nl.vlettenrally.data.dto.team;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;

@RegisterForReflection
public record ParticipantsUpdateRequest(List<String> participants) {}
