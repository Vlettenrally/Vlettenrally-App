package nl.vlettenrally.data.repository.mock;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import nl.vlettenrally.data.domain.AdminNotification;
import nl.vlettenrally.data.repository.AdminNotificationRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@IfBuildProfile(anyOf = {"dev", "test"})
@ApplicationScoped
public class MockAdminNotificationRepository implements AdminNotificationRepository {

    private final Map<UUID, AdminNotification> notifications = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        initializeData();
    }

    public void initializeData() {
        notifications.clear();
        Instant now = Instant.now();

        AdminNotification first = new AdminNotification();
        first.setId(UUID.fromString("cccc0001-0000-0000-0000-000000000001"));
        first.setGameId(MockGameRepository.GAME_2026_ID);
        first.setTeamId(MockTeamRepository.TEAM_WATERVLOOIEN_ID);
        first.setSource("AUTOMATION_NOTIFICATION");
        first.setContent("De Watervlooien nadert checkpoint Amstel.");
        first.setMetadataJson("{\"ruleName\":\"Checkpoint proximity\",\"teamName\":\"De Watervlooien\"}");
        first.setCreatedAt(now.minus(15, ChronoUnit.MINUTES));
        notifications.put(first.getId(), first);

        AdminNotification second = new AdminNotification();
        second.setId(UUID.fromString("cccc0002-0000-0000-0000-000000000002"));
        second.setGameId(MockGameRepository.GAME_2026_ID);
        second.setTeamId(MockTeamRepository.TEAM_WINDKRACHT_ID);
        second.setSource("AUTOMATION_NOTIFICATION");
        second.setContent("Windkracht Tien ging De Watervlooien voorbij.");
        second.setMetadataJson("{\"ruleName\":\"Rival overtake\",\"teamName\":\"Windkracht Tien\"}");
        second.setCreatedAt(now.minus(5, ChronoUnit.MINUTES));
        second.setRead(true);
        second.setReadAt(now.minus(1, ChronoUnit.MINUTES));
        second.setReadByAdminId(MockAdminRepository.ADMIN_DEFAULT_ID);
        notifications.put(second.getId(), second);
    }

    @Override
    public Optional<AdminNotification> findById(UUID id) {
        return Optional.ofNullable(notifications.get(id));
    }

    @Override
    public List<AdminNotification> findByGameId(UUID gameId, int page, int pageSize) {
        return notifications.values().stream()
            .filter(n -> gameId.equals(n.getGameId()))
            .sorted(Comparator.comparing(AdminNotification::getCreatedAt).reversed())
            .skip((long) page * pageSize)
            .limit(pageSize)
            .toList();
    }

    @Override
    public long countByGameId(UUID gameId) {
        return notifications.values().stream().filter(n -> gameId.equals(n.getGameId())).count();
    }

    @Override
    public AdminNotification save(AdminNotification notification) {
        if (notification.getId() == null) {
            notification.setId(UUID.randomUUID());
        }
        if (notification.getCreatedAt() == null) {
            notification.setCreatedAt(Instant.now());
        }
        notifications.put(notification.getId(), notification);
        return notification;
    }

    @Override
    public void markAsRead(UUID notificationId, UUID readByAdminId) {
        AdminNotification notification = notifications.get(notificationId);
        if (notification == null || notification.isRead()) {
            return;
        }
        notification.setRead(true);
        notification.setReadAt(Instant.now());
        notification.setReadByAdminId(readByAdminId);
    }

    @Override
    public void markAllAsReadByGame(UUID gameId, UUID readByAdminId) {
        Instant now = Instant.now();
        notifications.values().stream()
            .filter(n -> gameId.equals(n.getGameId()) && !n.isRead())
            .forEach(n -> {
                n.setRead(true);
                n.setReadAt(now);
                n.setReadByAdminId(readByAdminId);
            });
    }
}
