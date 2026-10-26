package org.abondar.experimental.bookstore.catalog.application.adapter.out;

import org.abondar.experimental.bookstore.BaseAdapterTest;
import org.abondar.experimental.bookstore.catalog.adapter.out.CatalogPersistenceAdapter;
import org.abondar.experimental.bookstore.catalog.application.exception.CatalogNotFoundException;
import org.abondar.experimental.bookstore.catalog.domain.Catalog;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.common.Text;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.abondar.experimental.bookstore.adapter.out.persistence.generated.Tables.BOOK;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CatalogPersistenceAdapterTest extends BaseAdapterTest {

    @Autowired
    private CatalogPersistenceAdapter adapter;

    @Test
    public void saveAndFindCatalog() {
        var catalog = Catalog.create(new Text("Primary catalog"));

        adapter.save(catalog);

        var found = adapter.findById(catalog.getId()).orElseThrow();
        assertAll(
                () -> assertEquals(catalog.getId(), found.getId()),
                () -> assertEquals(catalog.getName(), found.getName()),
                () -> assertNotNull(found.getCreatedAt()),
                () -> assertNotNull(found.getUpdatedAt()),
                () ->
                        assertEquals(
                                catalog.getId(),
                                adapter.findByName(catalog.getName().value()).orElseThrow().getId()));
    }

    @Test
    public void findMissingCatalog() {
        assertAll(
                () -> assertTrue(adapter.findById(Id.generate()).isEmpty()),
                () -> assertTrue(adapter.findByName("Missing catalog").isEmpty()));
    }

    @Test
    public void updateCatalogKeepsCreationTime() {
        var catalog = Catalog.create(new Text("Before rename"));
        adapter.save(catalog);
        var originalCreatedAt = adapter.findById(catalog.getId()).orElseThrow().getCreatedAt();
        var updated =
                Catalog.restore(
                        catalog.getId(),
                        new Text("After rename"),
                        catalog.getCreatedAt(),
                        catalog.getUpdatedAt().plusSeconds(1));

        adapter.update(updated);

        var found = adapter.findById(catalog.getId()).orElseThrow();
        assertAll(
                () -> assertEquals(updated.getName(), found.getName()),
                () -> assertEquals(originalCreatedAt, found.getCreatedAt()),
                () -> assertTrue(found.getUpdatedAt().isAfter(originalCreatedAt)),
                () -> assertTrue(adapter.findByName("Before rename").isEmpty()));
    }

    @Test
    public void updateMissingCatalogDoesNotInsertIt() {
        var catalog = Catalog.create(new Text("Absent catalog"));

        assertThrows(CatalogNotFoundException.class, () -> adapter.update(catalog));
        assertTrue(adapter.findById(catalog.getId()).isEmpty());
    }

    @Test
    public void rejectsDuplicateCatalogName() {
        var first = Catalog.create(new Text("Duplicate catalog"));
        var second = Catalog.create(new Text("Duplicate catalog"));
        adapter.save(first);

        assertThrows(DataIntegrityViolationException.class, () -> adapter.save(second));

        assertTrue(adapter.findById(second.getId()).isEmpty());
    }

    @Test
    public void deleteCatalog() {
        var catalog = Catalog.create(new Text("Deletable catalog"));
        adapter.save(catalog);

        adapter.delete(catalog.getId());

        assertTrue(adapter.findById(catalog.getId()).isEmpty());
    }

    @Test
    public void deletingCatalogWithBooksDoesNotDeleteBooks() {
        var catalog = Catalog.create(new Text("Referenced catalog"));
        adapter.save(catalog);
        var bookId = Id.generate();
        dslContext
                .insertInto(BOOK)
                .set(BOOK.ID, bookId.value())
                .set(BOOK.ISBN, "catalog-delete-" + bookId.value())
                .set(BOOK.TITLE, "Book in catalog")
                .set(BOOK.CATALOG_ID, catalog.getId().value())
                .execute();

        assertThrows(DataIntegrityViolationException.class, () -> adapter.delete(catalog.getId()));

        assertAll(
                () -> assertTrue(adapter.findById(catalog.getId()).isPresent()),
                () -> assertEquals(1, dslContext.fetchCount(BOOK, BOOK.ID.eq(bookId.value()))));
    }
}
