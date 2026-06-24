package nl.vlettenrally.service;

/**
 * Result of a generic import operation (counts only).
 */
public record ImportResult(
        int created,
        int updated,
        int errors
) {}
