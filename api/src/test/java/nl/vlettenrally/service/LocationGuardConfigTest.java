package nl.vlettenrally.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.service.util.TrackOutlierFilter;

/**
 * Unit tests for the per-game guard resolution in {@link LocationGuardConfig}. The CDI cache
 * interceptors don't fire in a plain unit test, so these exercise the fallback/override logic
 * directly (caching itself is a Quarkus concern verified at integration level).
 */
@ExtendWith(MockitoExtension.class)
class LocationGuardConfigTest {

    @Mock
    GameService gameService;

    @InjectMocks
    LocationGuardConfig config;

    private UUID gameId;

    @BeforeEach
    void setUp() {
        gameId = UUID.randomUUID();
        // Mirror the application.properties global defaults.
        config.minIntervalSeconds = 5;
        config.maxSpeedKmh = 120;
        config.boatMaxSpeedKmh = 25;
        config.turnAngleThresholdDegrees = 120;
        config.minBearingSegmentMeters = 10;
        config.maxAccuracyMeters = 50;
        config.staleFixSeconds = 300;
    }

    @Test
    void thresholdsFor_allNullOverrides_usesGlobalDefaults() {
        Game game = new Game();
        game.setId(gameId); // every guard override left null
        when(gameService.findById(gameId)).thenReturn(Optional.of(game));

        TrackOutlierFilter.Thresholds t = config.thresholdsFor(gameId);

        assertEquals(5, t.minIntervalSeconds());
        assertEquals(50, t.maxAccuracyMeters());
        assertEquals(120, t.maxSpeedKmh());
        assertEquals(25, t.boatMaxSpeedKmh());
        assertEquals(120, t.turnAngleThresholdDegrees());
        assertEquals(10, t.minBearingSegmentMeters());
        assertEquals(300, t.staleFixSeconds());
    }

    @Test
    void thresholdsFor_withOverrides_usesGameValues() {
        Game game = new Game();
        game.setId(gameId);
        game.setLocMinIntervalSeconds(10L);
        game.setLocMaxSpeedKmh(80.0);
        game.setLocBoatMaxSpeedKmh(15.0);
        game.setLocTurnAngleThresholdDegrees(90.0);
        game.setLocMinBearingSegmentMeters(5.0);
        game.setLocMaxAccuracyMeters(30.0);
        game.setLocStaleFixSeconds(120L);
        when(gameService.findById(gameId)).thenReturn(Optional.of(game));

        TrackOutlierFilter.Thresholds t = config.thresholdsFor(gameId);

        assertEquals(10, t.minIntervalSeconds());
        assertEquals(30, t.maxAccuracyMeters());
        assertEquals(80, t.maxSpeedKmh());
        assertEquals(15, t.boatMaxSpeedKmh());
        assertEquals(90, t.turnAngleThresholdDegrees());
        assertEquals(5, t.minBearingSegmentMeters());
        assertEquals(120, t.staleFixSeconds());
    }

    @Test
    void thresholdsFor_partialOverride_mixesGameAndGlobal() {
        Game game = new Game();
        game.setId(gameId);
        game.setLocMaxSpeedKmh(60.0); // only the hard ceiling is overridden
        when(gameService.findById(gameId)).thenReturn(Optional.of(game));

        TrackOutlierFilter.Thresholds t = config.thresholdsFor(gameId);

        assertEquals(60, t.maxSpeedKmh());   // overridden
        assertEquals(25, t.boatMaxSpeedKmh()); // global default
        assertEquals(300, t.staleFixSeconds()); // global default
    }

    @Test
    void thresholdsFor_unknownGame_usesGlobalDefaults() {
        when(gameService.findById(gameId)).thenReturn(Optional.empty());

        TrackOutlierFilter.Thresholds t = config.thresholdsFor(gameId);

        assertEquals(5, t.minIntervalSeconds());
        assertEquals(120, t.maxSpeedKmh());
        assertEquals(300, t.staleFixSeconds());
    }
}
