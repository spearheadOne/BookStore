package org.abondar.experimental.bookstore.common;

import java.util.Objects;

public record Text(String value) {

    public Text {
        Objects.requireNonNull(value, "Value must not be null");

        value = value.strip();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Value must not be blank");
        }
    }

}
