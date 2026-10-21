package org.abondar.experimental.bookstore.catalog.application.service;

import org.abondar.experimental.bookstore.book.application.port.out.BookRepository;
import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.book.domain.Isbn;
import org.abondar.experimental.bookstore.catalog.application.port.out.CatalogRepository;
import org.abondar.experimental.bookstore.catalog.domain.Catalog;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.common.Text;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CatalogServiceTest {

    @Mock
    private CatalogRepository catalogRepository;

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private CatalogService catalogService;

    @Test
    public void saveCatalog() {
        var catalog = Catalog.create(new Text("Main catalog"));

        catalogService.saveCatalog(catalog);

        verify(catalogRepository).save(catalog);
    }

    @Test
    public void rejectExistingCatalogName() {
        var catalog = Catalog.create(new Text("Main catalog"));
        when(catalogRepository.findByName(catalog.getName().value()))
                .thenReturn(Optional.of(catalog));

        assertThrows(IllegalArgumentException.class, () -> catalogService.saveCatalog(catalog));

        verify(catalogRepository, never()).save(catalog);
    }

    @Test
    public void updateCatalogName() {
        var catalog = Catalog.create(new Text("Before rename"));
        when(catalogRepository.findById(catalog.getId())).thenReturn(Optional.of(catalog));

        catalogService.updateCatalogName(catalog.getId(), "After rename");

        var saved = ArgumentCaptor.forClass(Catalog.class);
        verify(catalogRepository).update(saved.capture());
        assertAll(
                () -> assertEquals(catalog.getId(), saved.getValue().getId()),
                () -> assertEquals(new Text("After rename"), saved.getValue().getName()),
                () -> assertEquals(catalog.getCreatedAt(), saved.getValue().getCreatedAt()),
                () -> assertFalse(saved.getValue().getUpdatedAt().isBefore(catalog.getUpdatedAt()))
        );
    }

    @Test
    public void updatingToSameNameIsAllowed() {
        var catalog = Catalog.create(new Text("Same name"));
        when(catalogRepository.findById(catalog.getId())).thenReturn(Optional.of(catalog));
        when(catalogRepository.findByName(catalog.getName().value())).thenReturn(Optional.of(catalog));

        catalogService.updateCatalogName(catalog.getId(), catalog.getName().value());

        verify(catalogRepository).update(any(Catalog.class));
    }

    @Test
    public void updateCatalogRejectsNameUsedByAnotherCatalog() {
        var catalog = Catalog.create(new Text("Original"));
        var other = Catalog.create(new Text("Taken name"));
        when(catalogRepository.findById(catalog.getId())).thenReturn(Optional.of(catalog));
        when(catalogRepository.findByName(other.getName().value())).thenReturn(Optional.of(other));

        assertThrows(IllegalArgumentException.class,
                () -> catalogService.updateCatalogName(catalog.getId(), other.getName().value()));

        verify(catalogRepository, never()).update(any(Catalog.class));
    }

    @Test
    public void updateMissingCatalog() {
        Id<Catalog> id = Id.generate();

        assertThrows(IllegalArgumentException.class,
                () -> catalogService.updateCatalogName(id, "New name"));

        verify(catalogRepository, never()).update(any(Catalog.class));
    }

    @Test
    public void getCatalog() {
        var catalog = Catalog.create(new Text("Main catalog"));
        when(catalogRepository.findById(catalog.getId())).thenReturn(Optional.of(catalog));

        assertSame(catalog, catalogService.getCatalog(catalog.getId()).orElseThrow());
    }

    @Test
    public void getMissingCatalog() {
        Id<Catalog> id = Id.generate();

        assertTrue(catalogService.getCatalog(id).isEmpty());
    }

    @Test
    public void getCatalogWithBooks() {
        var catalog = Catalog.create(new Text("Main catalog"));
        var book = Book.create(new Isbn("0306406152"), new Text("Book"),
                null, null, List.of(), catalog.getId());
        when(catalogRepository.findById(catalog.getId())).thenReturn(Optional.of(catalog));
        when(bookRepository.findByCatalogId(catalog.getId())).thenReturn(List.of(book));

        var found = catalogService.getCatalogWithBooks(catalog.getId()).orElseThrow();

        assertAll(
                () -> assertSame(catalog, found.catalog()),
                () -> assertEquals(List.of(book), found.books())
        );
    }

    @Test
    public void getCatalogWithBooksWhenMissing() {
        Id<Catalog> id = Id.generate();

        assertTrue(catalogService.getCatalogWithBooks(id).isEmpty());
        verifyNoInteractions(bookRepository);
    }

    @Test
    public void deleteCatalog() {
        Id<Catalog> id = Id.generate();

        catalogService.deleteCatalog(id);

        verify(catalogRepository).delete(id);
    }
}
