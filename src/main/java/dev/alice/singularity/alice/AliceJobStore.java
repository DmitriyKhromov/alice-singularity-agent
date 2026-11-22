package dev.alice.singularity.alice;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory фоновые job'ы Алисы (create/update / долгий list).
 * Готовый результат живёт {@link #resultTtl} после завершения, затем забывается.
 */
@Component
public class AliceJobStore {

    public enum Status {
        PENDING,
        DONE,
        FAILED
    }

    public record Job(
            Status status,
            Instant startedAt,
            Instant finishedAt,
            String speech,
            List<AliceSessionStore.TaskRef> listedTasks
    ) {
    }

    private final ConcurrentHashMap<String, Job> jobs = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration resultTtl;
    private final Duration pendingLimit;

    public AliceJobStore() {
        this(Clock.systemUTC(), Duration.ofSeconds(10), Duration.ofSeconds(120));
    }

    AliceJobStore(Clock clock, Duration resultTtl, Duration pendingLimit) {
        this.clock = clock;
        this.resultTtl = resultTtl;
        this.pendingLimit = pendingLimit;
    }

    public boolean hasActive(String sessionId) {
        return get(sessionId).isPresent();
    }

    public Optional<Job> get(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        Job job = jobs.get(sessionId);
        if (job == null) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        if (job.status() == Status.PENDING) {
            if (job.startedAt().plus(pendingLimit).isBefore(now)) {
                jobs.remove(sessionId, job);
                return Optional.empty();
            }
            return Optional.of(job);
        }
        Instant finished = job.finishedAt() == null ? job.startedAt() : job.finishedAt();
        if (finished.plus(resultTtl).isBefore(now)) {
            jobs.remove(sessionId, job);
            return Optional.empty();
        }
        return Optional.of(job);
    }

    public boolean tryStart(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        if (get(sessionId).isPresent()) {
            return false;
        }
        Job pending = new Job(Status.PENDING, clock.instant(), null, null, List.of());
        Job prev = jobs.putIfAbsent(sessionId, pending);
        return prev == null;
    }

    public void complete(String sessionId, String speech, List<AliceSessionStore.TaskRef> listedTasks) {
        if (sessionId == null) {
            return;
        }
        jobs.put(sessionId, new Job(
                Status.DONE,
                clock.instant(),
                clock.instant(),
                speech,
                listedTasks == null ? List.of() : List.copyOf(listedTasks)
        ));
    }

    public void fail(String sessionId, String speech) {
        if (sessionId == null) {
            return;
        }
        jobs.put(sessionId, new Job(
                Status.FAILED,
                clock.instant(),
                clock.instant(),
                speech,
                List.of()
        ));
    }

    public void clear(String sessionId) {
        if (sessionId != null) {
            jobs.remove(sessionId);
        }
    }
}
