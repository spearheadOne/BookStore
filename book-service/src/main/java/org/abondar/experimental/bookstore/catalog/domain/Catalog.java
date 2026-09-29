package org.abondar.experimental.bookstore.catalog.domain;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.common.Text;

import java.time.Instant;
import java.util.Objects;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class Catalog {

    @EqualsAndHashCode.Include
    private final Id<Catalog> id;

    private final Text name;

    private final Instant createdAt;
    private final Instant updatedAt;

    private Catalog(
            Id<Catalog> id,
            Text name,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);

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

    public static Catalog create(Text name) {
        var now = Instant.now();

        return new Catalog(
                Id.generate(),
                name,
                now,
                now
        );
    }

    public static Catalog restore(
            Id<Catalog> id,
            Text name,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new Catalog(
                id,
                name,
                createdAt,
                updatedAt
        );
    }

}
