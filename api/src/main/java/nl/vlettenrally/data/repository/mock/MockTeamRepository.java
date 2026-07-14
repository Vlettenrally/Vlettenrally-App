package nl.vlettenrally.data.repository.mock;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.enums.CheckInMethod;
import nl.vlettenrally.data.repository.TeamRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Mock implementation of TeamRepository with prefilled data.
 * Only active in dev and test profiles - replaced by PostgresTeamRepository in prod.
 */
@IfBuildProfile(anyOf = {"dev", "test"})
@ApplicationScoped
public class MockTeamRepository implements TeamRepository {

    private final Map<UUID, Team> teams = new ConcurrentHashMap<>();

    // Well-known UUIDs for consistent cross-reference
    public static final UUID TEAM_WATERVLOOIEN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID TEAM_WINDKRACHT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID TEAM_GORILLAS_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    public static final UUID TEAM_HAAK_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    public static final UUID TEAM_ZEILZUSJES_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @PostConstruct
    void init() {
        initializeData();
    }

    public void initializeData() {
        teams.clear();
        
        Instant now = Instant.now();

        // Team 1 - De Watervlooien (main player team)
        Team team1 = new Team();
        team1.setId(TEAM_WATERVLOOIEN_ID);
        team1.setGameId(MockGameRepository.GAME_2026_ID);
        team1.setName("De Watervlooien");
        team1.setDescription("Snelle en wendbare vlettenvaarders");
        team1.setParticipants(List.of("Jan Janssen", "Piet Pietersen", "Klaas Kansen"));
        team1.setDeviceId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        team1.setCheckedIn(true);
        team1.setCheckedInDigitally(true);
        team1.setCheckInMethod(CheckInMethod.DIGITAL);
        team1.setRivals(Collections.singletonList(TEAM_WINDKRACHT_ID));
        team1.setNotes("Favoriet voor de winst!");
        team1.setEmergencyPhoneNumbers(List.of("+31612345678", "+31687654321"));
        team1.setCreatedAt(now.minus(48, ChronoUnit.HOURS));
        team1.setUpdatedAt(now.minus(30, ChronoUnit.MINUTES));
        teams.put(team1.getId(), team1);

        // Team 2 - Windkracht Tien (rival team)
        Team team2 = new Team();
        team2.setId(TEAM_WINDKRACHT_ID);
        team2.setGameId(MockGameRepository.GAME_2026_ID);
        team2.setName("Windkracht Tien");
        team2.setDescription("Altijd volle kracht vooruit");
        team2.setParticipants(List.of("Anna de Groot", "Marie Storm", "Lisa Wind"));
        team2.setDeviceId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
        team2.setCheckedIn(true);
        team2.setCheckedInDigitally(true);
        team2.setCheckInMethod(CheckInMethod.DIGITAL);
        team2.setRivals(Collections.singletonList(TEAM_WATERVLOOIEN_ID));
        team2.setEmergencyPhoneNumbers(List.of("+31687654321"));
        team2.setCreatedAt(now.minus(47, ChronoUnit.HOURS));
        team2.setUpdatedAt(now.minus(45, ChronoUnit.MINUTES));
        teams.put(team2.getId(), team2);

        // Team 3 - De Grachtengorrillas (checked in manually at the rally desk)
        Team team3 = new Team();
        team3.setId(TEAM_GORILLAS_ID);
        team3.setGameId(MockGameRepository.GAME_2026_ID);
        team3.setName("De Grachtengorrillas");
        team3.setDescription("Rustig maar doeltreffend");
        team3.setParticipants(List.of("Tom de Vries", "Henk Boot"));
        team3.setCheckedIn(true);
        team3.setCheckedInDigitally(false);
        team3.setCheckInMethod(CheckInMethod.MANUAL);
        team3.setRivals(Collections.emptyList());
        team3.setEmergencyPhoneNumbers(List.of("+31612345678", "+31687654321"));
        team3.setCreatedAt(now.minus(46, ChronoUnit.HOURS));
        team3.setUpdatedAt(now.minus(1, ChronoUnit.HOURS));
        teams.put(team3.getId(), team3);

        // Team 4 - Kapitein Haak (not checked in)
        Team team4 = new Team();
        team4.setId(TEAM_HAAK_ID);
        team4.setGameId(MockGameRepository.GAME_2026_ID);
        team4.setName("Kapitein Haak");
        team4.setDescription("Piraten van de grachten");
        team4.setParticipants(List.of("Peter Pan", "Wendy Darling", "Tinkerbell"));
        team4.setCheckedIn(false);
        team4.setCheckedInDigitally(false);
        team4.setTraccarEnabled(true);
        team4.setTraccarIdentifier("mock-traccar-kapitein-haak");
        team4.setRivals(Collections.emptyList());
        team4.setEmergencyPhoneNumbers(List.of("+31612345678", "+31687654321"));
        team4.setCreatedAt(now.minus(45, ChronoUnit.HOURS));
        team4.setUpdatedAt(now.minus(2, ChronoUnit.HOURS));
        teams.put(team4.getId(), team4);

        // Team 5 - De Zeilzusjes (sent off to check in on their own phones, not finished yet)
        Team team5 = new Team();
        team5.setId(TEAM_ZEILZUSJES_ID);
        team5.setGameId(MockGameRepository.GAME_2026_ID);
        team5.setName("De Zeilzusjes");
        team5.setDescription("Zusjes met de wind mee");
        team5.setParticipants(List.of("Sanne Zeil", "Fleur Zeil"));
        team5.setCheckedIn(true);
        team5.setCheckedInDigitally(false);
        team5.setCheckInMethod(CheckInMethod.DIGITAL);
        team5.setCheckedInAt(now.minus(10, ChronoUnit.MINUTES));
        team5.setRivals(Collections.emptyList());
        team5.setEmergencyPhoneNumbers(List.of("+31612345678"));
        team5.setCreatedAt(now.minus(44, ChronoUnit.HOURS));
        team5.setUpdatedAt(now.minus(10, ChronoUnit.MINUTES));
        teams.put(team5.getId(), team5);

        Log.info("🎭 MockTeamRepository initialized with " + teams.size() + " teams");
    }

