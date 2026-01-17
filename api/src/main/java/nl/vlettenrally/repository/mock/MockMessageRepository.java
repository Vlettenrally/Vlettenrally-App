package nl.vlettenrally.repository.mock;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import nl.vlettenrally.domain.Message;
import nl.vlettenrally.repository.MessageRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Mock implementation of MessageRepository with prefilled data.
 * Annotated as @Alternative - activate in application.properties for development.
 */
@Alternative
@ApplicationScoped
public class MockMessageRepository implements MessageRepository {

    private final Map<UUID, Message> messages = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        initializeData();
    }

    public void initializeData() {
        messages.clear();
        
        Instant now = Instant.now();
        UUID teamId = MockTeamRepository.TEAM_WATERVLOOIEN_ID;

        // Message 1 - Welcome
        Message msg1 = new Message();
        msg1.setId(UUID.fromString("bbbb0001-0000-0000-0000-000000000001"));
        msg1.setTeamId(teamId);
        msg1.setMessageType(Message.MessageType.GENERAL);
        msg1.setContent("Welkom bij de Vlettenrally 2026! Veel succes vandaag!");
        msg1.setSentAt(now.minus(2, ChronoUnit.HOURS));
        msg1.setDelivered(true);
        msg1.setRead(true);
        messages.put(msg1.getId(), msg1);

        // Message 2 - Countdown
        Message msg2 = new Message();
        msg2.setId(UUID.fromString("bbbb0002-0000-0000-0000-000000000002"));
        msg2.setTeamId(teamId);
        msg2.setMessageType(Message.MessageType.COUNTDOWN);
        msg2.setContent("De rally start over 5 minuten. Maak je klaar!");
        msg2.setIcon("⏱️");
        msg2.setSentAt(now.minus(2, ChronoUnit.HOURS));
        msg2.setDelivered(true);
        msg2.setRead(true);
        messages.put(msg2.getId(), msg2);

        // Message 3 - Checkpoint Amstel
        Message msg3 = new Message();
        msg3.setId(UUID.fromString("bbbb0003-0000-0000-0000-000000000003"));
        msg3.setTeamId(teamId);
        msg3.setMessageType(Message.MessageType.CHECKPOINT);
        msg3.setContent("Checkpoint Amstel bereikt! +100 punten verdiend.");
        msg3.setIcon("🏁");
        msg3.setSentAt(now.minus(90, ChronoUnit.MINUTES));
        msg3.setDelivered(true);
        msg3.setRead(true);
        messages.put(msg3.getId(), msg3);

        // Message 4 - Checkpoint Prinsengracht
        Message msg4 = new Message();
        msg4.setId(UUID.fromString("bbbb0004-0000-0000-0000-000000000004"));
        msg4.setTeamId(teamId);
        msg4.setMessageType(Message.MessageType.CHECKPOINT);
        msg4.setContent("Checkpoint Prinsengracht bereikt! +150 punten verdiend.");
        msg4.setIcon("🏁");
        msg4.setSentAt(now.minus(45, ChronoUnit.MINUTES));
        msg4.setDelivered(true);
        msg4.setRead(true);
        messages.put(msg4.getId(), msg4);

        // Message 5 - Rival update
        Message msg5 = new Message();
        msg5.setId(UUID.fromString("bbbb0005-0000-0000-0000-000000000005"));
        msg5.setTeamId(teamId);
        msg5.setMessageType(Message.MessageType.RIVAL);
        msg5.setContent("Je rivaal \"Windkracht Tien\" heeft Checkpoint Amstel bereikt!");
        msg5.setIcon("⚔️");
        msg5.setSentAt(now.minus(75, ChronoUnit.MINUTES));
        msg5.setDelivered(true);
        msg5.setRead(false);
        messages.put(msg5.getId(), msg5);

        // Message 6 - Rallydesk announcement
        Message msg6 = new Message();
        msg6.setId(UUID.fromString("bbbb0006-0000-0000-0000-000000000006"));
        msg6.setTeamId(teamId);
        msg6.setMessageType(Message.MessageType.RALLYDESK);
        msg6.setContent("Let op: Door drukte op de Amstel is er een kleine vertraging mogelijk bij Checkpoint 2.");
        msg6.setIcon("📢");
        msg6.setSentAt(now.minus(45, ChronoUnit.MINUTES));
        msg6.setDelivered(true);
        msg6.setRead(false);
        messages.put(msg6.getId(), msg6);

        // Message 7 - Achievement
        Message msg7 = new Message();
        msg7.setId(UUID.fromString("bbbb0007-0000-0000-0000-000000000007"));
        msg7.setTeamId(teamId);
        msg7.setMessageType(Message.MessageType.ACHIEVEMENT);
        msg7.setContent("Gefeliciteerd! Je hebt de \"Snelste Starter\" badge verdiend!");
        msg7.setIcon("🏆");
        msg7.setSentAt(now.minus(30, ChronoUnit.MINUTES));
        msg7.setDelivered(true);
        msg7.setRead(false);
        messages.put(msg7.getId(), msg7);

        // Message 8 - Checkpoint Keizersgracht
        Message msg8 = new Message();
        msg8.setId(UUID.fromString("bbbb0008-0000-0000-0000-000000000008"));
        msg8.setTeamId(teamId);
        msg8.setMessageType(Message.MessageType.CHECKPOINT);
        msg8.setContent("Checkpoint Keizersgracht bereikt! +100 punten verdiend.");
        msg8.setIcon("🏁");
        msg8.setSentAt(now.minus(15, ChronoUnit.MINUTES));
        msg8.setDelivered(true);
        msg8.setRead(false);
        messages.put(msg8.getId(), msg8);

        // Message 9 - Rival standing
        Message msg9 = new Message();
        msg9.setId(UUID.fromString("bbbb0009-0000-0000-0000-000000000009"));
        msg9.setTeamId(teamId);
        msg9.setMessageType(Message.MessageType.RIVAL);
        msg9.setContent("Je staat nu 50 punten voor op je rivaal \"Windkracht Tien\"!");
        msg9.setIcon("🎯");
        msg9.setSentAt(now.minus(10, ChronoUnit.MINUTES));
        msg9.setDelivered(true);
        msg9.setRead(false);
        messages.put(msg9.getId(), msg9);

        System.out.println("🎭 MockMessageRepository initialized with " + messages.size() + " messages");
    }

    @Override
    public Optional<Message> findById(UUID id) {
        return Optional.ofNullable(messages.get(id));
    }

    @Override
    public List<Message> findAll() {
        return new ArrayList<>(messages.values());
    }

    @Override
    public List<Message> findByTeamId(UUID teamId) {
        return messages.values().stream()
                .filter(m -> teamId.equals(m.getTeamId()))
                .sorted(Comparator.comparing(Message::getSentAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<Message> findUnreadByTeamId(UUID teamId) {
        return messages.values().stream()
                .filter(m -> teamId.equals(m.getTeamId()) && !m.isRead())
                .sorted(Comparator.comparing(Message::getSentAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public Message save(Message message) {
        if (message.getId() == null) {
            message.setId(UUID.randomUUID());
        }
        messages.put(message.getId(), message);
        return message;
    }

    @Override
    public void markAsRead(UUID id) {
        Message message = messages.get(id);
        if (message != null) {
            message.setRead(true);
        }
    }

    @Override
    public void markAllAsReadForTeam(UUID teamId) {
        messages.values().stream()
                .filter(m -> teamId.equals(m.getTeamId()))
                .forEach(m -> m.setRead(true));
    }

    @Override
    public void delete(UUID id) {
        messages.remove(id);
    }
}
