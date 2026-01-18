package nl.vlettenrally.data.dto.auth;

public record TeamRegistrationRequest(
    String gameId,
    String boatNumber,
    String scoutingGroup,
    String emergencyContact,
    String[] participantNames,
    String rivalTeamId,
    String bookingId
) {}
