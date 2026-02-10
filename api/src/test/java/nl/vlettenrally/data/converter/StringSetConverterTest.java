package nl.vlettenrally.data.converter;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StringSetConverter
 */
class StringSetConverterTest {

    private final StringSetConverter converter = new StringSetConverter();

    @Test
    void testConvertToDatabaseColumn_withValidSet() {
        Set<String> roles = Set.of("admin", "viewer", "editor");
        String[] result = converter.convertToDatabaseColumn(roles);

        assertNotNull(result);
        assertEquals(3, result.length);
        assertTrue(Set.of(result).containsAll(roles));
    }

    @Test
    void testConvertToDatabaseColumn_withEmptySet() {
        Set<String> roles = new HashSet<>();
        String[] result = converter.convertToDatabaseColumn(roles);

        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    void testConvertToDatabaseColumn_withNull() {
        String[] result = converter.convertToDatabaseColumn(null);

        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    void testConvertToEntityAttribute_withValidArray() {
        String[] dbData = new String[]{"admin", "viewer", "editor"};
        Set<String> result = converter.convertToEntityAttribute(dbData);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertTrue(result.containsAll(Set.of("admin", "viewer", "editor")));
    }

    @Test
    void testConvertToEntityAttribute_withEmptyArray() {
        String[] dbData = new String[]{};
        Set<String> result = converter.convertToEntityAttribute(dbData);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testConvertToEntityAttribute_withNull() {
        Set<String> result = converter.convertToEntityAttribute(null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testConvertToEntityAttribute_withNullElements() {
        String[] dbData = new String[]{"admin", null, "", "viewer"};
        Set<String> result = converter.convertToEntityAttribute(dbData);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains("admin"));
        assertTrue(result.contains("viewer"));
        assertFalse(result.contains(null));
        assertFalse(result.contains(""));
    }

    @Test
    void testRoundTrip() {
        Set<String> original = Set.of("admin", "super_admin", "game_master");
        String[] dbData = converter.convertToDatabaseColumn(original);
        Set<String> result = converter.convertToEntityAttribute(dbData);

        assertEquals(original, result);
    }
}

