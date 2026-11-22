package dev.alice.singularity.singularity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.alice.singularity.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class SingularityClient {

    private static final Logger log = LoggerFactory.getLogger(SingularityClient.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper json;
    private final String baseUrl;
    private final String apiToken;
    private final ZoneId zoneId;

    public SingularityClient(AppProperties properties, ObjectMapper json) {
        this.json = json;
        AppProperties.Singularity singularity = properties.singularity();
        this.baseUrl = trimSlash(Objects.requireNonNullElse(singularity.baseUrl(), "https://api.singularity-app.com"));
        this.apiToken = singularity.apiToken() == null ? "" : singularity.apiToken().trim();
        this.zoneId = ZoneId.of(properties.timezone() == null || properties.timezone().isBlank()
                ? "Europe/Moscow"
                : properties.timezone());
    }

    public void requireToken() {
        if (apiToken.isBlank()) {
            throw new IllegalStateException("Не задан SINGULARITY_API_TOKEN");
        }
    }

    public String createTask(String title, String start, Integer priority, String note) {
        requireToken();
        ObjectNode body = json.createObjectNode();
        body.put("title", title);
        String normalizedStart = normalizeDateTime(start);
        if (normalizedStart != null) {
            body.put("start", normalizedStart);
        }
        if (priority != null) {
            body.put("priority", priority);
        }
        if (note != null && !note.isBlank()) {
            body.put("note", note);
        }
        return exchange("POST", "/v2/task", null, body);
    }

    public String listTasks(
            Integer maxCount,
            Integer offset,
            String startDateFrom,
            String startDateTo,
            Boolean includeArchived,
            Boolean includeRemoved
    ) {
        requireToken();
        Map<String, String> query = new LinkedHashMap<>();
        query.put("maxCount", String.valueOf(maxCount == null ? 20 : maxCount));
        if (offset != null) {
            query.put("offset", String.valueOf(offset));
        }
        if (startDateFrom != null && !startDateFrom.isBlank()) {
            query.put("startDateFrom", Objects.requireNonNullElse(normalizeDateOnly(startDateFrom), startDateFrom.trim()));
        }
        if (startDateTo != null && !startDateTo.isBlank()) {
            query.put("startDateTo", Objects.requireNonNullElse(normalizeDateOnly(startDateTo), startDateTo.trim()));
        }
        if (includeArchived != null) {
            query.put("includeArchived", String.valueOf(includeArchived));
        }
        if (includeRemoved != null) {
            query.put("includeRemoved", String.valueOf(includeRemoved));
        }
        return exchange("GET", "/v2/task", query, null);
    }

    public String updateTask(
            String id,
            String title,
            String start,
            String note,
            Integer priority,
            String journalDate,
            String deleteDate
    ) {
        requireToken();
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id задачи обязателен");
        }
        ObjectNode body = json.createObjectNode();
        if (title != null && !title.isBlank()) {
            body.put("title", title.trim());
        }
        String normalizedStart = normalizeDateTime(start);
        if (normalizedStart != null) {
            body.put("start", normalizedStart);
        }
        if (note != null) {
            body.put("note", note);
        }
        if (priority != null) {
            body.put("priority", priority);
        }
        String normalizedJournal = normalizeDateTime(journalDate);
        if (normalizedJournal != null) {
            body.put("journalDate", normalizedJournal);
        }
        String normalizedDelete = normalizeDateTime(deleteDate);
        if (normalizedDelete != null) {
            body.put("deleteDate", normalizedDelete);
        }
        if (body.isEmpty()) {
            throw new IllegalArgumentException("Нужно хотя бы одно поле для обновления");
        }
        String path = "/v2/task/" + URLEncoder.encode(id.trim(), StandardCharsets.UTF_8);
        return exchange("PATCH", path, null, body);
    }

    private String exchange(String method, String path, Map<String, String> query, JsonNode body) {
        String url = baseUrl + path;
        if (query != null && !query.isEmpty()) {
            String qs = query.entrySet().stream()
                    .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                            + "="
                            + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));
            url = url + "?" + qs;
        }

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiToken)
                    .header("Accept", "application/json");

            if (body != null) {
                byte[] bytes = json.writeValueAsBytes(body);
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofByteArray(bytes));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }

            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String responseBody = response.body() == null ? "" : response.body();
            log.info("[SingularityClient] {} {} -> {}", method, path, response.statusCode());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new SingularityException(response.statusCode(), responseBody);
            }
            return responseBody.isBlank() ? "{}" : responseBody;
        } catch (SingularityException ex) {
            throw ex;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Ошибка HTTP к SingularityApp: " + ex.getMessage(), ex);
        }
    }

    /**
     * Singularity expects ISO-8601 datetime with timezone for start/journalDate/deleteDate.
     * Accepts relative RU words, YYYY-MM-DD, or datetime without/with zone.
     */
    String normalizeDateTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
        LocalDate today = LocalDate.now(zoneId);
        if (value.equals("сегодня") || value.equals("today")) {
            return today.atStartOfDay(zoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
        if (value.equals("завтра") || value.equals("tomorrow")) {
            return today.plusDays(1).atStartOfDay(zoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
        if (value.equals("послезавтра")) {
            return today.plusDays(2).atStartOfDay(zoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }

        String original = raw.trim();
        if (original.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return LocalDate.parse(original).atStartOfDay(zoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
        // datetime without timezone, e.g. 2026-11-23T00:00:00 or 2026-11-23T00:00:00.000
        if (original.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?")) {
            String withSeconds = original.length() == 16 ? original + ":00" : original;
            LocalDateTime local = LocalDateTime.parse(withSeconds, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return local.atZone(zoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
        // already has Z or ±HH:MM
        if (original.matches(".*([zZ]|[+-]\\d{2}:\\d{2})$")) {
            return original;
        }
        // last resort: treat as date prefix
        if (original.length() >= 10 && original.substring(0, 10).matches("\\d{4}-\\d{2}-\\d{2}")) {
            return LocalDate.parse(original.substring(0, 10)).atStartOfDay(zoneId)
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
        return original;
    }

    String normalizeDateOnly(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.length() >= 10 && value.substring(0, 10).matches("\\d{4}-\\d{2}-\\d{2}")) {
            return value.substring(0, 10);
        }
        return value;
    }

    private static String trimSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
