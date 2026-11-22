package dev.alice.singularity.singularity;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.alice.singularity.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SingularityClientTest {

    @Test
    void requireTokenFailsWhenBlank() {
        AppProperties properties = new AppProperties(
                "Europe/Moscow",
                new AppProperties.GigaChat("key", "GIGACHAT_API_PERS", "GigaChat-2-Max"),
                new AppProperties.Singularity("  ", "https://api.singularity-app.com")
        );
        SingularityClient client = new SingularityClient(properties, new ObjectMapper());
        assertThrows(IllegalStateException.class, client::requireToken);
    }
}
