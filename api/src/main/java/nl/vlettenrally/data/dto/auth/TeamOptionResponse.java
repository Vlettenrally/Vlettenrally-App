package nl.vlettenrally.data.dto.auth;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.UUID;

@RegisterForReflection
public record TeamOptionResponse(
    UUID id,
    String name,
    String boatNumber
) {}
