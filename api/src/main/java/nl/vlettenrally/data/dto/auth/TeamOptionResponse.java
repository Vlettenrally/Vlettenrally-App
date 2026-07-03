package nl.vlettenrally.data.dto.auth;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.UUID;

@RegisterForReflection
public record TeamOptionResponse(
    UUID id,
    String name,
    String description,
    boolean checkedIn,
    boolean hasMainDevice
) {
    public TeamOptionResponse(UUID id, String name) {
        this(id, name, null, false, false);
    }
}
