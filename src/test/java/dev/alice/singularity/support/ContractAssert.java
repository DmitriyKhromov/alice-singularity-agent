package dev.alice.singularity.support;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ContractAssert {

    private static final Pattern STATUS = Pattern.compile("(?im)^Status:\\s*(success|error)\\s*$");
    private static final Pattern ACTION = Pattern.compile("(?im)^Action:\\s*(.+)\\s*$");
    private static final Pattern DATA = Pattern.compile("(?is)Data:\\s*(.+?)\\s*Errors:");
    private static final Pattern ERRORS = Pattern.compile("(?im)^Errors:\\s*(.+)\\s*$");

    private ContractAssert() {
    }

    public static void assertSuccess(String answer) {
        assertNotNull(answer);
        assertTrue(STATUS.matcher(answer).find(), "missing Status: success|error\n" + answer);
        Matcher status = STATUS.matcher(answer);
        assertTrue(status.find());
        assertTrue(status.group(1).equalsIgnoreCase("success"), "expected success, got:\n" + answer);
        assertTrue(ACTION.matcher(answer).find(), "missing Action:\n" + answer);
        assertTrue(DATA.matcher(answer).find() || answer.toLowerCase(Locale.ROOT).contains("data:"), "missing Data:\n" + answer);
        assertTrue(answer.toLowerCase(Locale.ROOT).contains("errors:"), "missing Errors:\n" + answer);
        assertFalse(answer.contains("<|superquote|>"), "Data still has <|superquote|>:\n" + answer);
    }

    public static void assertError(String answer) {
        assertNotNull(answer);
        Matcher status = STATUS.matcher(answer);
        assertTrue(status.find(), "missing Status:\n" + answer);
        assertTrue(status.group(1).equalsIgnoreCase("error"), "expected error, got:\n" + answer);
        assertTrue(ACTION.matcher(answer).find(), "missing Action:\n" + answer);
        Matcher errors = ERRORS.matcher(answer);
        assertTrue(errors.find(), "missing Errors:\n" + answer);
        String err = errors.group(1).trim();
        assertFalse(err.isEmpty() || err.equalsIgnoreCase("null"), "Errors should explain the problem:\n" + answer);
    }

    public static void assertDataContains(String answer, String fragment) {
        assertTrue(answer.contains(fragment), "Data should contain '" + fragment + "':\n" + answer);
    }
}
