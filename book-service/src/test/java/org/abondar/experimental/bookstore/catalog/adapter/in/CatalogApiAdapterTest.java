package org.abondar.experimental.bookstore.catalog.adapter.in;

import org.abondar.experimental.bookstore.adapter.in.generated.bookservice.model.CatalogResponse;
import org.abondar.experimental.bookstore.adapter.in.generated.bookservice.model.CatalogWithBooksResponse;
import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.book.domain.Isbn;
import org.abondar.experimental.bookstore.catalog.application.exception.CatalogAlreadyExistsException;
import org.abondar.experimental.bookstore.catalog.application.exception.CatalogNotFoundException;
import org.abondar.experimental.bookstore.catalog.application.port.in.DeleteCatalogUseCase;
import org.abondar.experimental.bookstore.catalog.application.port.in.GetCatalogUseCase;
import org.abondar.experimental.bookstore.catalog.application.port.in.GetCatalogWithBooksUseCase;
import org.abondar.experimental.bookstore.catalog.application.port.in.SaveCatalogUseCase;
import org.abondar.experimental.bookstore.catalog.application.port.in.UpdateCatalogNameUseCase;
import org.abondar.experimental.bookstore.catalog.domain.Catalog;
import org.abondar.experimental.bookstore.catalog.domain.CatalogWithBooks;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.common.Text;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogApiAdapterTest {

    @Mock
    private SaveCatalogUseCase saveCatalogUseCase;
    @Mock
    private UpdateCatalogNameUseCase updateCatalogNameUseCase;
    @Mock
    private GetCatalogUseCase getCatalogUseCase;
    @Mock
    private GetCatalogWithBooksUseCase getCatalogWithBooksUseCase;
    @Mock
    private DeleteCatalogUseCase deleteCatalogUseCase;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client =
                RestTestClient.bindToController(
                                new CatalogApiAdapter(
                                        saveCatalogUseCase,
                                        updateCatalogNameUseCase,
                                        getCatalogUseCase,
                                        getCatalogWithBooksUseCase,
                                        deleteCatalogUseCase))
                        .configureServer(server -> server.setControllerAdvice(new CatalogApiExceptionHandler()))
                        .build();
    }

    @Test
    void registerCatalog() {
        var response =
                client
                        .post()
                        .uri("/api/v1/catalogs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"name\":\"Main catalog\"}")
                        .exchange()
                        .expectStatus()
                        .isCreated()
                        .expectBody(CatalogResponse.class)
                        .returnResult()
                        .getResponseBody();

        var captor = ArgumentCaptor.forClass(Catalog.class);
        verify(saveCatalogUseCase).saveCatalog(captor.capture());
        assertEquals(new Text("Main catalog"), captor.getValue().getName());
        assertNotNull(response);
        assertEquals(captor.getValue().getId().value(), response.getId());
        assertNotNull(response.getCreatedAt());
        assertNotNull(response.getUpdatedAt());
    }

    @Test
    void registerCatalogRejectsDuplicateName() {
        doThrow(new CatalogAlreadyExistsException("Main catalog"))
                .when(saveCatalogUseCase)
                .saveCatalog(any(Catalog.class));

        client
                .post()
                .uri("/api/v1/catalogs")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"Main catalog\"}")
                .exchange()
                .expectStatus()
                .isEqualTo(409);
    }

    @Test
    void registerCatalogMapsDatabaseConflict() {
        doThrow(new DataIntegrityViolationException("Duplicate catalog name"))
                .when(saveCatalogUseCase)
                .saveCatalog(any(Catalog.class));

        client
                .post()
                .uri("/api/v1/catalogs")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"Main catalog\"}")
                .exchange()
                .expectStatus()
                .isEqualTo(409);
    }

    @Test
    void registerCatalogRejectsBlankName() {
        client
                .post()
                .uri("/api/v1/catalogs")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"   \"}")
                .exchange()
                .expectStatus()
                .isBadRequest();

        verify(saveCatalogUseCase, never()).saveCatalog(any(Catalog.class));
    }

    @Test
    void getCatalog() {
        var catalog = catalog();
        when(getCatalogUseCase.getCatalog(catalog.getId())).thenReturn(Optional.of(catalog));

        var response =
                client
                        .get()
                        .uri("/api/v1/catalogs/{id}", catalog.getId().value())
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(CatalogResponse.class)
                        .returnResult()
                        .getResponseBody();

        assertNotNull(response);
        assertEquals(catalog.getId().value(), response.getId());
        assertEquals(catalog.getName().value(), response.getName());
    }

    @Test
    void getMissingCatalog() {
        var id = UUID.randomUUID();
        when(getCatalogUseCase.getCatalog(new Id<Catalog>(id))).thenReturn(Optional.empty());

        client.get().uri("/api/v1/catalogs/{id}", id).exchange().expectStatus().isNotFound();
    }

    @Test
    void getCatalogWithBooks() {
        var catalog = catalog();
        var book =
                Book.create(
                        new Isbn("0306406152"), new Text("Book"), null, null, List.of(), catalog.getId());
        when(getCatalogWithBooksUseCase.getCatalogWithBooks(catalog.getId()))
                .thenReturn(Optional.of(new CatalogWithBooks(catalog, List.of(book))));

        var response =
                client
                        .get()
                        .uri("/api/v1/catalogs/{id}/books", catalog.getId().value())
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(CatalogWithBooksResponse.class)
                        .returnResult()
                        .getResponseBody();

        assertNotNull(response);
        assertEquals(catalog.getId().value(), response.getCatalog().getId());
        assertEquals(book.getId().value(), response.getBooks().getFirst().getId());
        assertEquals(catalog.getId().value(), response.getBooks().getFirst().getCatalogId());
        assertNull(response.getBooks().getFirst().getIssueYear());
    }

    @Test
    void getMissingCatalogWithBooks() {
        var id = UUID.randomUUID();
        when(getCatalogWithBooksUseCase.getCatalogWithBooks(new Id<Catalog>(id)))
                .thenReturn(Optional.empty());

        client.get().uri("/api/v1/catalogs/{id}/books", id).exchange().expectStatus().isNotFound();
    }

    @Test
    void renameCatalog() {
        var catalog = catalog();
        var renamed =
                Catalog.restore(
                        catalog.getId(),
                        new Text("Renamed catalog"),
                        catalog.getCreatedAt(),
                        catalog.getUpdatedAt());
        when(updateCatalogNameUseCase.updateCatalogName(catalog.getId(), "Renamed catalog"))
                .thenReturn(renamed);

        var response =
                client
                        .post()
                        .uri("/api/v1/catalogs/{id}/rename", catalog.getId().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"name\":\"Renamed catalog\"}")
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(CatalogResponse.class)
                        .returnResult()
                        .getResponseBody();

        verify(updateCatalogNameUseCase).updateCatalogName(catalog.getId(), "Renamed catalog");
        assertNotNull(response);
        assertEquals("Renamed catalog", response.getName());
        assertEquals(catalog.getId().value(), response.getId());
    }

    @Test
    void renameCatalogRejectsMissingName() {
        client
                .post()
                .uri("/api/v1/catalogs/{id}/rename", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .exchange()
                .expectStatus()
                .isBadRequest();

        verify(updateCatalogNameUseCase, never()).updateCatalogName(any(), any());
    }

    @Test
    void renameCatalogRejectsBlankName() {
        client
                .post()
                .uri("/api/v1/catalogs/{id}/rename", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"   \"}")
                .exchange()
                .expectStatus()
                .isBadRequest();

        verify(updateCatalogNameUseCase, never()).updateCatalogName(any(), any());
    }

    @Test
    void renameMissingCatalog() {
        var id = UUID.randomUUID();
        when(updateCatalogNameUseCase.updateCatalogName(new Id<Catalog>(id), "New name"))
                .thenThrow(new CatalogNotFoundException());

        client
                .post()
                .uri("/api/v1/catalogs/{id}/rename", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"New name\"}")
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    @Test
    void renameCatalogRejectsDuplicateName() {
        var id = UUID.randomUUID();
        when(updateCatalogNameUseCase.updateCatalogName(new Id<Catalog>(id), "Taken name"))
                .thenThrow(new CatalogAlreadyExistsException("Taken name"));

        client
                .post()
                .uri("/api/v1/catalogs/{id}/rename", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"Taken name\"}")
                .exchange()
                .expectStatus()
                .isEqualTo(409);
    }

    @Test
    void deleteCatalog() {
        var id = UUID.randomUUID();

        client.delete().uri("/api/v1/catalogs/{id}", id).exchange().expectStatus().isNoContent();

        verify(deleteCatalogUseCase).deleteCatalog(new Id<Catalog>(id));
    }

    @Test
    void deleteCatalogWithBooksReturnsConflict() {
        var id = UUID.randomUUID();
        doThrow(new DataIntegrityViolationException("Catalog is referenced by books"))
                .when(deleteCatalogUseCase)
                .deleteCatalog(new Id<Catalog>(id));

        client.delete().uri("/api/v1/catalogs/{id}", id).exchange().expectStatus().isEqualTo(409);
    }

    private Catalog catalog() {
        return Catalog.create(new Text("Main catalog"));
    }
}
