package org.abondar.experimental.bookstore.author.domain;

import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.common.Text;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AuthorTest {

    @Test
    void createsAuthor() {
        var firstName = new Text("test");
        var lastName = new Text("test");
        var sortName = "test";
        var dateOfBirth = LocalDate.of(1950, 3, 10);
        var dateOfDeath = LocalDate.of(2020, 8, 4);
        var countryCode = "DE";
        var biography = "test";

        var author = Author.create(
                firstName,
                lastName,
                sortName,
                dateOfBirth,
                dateOfDeath,
                countryCode,
                biography
        );


        assertAll(
                () -> assertNotNull(author.getId()),
                () -> assertEquals(firstName, author.getFirstName()),
                () -> assertEquals(lastName, author.getLastName()),
                () -> assertEquals(sortName, author.getSortName()),
                () -> assertEquals(dateOfBirth, author.getDateOfBirth()),
                () -> assertEquals(dateOfDeath, author.getDateOfDeath()),
                () -> assertEquals(countryCode, author.getCountryCode()),
                () -> assertEquals(biography, author.getBiography()),
                () -> assertNotNull(author.getCreatedAt()),
                () -> assertEquals(author.getCreatedAt(), author.getUpdatedAt())
        );

    }

    @Test
    void createsAuthorWithNullables() {
        var firstName = new Text("test");
        var lastName = new Text("test");

        var author = Author.create(
                firstName,
                lastName,
                null,
                null,
                null,
                null,
                null
        );


        assertAll(
                () -> assertNotNull(author.getId()),
                () -> assertEquals(firstName, author.getFirstName()),
                () -> assertEquals(lastName, author.getLastName()),
                () -> assertNull(author.getSortName()),
                () -> assertNull(author.getDateOfBirth()),
                () -> assertNull(author.getDateOfDeath()),
                () -> assertNull(author.getCountryCode()),
                () -> assertNull(author.getBiography())
        );

    }

    @Test
    void rejectsWithNullLastName() {
        assertThrows(
                NullPointerException.class,
                () -> Author.create(
                        new Text("test"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );

    }

    @Test
    void rejectsWithNullFirstName() {
        assertThrows(
                NullPointerException.class,
                () -> Author.create(
                        null,
                        new Text("test"),
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );

    }

    @Test
    void rejectsDateOfDeathBeforeDob() {
        var firstName = new Text("test");
        var lastName = new Text("test");
        var sortName = "test";
        var dateOfBirth = LocalDate.now();
        var dateOfDeath = LocalDate.of(1995, 1, 1);

        assertThrows(
                IllegalArgumentException.class,
                () -> Author.create(
                        firstName,
                        lastName,
                        sortName,
                        dateOfBirth,
                        dateOfDeath,
                        null,
                        null
                )
        );
    }


    @Test
    void rejectMisformattedCountryCode() {
        var firstName = new Text("test");
        var lastName = new Text("test");
        var sortName = "test";
        var countryCode = "test";

        assertThrows(
                IllegalArgumentException.class,
                () -> Author.create(
                        firstName,
                        lastName,
                        sortName,
                        null,
                        null,
                        countryCode,
                        null
                )
        );
    }

    @Test
    void restoresAuthor() {
        var firstName = new Text("test");
        var lastName = new Text("test");
        var sortName = "test";
        var dateOfBirth = LocalDate.now();
        var dateOfDeath = LocalDate.now();
        var countryCode = "DE";
        var biography = "test";

        var author = Author.create(
                firstName,
                lastName,
                sortName,
                dateOfBirth,
                dateOfDeath,
                countryCode,
                biography
        );

        var restored = Author.restore(
                author.getId(),
                firstName,
                lastName,
                sortName,
                dateOfBirth,
                dateOfDeath,
                countryCode,
                biography,
                author.getCreatedAt(),
                author.getUpdatedAt()
        );

        assertAll(
                () -> assertEquals(author.getId(), restored.getId()),
                () -> assertEquals(author.getFirstName(), restored.getFirstName()),
                () -> assertEquals(author.getLastName(), restored.getLastName()),
                () -> assertEquals(author.getSortName(), restored.getSortName()),
                () -> assertEquals(author.getDateOfBirth(), restored.getDateOfBirth()),
                () -> assertEquals(author.getDateOfDeath(), restored.getDateOfDeath()),
                () -> assertEquals(author.getCountryCode(), restored.getCountryCode()),
                () -> assertEquals(author.getBiography(), restored.getBiography()),
                () -> assertEquals(author.getCreatedAt(), restored.getCreatedAt()),
                () -> assertEquals(author.getUpdatedAt(), restored.getUpdatedAt())
        );

    }

    @Test
    void rejectsUpdateTimeBeforeCreationTime() {
        var createdAt = Instant.parse("2026-01-01T12:00:00Z");
        var updatedAt = createdAt.minusSeconds(1);

        assertThrows(
                IllegalArgumentException.class,
                () -> Author.restore(
                        Id.generate(),
                        new Text("Test"),
                        new Text("Test"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        createdAt,
                        updatedAt
                )
        );
    }
}
