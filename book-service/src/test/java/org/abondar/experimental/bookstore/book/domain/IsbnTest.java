package org.abondar.experimental.bookstore.book.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class IsbnTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "0306406152",
            "0-306-40615-2",
            "080442957X",
            "0-8044-2957-X"
    })
    void acceptsValidIsbn10(String value) {
        var isbn = new Isbn(value);
        assertEquals(value.replace("-", ""), isbn.value());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0306406153",  // incorrect checksum
            "0804429571",  // X replaced with an incorrect digit
            "123456789",   // too short
            "123456789XX"  // invalid format
    })
    void rejectsInvalidIsbn10(String value) {
        assertThrows(IllegalArgumentException.class, () -> new Isbn(value));
    }


    @ParameterizedTest
    @ValueSource(strings = {
            "9780306406157",
            "978-0-306-40615-7",
            "9780134685991",
            "978-0-13-468599-1"
    })
    void acceptsValidIsbn13(String value) {
        var isbn = new Isbn(value);
        assertEquals(value.replace("-", ""), isbn.value());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "9780306406158", // incorrect checksum
            "9780134685992", // incorrect checksum
            "978030640615",  // too short
            "978030640615X"  // ISBN-13 cannot contain X
    })
    void rejectsInvalidIsbn13(String value) {
        assertThrows(IllegalArgumentException.class, () -> new Isbn(value));
    }

}
