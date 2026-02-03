package nl.vlettenrally.client.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Result of testing a location endpoint connection.
 */
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class TestConnectionResult {
    private boolean success;
    private int participantCount;
    private String errorMessage;
}
