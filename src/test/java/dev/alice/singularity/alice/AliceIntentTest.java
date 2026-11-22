package dev.alice.singularity.alice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AliceIntentTest {

    @Test
    void classifiesMutateListOther() {
        assertEquals(AliceIntent.Kind.MUTATE, AliceIntent.classify("создай задачу купить молоко"));
        assertEquals(AliceIntent.Kind.MUTATE, AliceIntent.classify("поставь пробежка на завтра"));
        assertEquals(AliceIntent.Kind.MUTATE, AliceIntent.classify("переименуй задачу в хлеб"));
        assertEquals(AliceIntent.Kind.LIST, AliceIntent.classify("какие у меня задачи"));
        assertEquals(AliceIntent.Kind.LIST, AliceIntent.classify("что на сегодня"));
        assertEquals(AliceIntent.Kind.OTHER, AliceIntent.classify("сколько будет 2+2"));
    }
}
