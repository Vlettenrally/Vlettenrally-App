package nl.vlettenrally.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class MessagePlaceholdersTest {

    @Test
    void replacesProvidedKeys() {
        String result = MessagePlaceholders.resolve(
            "{team} bij {checkpoint}", Map.of("team", "De Watervlooien", "checkpoint", "Finish"));
        assertEquals("De Watervlooien bij Finish", result);
    }

    @Test
    void leavesUnprovidedAndUnknownPlaceholdersIntact() {
        String result = MessagePlaceholders.resolve(
            "{team} ging {rival} voorbij {typo}", Map.of("team", "A"));
        assertEquals("A ging {rival} voorbij {typo}", result);
    }

    @Test
    void replacesAllOccurrences() {
        String result = MessagePlaceholders.resolve("{team} en nogmaals {team}", Map.of("team", "X"));
        assertEquals("X en nogmaals X", result);
    }

    @Test
    void nullContentReturnsNull() {
        assertNull(MessagePlaceholders.resolve(null, Map.of("team", "X")));
    }

    @Test
    void nullValueLeavesPlaceholder() {
        Map<String, String> values = new HashMap<>();
        values.put("checkpoint", null);
        assertEquals("bij {checkpoint}", MessagePlaceholders.resolve("bij {checkpoint}", values));
    }
}
