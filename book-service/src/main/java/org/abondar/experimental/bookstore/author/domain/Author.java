package org.abondar.experimental.bookstore.author.domain;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.common.Text;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class Author {

    @EqualsAndHashCode.Include
    private final Id<Author> id;
    private final Text firstName;
    private final Text lastName;
    private final String sortName;
    private final LocalDate dateOfBirth;
    private final LocalDate dateOfDeath;
    private final String countryCode;
    private final String biography;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Author(
            Id<Author> id,
            Text firstName,
            Text lastName,
            String sortName,
            LocalDate dateOfBirth,
            LocalDate dateOfDeath,
            String countryCode,
            String biography,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(id);
        this.firstName = Objects.requireNonNull(firstName);
        this.lastName = Objects.requireNonNull(lastName);

        this.sortName = sortName;


        this.dateOfBirth = dateOfBirth;
        this.dateOfDeath = dateOfDeath;

        if (dateOfBirth != null && dateOfDeath != null && dateOfDeath.isBefore(dateOfBirth)) {
            throw new IllegalArgumentException(
                    "Date of death cannot precede date of birth"
            );
        }

        this.countryCode = countryCode;
        if (countryCode != null && !countryCode.matches("[A-Z]{2}")) {
            throw new IllegalArgumentException("Country code is invalid");
        }

        this.biography = biography;

        this.createdAt = Objects.requireNonNull(
                createdAt,
                "Creation time must not be null"
        );
        this.updatedAt = Objects.requireNonNull(
                updatedAt,
                "Update time must not be null"
        );

        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "Updated time cannot precede creation time"
            );
        }
    }

    public static Author create(
            Text firstName,
            Text lastName,
            String sortName,
            LocalDate dateOfBirth,
            LocalDate dateOfDeath,
            String countryCode,
            String biography
    ) {
        var now = Instant.now();

        return new Author(
                Id.generate(),
                firstName,
                lastName,
                sortName,
                dateOfBirth,
                dateOfDeath,
                countryCode,
                biography,
                now,
                now
        );
    }

    public static Author restore(
            Id<Author> id,
            Text firstName,
            Text lastName,
            String sortName,
            LocalDate dateOfBirth,
            LocalDate dateOfDeath,
            String countryCode,
            String biography,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new Author(
                id,
                firstName,
                lastName,
                sortName,
                dateOfBirth,
                dateOfDeath,
                countryCode,
                biography,
                createdAt,
                updatedAt
        );
    }

}
