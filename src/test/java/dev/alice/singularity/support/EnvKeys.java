package dev.alice.singularity.support;

import io.github.cdimascio.dotenv.Dotenv;

public final class EnvKeys {

    private EnvKeys() {
    }

    public static void loadDotEnvIntoSystemProperties() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        dotenv.entries().forEach(entry -> {
            if (System.getenv(entry.getKey()) == null && System.getProperty(entry.getKey()) == null) {
                System.setProperty(entry.getKey(), entry.getValue());
            }
        });
    }

    public static boolean hasLiveKeys() {
        loadDotEnvIntoSystemProperties();
        String giga = firstNonBlank(System.getenv("GIGACHAT_AUTH_KEY"), System.getProperty("GIGACHAT_AUTH_KEY"));
        String token = firstNonBlank(System.getenv("SINGULARITY_API_TOKEN"), System.getProperty("SINGULARITY_API_TOKEN"));
        return giga != null && !giga.isBlank() && token != null && !token.isBlank();
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }
}
