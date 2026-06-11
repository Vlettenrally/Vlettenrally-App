package nl.vlettenrally.data.dto.automation;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.UUID;

/**
 * Request to copy all eligible automation rules from another game into the
 * game identified by the path.
 */
@RegisterForReflection
public record CopyAutomationsRequest(UUID sourceGameId) {}
