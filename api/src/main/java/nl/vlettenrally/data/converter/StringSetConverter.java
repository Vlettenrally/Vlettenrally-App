package nl.vlettenrally.data.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JPA Converter for PostgreSQL TEXT[] array to Java Set<String>
 * Converts between Java Set and PostgreSQL array representation
 */
@Converter
public class StringSetConverter implements AttributeConverter<Set<String>, String[]> {

    @Override
    public String[] convertToDatabaseColumn(Set<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return new String[0];
        }
        return attribute.toArray(new String[0]);
    }

    @Override
    public Set<String> convertToEntityAttribute(String[] dbData) {
        if (dbData == null || dbData.length == 0) {
            return new HashSet<>();
        }
        return Arrays.stream(dbData)
                .filter(s -> s != null && !s.isEmpty())
                .collect(Collectors.toSet());
    }
}

