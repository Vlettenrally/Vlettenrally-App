package nl.vlettenrally.service;

/**
 * Result of a team import operation.
 */
public record ImportResult(
        int created,
        int updated,
        int errors
) {}
