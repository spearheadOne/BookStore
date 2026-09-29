package org.abondar.experimental.bookstore.common;

import java.util.Objects;
import java.util.UUID;

public record Id<T>(UUID value) {

    public Id {
        Objects.requireNonNull(value, "ID must not be null");
    }

    public static <T> Id<T> generate() {
        return new Id<T>(UUID.randomUUID());
    }

}
