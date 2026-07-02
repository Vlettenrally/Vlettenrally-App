package nl.vlettenrally.service.impl;

import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.repository.GameRepository;
import nl.vlettenrally.service.GameService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of GameService.
 * <p>
 * {@link #findById} and {@link #findByStatus} are read on the per-fix ingest path and
 * on every scheduler tick, so they are memoized under a short TTL (see
 * {@code quarkus.cache.caffeine."games".expire-after-write}). A poll cycle that ingests
 * many fixes for one game then reads the same {@code Game} row once, not once per fix.
 * Games are a small set and change rarely, so any write ({@link #save}/{@link #delete})
 * evicts both caches entirely; the short TTL is only a backstop against a missed eviction.
 */
@ApplicationScoped
public class GameServiceImpl implements GameService {

    static final String GAME_BY_ID_CACHE = "game-by-id";
    static final String GAMES_BY_STATUS_CACHE = "games-by-status";

    @Inject
    GameRepository gameRepository;

    @Override
    @CacheResult(cacheName = GAME_BY_ID_CACHE)
    public Optional<Game> findById(UUID id) {
        return gameRepository.findById(id);
    }

    @Override
    public List<Game> findAll() {
        return gameRepository.findAll();
    }

    @Override
    public List<Game> findActiveGames() {
        return gameRepository.findActiveGames();
    }

    @Override
    @CacheResult(cacheName = GAMES_BY_STATUS_CACHE)
    public List<Game> findByStatus(Game.GameStatus status) {
        return gameRepository.findByStatus(status);
    }

    @Override
    @CacheInvalidateAll(cacheName = GAME_BY_ID_CACHE)
    @CacheInvalidateAll(cacheName = GAMES_BY_STATUS_CACHE)
    public Game save(Game game) {
        return gameRepository.save(game);
    }

    @Override
    @CacheInvalidateAll(cacheName = GAME_BY_ID_CACHE)
    @CacheInvalidateAll(cacheName = GAMES_BY_STATUS_CACHE)
    public void delete(UUID id) {
        gameRepository.delete(id);
    }

    @Override
    public long countGames() {
        return gameRepository.count();
    }
}
