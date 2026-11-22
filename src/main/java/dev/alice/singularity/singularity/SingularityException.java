package dev.alice.singularity.singularity;

public class SingularityException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    public SingularityException(int statusCode, String responseBody) {
        super("SingularityApp HTTP " + statusCode + ": " + abbreviate(responseBody));
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int statusCode() {
        return statusCode;
    }

    public String responseBody() {
        return responseBody;
    }

    private static String abbreviate(String body) {
        if (body == null || body.isBlank()) {
            return "<empty>";
        }
        String trimmed = body.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500) + "...";
    }
}