    @Override
    public Optional<Team> findById(UUID id) {
        return Optional.ofNullable(teams.get(id));
    }

    @Override
    public List<Team> findAll() {
        return new ArrayList<>(teams.values());
    }

    @Override
    public List<Team> findByGameId(UUID gameId) {
        return teams.values().stream()
                .filter(t -> gameId.equals(t.getGameId()))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Team> findByDeviceId(UUID deviceId) {
        return teams.values().stream()
                .filter(t -> deviceId.equals(t.getDeviceId()))
                .findFirst();
    }


    @Override
    public Optional<Team> findByTraccarIdentifier(String traccarIdentifier) {
        if (traccarIdentifier == null) {
            return Optional.empty();
        }
        return teams.values().stream()
                .filter(t -> traccarIdentifier.equals(t.getTraccarIdentifier()))
                .findFirst();
    }

    @Override
    public List<Team> findRivals(UUID teamId) {
        return findById(teamId)
                .map(team -> team.getRivals().stream()
                        .map(this::findById)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    @Override
    public Optional<Team> findByGameIdAndExternalId(UUID gameId, String externalId) {
        return teams.values().stream()
                .filter(t -> gameId.equals(t.getGameId()) && externalId.equals(t.getExternalId()))
                .findFirst();
    }

    @Override
    public Team save(Team team) {
        if (team.getId() == null) {
            team.setId(UUID.randomUUID());
        }
        team.setUpdatedAt(Instant.now());
        teams.put(team.getId(), team);
        return team;
    }

    @Override
    public void delete(UUID id) {
        teams.remove(id);
    }
}
