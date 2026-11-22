package dev.alice.singularity;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AliceSingularityApplication {

    public static void main(String[] args) {
        loadDotEnv();
        if (hasCliPhrase(args)) {
            System.setProperty("app.cli.enabled", "true");
            System.setProperty("APP_CLI_ENABLED", "true");
            System.setProperty("spring.main.web-application-type", "none");
            System.setProperty("SPRING_MAIN_WEB_APPLICATION_TYPE", "none");
        } else {
            System.setProperty("app.cli.enabled", System.getProperty("app.cli.enabled", "false"));
        }
        SpringApplication.run(AliceSingularityApplication.class, args);
    }

    private static boolean hasCliPhrase(String[] args) {
        for (String arg : args) {
            if (arg != null && !arg.isBlank() && !arg.startsWith("-")) {
                return true;
            }
        }
        return false;
    }

    private static void loadDotEnv() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        dotenv.entries().forEach(entry -> {
            if (System.getenv(entry.getKey()) == null && System.getProperty(entry.getKey()) == null) {
                System.setProperty(entry.getKey(), entry.getValue());
            }
        });
    }
}
