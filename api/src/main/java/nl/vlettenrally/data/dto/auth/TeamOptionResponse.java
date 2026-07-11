package nl.vlettenrally.data.dto.auth;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.UUID;

@RegisterForReflection
public record TeamOptionResponse(
    UUID id,
    String name,
    String description,
    boolean checkedIn,
    boolean hasMainDevice,
    // Whether the regular game/team selection screen may still be used to register an
    // additional device for this team (false once the 15-min window has closed; such
    // teams are then only joinable via the invite QR).
    boolean acceptingNewDevices
) {
    public TeamOptionResponse(UUID id, String name) {
        this(id, name, null, false, false, true);
    }
}
