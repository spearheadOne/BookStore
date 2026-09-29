package org.abondar.experimental.bookstore.book.domain;

import java.util.Locale;
import java.util.Objects;

public record Isbn(String value) {

    public Isbn {
        Objects.requireNonNull(value, "ISBN must not be null");

        value = value
                .replaceAll("[\\s-]", "")
                .toUpperCase(Locale.ROOT);

        if (!isValid(value)) {
            throw new IllegalArgumentException("Invalid ISBN:" + value);
        }
    }


    private static boolean isValid(String value) {
        return switch (value.length()) {
            case 10 -> isValidIsbn10(value);
            case 13 -> isValidIsbn13(value);
            default -> false;
        };
    }

    private static boolean isValidIsbn10(String value) {
        if (!value.matches("\\d{9}[\\dX]")) {
            return false;
        }

        int sum = 0;

        for (int index = 0; index < 10; index++) {
            int digit = value.charAt(index) == 'X'
                    ? 10
                    : Character.digit(value.charAt(index), 10);

            sum += digit * (10 - index);
        }

        return sum % 11 == 0;
    }

    private static boolean isValidIsbn13(String value) {
        if (!value.matches("\\d{13}")) {
            return false;
        }

        int sum = 0;

        for (int index = 0; index < 13; index++) {
            int digit = Character.digit(value.charAt(index), 10);
            sum += digit * (index % 2 == 0 ? 1 : 3);
        }

        return sum % 10 == 0;
    }


}
