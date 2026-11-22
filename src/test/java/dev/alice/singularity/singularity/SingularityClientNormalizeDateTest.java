package dev.alice.singularity.singularity;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.alice.singularity.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingularityClientNormalizeDateTest {

    private SingularityClient client;
    private final ZoneId zone = ZoneId.of("Europe/Moscow");

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
                "Europe/Moscow",
                new AppProperties.GigaChat("key", "GIGACHAT_API_PERS", "GigaChat-2-Max"),
                new AppProperties.Singularity("token", "https://api.singularity-app.com"),
                new AppProperties.Alice("", "Поставьте Диме новую задачу")
        );
        client = new SingularityClient(properties, new ObjectMapper());
    }

    @Test
    void blankBecomesNull() {
        assertNull(client.normalizeDateTime(null));
        assertNull(client.normalizeDateTime("  "));
    }

    @Test
    void yyyyMmDdGetsTimezone() {
        String value = client.normalizeDateTime("2026-11-23");
        assertEquals("2026-11-23T00:00:00+03:00", value);
    }

    @Test
    void datetimeWithoutZoneGetsTimezone() {
        String value = client.normalizeDateTime("2026-11-23T00:00:00");
        assertEquals("2026-11-23T00:00:00+03:00", value);
    }

    @Test
    void relativeTomorrow() {
        String value = client.normalizeDateTime("завтра");
        String expected = LocalDate.now(zone).plusDays(1).atStartOfDay(zone)
                .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        assertEquals(expected, value);
    }

    @Test
    void alreadyZonedKept() {
        String raw = "2026-11-23T00:00:00+03:00";
        assertEquals(raw, client.normalizeDateTime(raw));
        assertTrue(client.normalizeDateTime("2026-07-16T12:00:00.000Z").endsWith("Z"));
    }
}
