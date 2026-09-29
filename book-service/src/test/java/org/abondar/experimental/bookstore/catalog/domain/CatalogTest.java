package org.abondar.experimental.bookstore.catalog.domain;

import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.common.Text;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CatalogTest {

    @Test
    void createsCatalog() {
        var name = new Text("Main catalog");

        var catalog = Catalog.create(name);

        assertAll(
                () -> assertNotNull(catalog.getId()),
                () -> assertEquals(name, catalog.getName()),
                () -> assertNotNull(catalog.getCreatedAt()),
                () -> assertEquals(catalog.getCreatedAt(), catalog.getUpdatedAt())
        );
    }

    @Test
    void rejectsNullName() {
        assertThrows(NullPointerException.class, () -> Catalog.create(null));
    }


    @Test
    void restoresCatalog() {
        Id<Catalog> id = Id.generate();
        var name = new Text("Main catalog");
        var createdAt = Instant.parse("2026-01-01T12:00:00Z");
        var updatedAt = createdAt.plusSeconds(60);

        var catalog = Catalog.restore(id, name, createdAt, updatedAt);

        assertAll(
                () -> assertEquals(id, catalog.getId()),
                () -> assertEquals(name, catalog.getName()),
                () -> assertEquals(createdAt, catalog.getCreatedAt()),
                () -> assertEquals(updatedAt, catalog.getUpdatedAt())
        );
    }


    @Test
    void rejectsNullIdOnRestore() {
        var now = Instant.parse("2026-01-01T12:00:00Z");

        assertThrows(NullPointerException.class,
                () -> Catalog.restore(
                        null,
                        new Text("Main catalog"),
                        now,
                        now
                )
        );
    }

    @Test
    void rejectsNullTimestampsOnRestore() {
        Id<Catalog> id = Id.generate();
        var name = new Text("Main catalog");
        var now = Instant.parse("2026-01-01T12:00:00Z");

        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> Catalog.restore(
                                id,
                                name,
                                null,
                                now
                        )
                ),
                () -> assertThrows(NullPointerException.class,
                        () -> Catalog.restore(
                                id,
                                name,
                                now,
                                null
                        )
                )
        );
    }

    @Test
    void rejectsUpdateTimeBeforeCreationTime() {
        var createdAt = Instant.parse("2026-01-01T12:00:00Z");
        var updatedAt = createdAt.minusSeconds(1);

        assertThrows(IllegalArgumentException.class,
                () -> Catalog.restore(
                        Id.generate(),
                        new Text("Main catalog"),
                        createdAt,
                        updatedAt
                )
        );
    }
}
