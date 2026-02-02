package nl.vlettenrally.data.repository.mock;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import nl.vlettenrally.data.domain.MessageTemplate;
import nl.vlettenrally.data.enums.MessageType;
import nl.vlettenrally.data.repository.MessageTemplateRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock implementation of MessageTemplateRepository with prefilled data.
 * Only active in dev and test profiles - replaced by PostgresMessageTemplateRepository in prod.
 */
@IfBuildProfile(anyOf = {"dev", "test"})
@ApplicationScoped
public class MockMessageTemplateRepository implements MessageTemplateRepository {

    private final Map<UUID, MessageTemplate> templates = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        initializeData();
    }

    public void initializeData() {
        templates.clear();

        // Template 1 - Welcome
        MessageTemplate welcome = new MessageTemplate();
        welcome.setId(UUID.fromString("cccc0001-0000-0000-0000-000000000001"));
        welcome.setName("Welkom bericht");
        welcome.setMessageType(MessageType.GENERAL);
        welcome.setContent("Welkom bij de Vlettenrally! We wensen jullie veel succes vandaag!");
        welcome.setIcon("👋");
        templates.put(welcome.getId(), welcome);

        // Template 2 - Delay notification
        MessageTemplate delay = new MessageTemplate();
        delay.setId(UUID.fromString("cccc0002-0000-0000-0000-000000000002"));
        delay.setName("Vertraging melding");
        delay.setMessageType(MessageType.RALLYDESK);
        delay.setContent("Let op: Er is een vertraging bij checkpoint [X]. Houd rekening met extra wachttijd.");
        delay.setIcon("⚠️");
        templates.put(delay.getId(), delay);

        // Template 3 - Weather warning
        MessageTemplate weather = new MessageTemplate();
        weather.setId(UUID.fromString("cccc0003-0000-0000-0000-000000000003"));
        weather.setName("Weerswaarschuwing");
        weather.setMessageType(MessageType.RALLYDESK);
        weather.setContent("Weerswaarschuwing: Er wordt regen/wind verwacht. Vaar voorzichtig!");
        weather.setIcon("🌧️");
        templates.put(weather.getId(), weather);

        // Template 4 - Finish reminder
        MessageTemplate finish = new MessageTemplate();
        finish.setId(UUID.fromString("cccc0004-0000-0000-0000-000000000004"));
        finish.setName("Finish herinnering");
        finish.setMessageType(MessageType.COUNTDOWN);
        finish.setContent("Herinnering: De finish sluit over [X] minuten. Zorg dat je op tijd bent!");
        finish.setIcon("🏁");
        templates.put(finish.getId(), finish);

        Log.info("🎭 MockMessageTemplateRepository initialized with " + templates.size() + " templates");
    }

    @Override
    public Optional<MessageTemplate> findById(UUID id) {
        return Optional.ofNullable(templates.get(id));
    }

    @Override
    public List<MessageTemplate> findAll() {
        return new ArrayList<>(templates.values());
    }

    @Override
    public MessageTemplate save(MessageTemplate template) {
        if (template.getId() == null) {
            template.setId(UUID.randomUUID());
        }
        templates.put(template.getId(), template);
        return template;
    }

    @Override
    public void delete(UUID id) {
        templates.remove(id);
    }
}
