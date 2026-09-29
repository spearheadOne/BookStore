package org.abondar.experimental.bookstore.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TextTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "test",
            " test",
            "test "
    })
    void acceptsValues(String value) {
        var field = new Text(value);
        assertEquals("test", field.value());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            " "
    })
    void rejectsValues(String value) {
        assertThrows(IllegalArgumentException.class, () -> new Text(value));
    }

    @Test
    void rejectsNullTitle() {
        assertThrows(NullPointerException.class, () -> new Text(null));
    }

}
