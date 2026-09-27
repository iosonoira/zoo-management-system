package it.zoo.feeding.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LocalTimeListConverterTest {

    private final LocalTimeListConverter converter = new LocalTimeListConverter();

    @Test
    void shouldRoundTripMultipleValues() {
        List<LocalTime> times = List.of(LocalTime.of(7, 30), LocalTime.of(18, 0));

        String column = converter.convertToDatabaseColumn(times);
        assertEquals("07:30,18:00", column);

        List<LocalTime> result = converter.convertToEntityAttribute(column);
        assertEquals(times, result);
    }

    @Test
    void shouldRoundTripSingleValue() {
        List<LocalTime> times = List.of(LocalTime.of(9, 15));

        String column = converter.convertToDatabaseColumn(times);
        assertEquals("09:15", column);

        List<LocalTime> result = converter.convertToEntityAttribute(column);
        assertEquals(times, result);
    }

    @Test
    void shouldMapNullToNull() {
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
    }
}
