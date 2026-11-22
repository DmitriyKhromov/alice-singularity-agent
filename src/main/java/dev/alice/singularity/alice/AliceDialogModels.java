package dev.alice.singularity.alice;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AliceDialogModels {

    private AliceDialogModels() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AliceRequest(
            String version,
            Session session,
            RequestBody request,
            JsonNode meta,
            JsonNode state
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Session(
                @JsonProperty("new") Boolean isNew,
                @JsonProperty("session_id") String sessionId,
                @JsonProperty("skill_id") String skillId,
                @JsonProperty("message_id") Integer messageId,
                @JsonProperty("user_id") String userId,
                User user,
                Application application
        ) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record User(@JsonProperty("user_id") String userId) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Application(@JsonProperty("application_id") String applicationId) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record RequestBody(
                String command,
                @JsonProperty("original_utterance") String originalUtterance,
                String type,
                JsonNode nlu,
                JsonNode markup
        ) {
        }
    }

    public static Map<String, Object> response(AliceRequest request, String text, boolean endSession) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", request.version() == null ? "1.0" : request.version());
        if (request.session() != null) {
            Map<String, Object> session = new LinkedHashMap<>();
            session.put("session_id", request.session().sessionId());
            session.put("message_id", request.session().messageId());
            session.put("user_id", request.session().userId());
            body.put("session", session);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("text", text == null ? "" : text);
        response.put("end_session", endSession);
        body.put("response", response);
        return body;
    }
}
