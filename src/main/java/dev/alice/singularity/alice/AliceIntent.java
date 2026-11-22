package dev.alice.singularity.alice;

import java.util.Locale;
import java.util.regex.Pattern;

final class AliceIntent {

    enum Kind {
        MUTATE,
        LIST,
        OTHER
    }

    private static final Pattern MUTATE = Pattern.compile(
            ".*(создай|создать|поставь|поставить|добавь|добавить|переименуй|переименовать|"
                    + "перенеси|перенести|сдвинь|обнови|обновить|измени|изменить|удали в корзину).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern LIST = Pattern.compile(
            ".*(задач|список|что у меня|что на сегодня|какие|покажи|напомни что).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private AliceIntent() {
    }

    static Kind classify(String utterance) {
        if (utterance == null || utterance.isBlank()) {
            return Kind.OTHER;
        }
        String v = utterance.toLowerCase(Locale.ROOT);
        if (MUTATE.matcher(v).matches()) {
            return Kind.MUTATE;
        }
        if (LIST.matcher(v).matches()) {
            return Kind.LIST;
        }
        return Kind.OTHER;
    }
}
