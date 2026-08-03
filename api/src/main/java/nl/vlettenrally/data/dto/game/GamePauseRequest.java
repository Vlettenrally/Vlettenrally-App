package nl.vlettenrally.data.dto.game;

/**
 * Body of {@code POST /games/{gameId}/pause}.
 *
 * @param reason why the rally was stopped, shown to teams on their dashboard and kept with
 *               the pause period as the record of the incident. Optional — an emergency is
 *               a bad moment to make someone type.
 */
public record GamePauseRequest(String reason) {
}
