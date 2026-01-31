package nl.vlettenrally.data.repository.mock;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.MessageDelivery;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.repository.MessageDeliveryRepository;
import nl.vlettenrally.data.repository.MessageRepository;
import nl.vlettenrally.data.repository.TeamRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Mock implementation of MessageDeliveryRepository.
 * Only active in dev and test profiles - replaced by PostgresMessageDeliveryRepository in prod.
 */
@IfBuildProfile(anyOf = {"dev", "test"})
@ApplicationScoped
public class MockMessageDeliveryRepository implements MessageDeliveryRepository {

    private final Map<UUID, MessageDelivery> deliveries = new ConcurrentHashMap<>();

    @Inject
    MessageRepository messageRepository;

    @Inject
    TeamRepository teamRepository;

    @Override
    public Optional<MessageDelivery> findById(UUID id) {
        return Optional.ofNullable(deliveries.get(id));
    }

    @Override
    public List<MessageDelivery> findByMessageId(UUID messageId) {
        return deliveries.values().stream()
                .filter(d -> messageId.equals(d.getMessageId()))
                .collect(Collectors.toList());
    }

    @Override
    public List<MessageDelivery> findByTeamId(UUID teamId) {
        return deliveries.values().stream()
                .filter(d -> teamId.equals(d.getTeamId()))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<MessageDelivery> findByMessageIdAndTeamId(UUID messageId, UUID teamId) {
        return deliveries.values().stream()
                .filter(d -> messageId.equals(d.getMessageId()) && teamId.equals(d.getTeamId()))
                .findFirst();
    }

    @Override
    public List<MessageDelivery> findFailedByMessageId(UUID messageId) {
        return deliveries.values().stream()
                .filter(d -> messageId.equals(d.getMessageId()) && !d.isDelivered())
                .collect(Collectors.toList());
    }

    @Override
    public List<MessageDelivery> findFailedByGameId(UUID gameId) {
        // Get all teams for this game
        List<UUID> teamIds = teamRepository.findByGameId(gameId).stream()
                .map(Team::getId)
                .collect(Collectors.toList());
        
        // Find all failed deliveries for those teams
        return deliveries.values().stream()
                .filter(d -> teamIds.contains(d.getTeamId()) && !d.isDelivered())
                .collect(Collectors.toList());
    }

    @Override
    public MessageDelivery save(MessageDelivery delivery) {
        if (delivery.getId() == null) {
            delivery.setId(UUID.randomUUID());
        }
        deliveries.put(delivery.getId(), delivery);
        return delivery;
    }

    @Override
    public void delete(UUID id) {
        deliveries.remove(id);
    }
}
