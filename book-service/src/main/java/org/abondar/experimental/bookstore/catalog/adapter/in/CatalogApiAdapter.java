package org.abondar.experimental.bookstore.catalog.adapter.in;

import lombok.RequiredArgsConstructor;
import org.abondar.experimental.bookstore.adapter.in.ResponseMapper;
import org.abondar.experimental.bookstore.adapter.in.generated.bookservice.api.CatalogApi;
import org.abondar.experimental.bookstore.adapter.in.generated.bookservice.model.CatalogResponse;
import org.abondar.experimental.bookstore.adapter.in.generated.bookservice.model.CatalogWithBooksResponse;
import org.abondar.experimental.bookstore.adapter.in.generated.bookservice.model.RegisterCatalogRequest;
import org.abondar.experimental.bookstore.adapter.in.generated.bookservice.model.RenameCatalogRequest;
import org.abondar.experimental.bookstore.catalog.application.port.in.DeleteCatalogUseCase;
import org.abondar.experimental.bookstore.catalog.application.port.in.GetCatalogUseCase;
import org.abondar.experimental.bookstore.catalog.application.port.in.GetCatalogWithBooksUseCase;
import org.abondar.experimental.bookstore.catalog.application.port.in.SaveCatalogUseCase;
import org.abondar.experimental.bookstore.catalog.application.port.in.UpdateCatalogNameUseCase;
import org.abondar.experimental.bookstore.catalog.domain.Catalog;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.common.Text;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CatalogApiAdapter implements CatalogApi {

    private final SaveCatalogUseCase saveCatalogUseCase;

    private final UpdateCatalogNameUseCase updateCatalogUseCase;

    private final GetCatalogUseCase getCatalogUseCase;

    private final GetCatalogWithBooksUseCase getCatalogWithBooksUseCase;

    private final DeleteCatalogUseCase deleteCatalogUseCase;

    @Override
    public ResponseEntity<Void> deleteCatalog(UUID id) {
        deleteCatalogUseCase.deleteCatalog(new Id<>(id));
        return ResponseEntity.status(204).build();
    }

    @Override
    public ResponseEntity<CatalogResponse> getCatalog(UUID id) {
        var res = getCatalogUseCase.getCatalog(new Id<>(id));
        return res.map(catalog -> ResponseEntity.ok(ResponseMapper.toCatalogResponse(catalog)))
                .orElseGet(() -> ResponseEntity.status(404).build());
    }

    @Override
    public ResponseEntity<CatalogWithBooksResponse> getCatalogWithBooks(UUID id) {
        var res = getCatalogWithBooksUseCase.getCatalogWithBooks(new Id<>(id));
        return res.map(catalog -> ResponseEntity.ok(ResponseMapper.toCatalogWithBooksResponse(catalog)))
                .orElseGet(() -> ResponseEntity.status(404).build());
    }

    @Override
    public ResponseEntity<CatalogResponse> registerCatalog(
            RegisterCatalogRequest registerCatalogRequest) {
        var catalog = Catalog.create(new Text(registerCatalogRequest.getName()));

        saveCatalogUseCase.saveCatalog(catalog);

        return ResponseEntity.status(201).body(ResponseMapper.toCatalogResponse(catalog));
    }

    @Override
    public ResponseEntity<CatalogResponse> updateCatalogName(
            UUID id, RenameCatalogRequest renameCatalogRequest) {
        var name = renameCatalogRequest.getName();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Catalog name must not be blank");
        }

        var updated = updateCatalogUseCase.updateCatalogName(new Id<>(id), name);
        return ResponseEntity.ok(ResponseMapper.toCatalogResponse(updated));
    }
}
