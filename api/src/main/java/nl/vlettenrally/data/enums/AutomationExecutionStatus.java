package nl.vlettenrally.data.enums;

/**
 * Outcome of a single automation rule firing.
 */
public enum AutomationExecutionStatus {
    SUCCESS,                // All actions executed
    PARTIAL,                // Some actions failed
    FAILED                  // All actions failed
}
