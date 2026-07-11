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
    // Whether the regular game/team selection screen may still be used to register a
    // device for this team (false once the digital check-in window — measured from the
    // team's physical check-in — has closed; such teams are then only joinable via the
    // invite QR or an admin).
    boolean acceptingNewDevices
) {
    public TeamOptionResponse(UUID id, String name) {
        this(id, name, null, false, false, true);
    }
}
