package it.zoo.feeding.infrastructure.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Converter
public class LocalTimeListConverter implements AttributeConverter<List<LocalTime>, String> {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final String SEPARATOR = ",";

    @Override
    public String convertToDatabaseColumn(List<LocalTime> attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.stream()
                .map(FORMAT::format)
                .collect(Collectors.joining(SEPARATOR));
    }

    @Override
    public List<LocalTime> convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return Arrays.stream(dbData.split(SEPARATOR))
                .map(value -> LocalTime.parse(value, FORMAT))
                .collect(Collectors.toList());
    }
}
