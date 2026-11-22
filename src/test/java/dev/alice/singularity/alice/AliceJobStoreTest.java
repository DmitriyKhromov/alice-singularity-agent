package dev.alice.singularity.alice;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AliceJobStoreTest {

    @Test
    void resultExpiresAfterTtl() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-22T12:00:00Z"));
        Clock clock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        AliceJobStore store = new AliceJobStore(clock, Duration.ofSeconds(10), Duration.ofSeconds(120));
        assertTrue(store.tryStart("s1"));
        store.complete("s1", "Создала задачу «молоко».", List.of());
        assertTrue(store.get("s1").isPresent());
        now.set(now.get().plusSeconds(11));
        assertFalse(store.get("s1").isPresent());
    }

    @Test
    void tryStartFailsWhileActive() {
        AliceJobStore store = new AliceJobStore();
        assertTrue(store.tryStart("s1"));
        assertFalse(store.tryStart("s1"));
        store.complete("s1", "ok", List.of());
        assertEquals(AliceJobStore.Status.DONE, store.get("s1").orElseThrow().status());
    }
}
