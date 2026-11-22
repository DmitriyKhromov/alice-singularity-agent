package dev.alice.singularity.alice;

import dev.alice.singularity.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AliceAuth {

    private final Set<String> allowedUserIds;

    public AliceAuth(AppProperties properties) {
        AppProperties.Alice alice = properties.alice();
        String raw = alice == null ? null : alice.allowedUserIds();
        this.allowedUserIds = parseIds(raw);
    }

    public boolean isAllowed(AliceDialogModels.AliceRequest request) {
        if (allowedUserIds.isEmpty()) {
            return false;
        }
        String accountId = accountUserId(request);
        return accountId != null && allowedUserIds.contains(accountId.toUpperCase(Locale.ROOT));
    }

    public static String accountUserId(AliceDialogModels.AliceRequest request) {
        if (request == null || request.session() == null) {
            return null;
        }
        if (request.session().user() != null && request.session().user().userId() != null
                && !request.session().user().userId().isBlank()) {
            return request.session().user().userId().trim();
        }
        return request.session().userId();
    }

    private static Set<String> parseIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}
