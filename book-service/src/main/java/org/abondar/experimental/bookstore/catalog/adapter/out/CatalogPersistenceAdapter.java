package org.abondar.experimental.bookstore.catalog.adapter.out;

import lombok.RequiredArgsConstructor;
import org.abondar.experimental.bookstore.adapter.out.persistence.generated.tables.records.CatalogRecord;
import org.abondar.experimental.bookstore.catalog.application.exception.CatalogNotFoundException;
import org.abondar.experimental.bookstore.catalog.application.port.out.CatalogRepository;
import org.abondar.experimental.bookstore.catalog.domain.Catalog;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.common.Text;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.abondar.experimental.bookstore.adapter.out.persistence.generated.tables.Catalog.CATALOG;

@Repository
@RequiredArgsConstructor
public class CatalogPersistenceAdapter implements CatalogRepository {

    private final DSLContext dslContext;

    @Override
    public void save(Catalog catalog) {
        var createdAt = OffsetDateTime.ofInstant(catalog.getCreatedAt(), ZoneOffset.UTC);
        var updatedAt = OffsetDateTime.ofInstant(catalog.getUpdatedAt(), ZoneOffset.UTC);

        dslContext
                .insertInto(CATALOG)
                .set(CATALOG.ID, catalog.getId().value())
                .set(CATALOG.NAME, catalog.getName().value())
                .set(CATALOG.CREATED_AT, createdAt)
                .set(CATALOG.UPDATED_AT, updatedAt)
                .execute();
    }

    @Override
    public void update(Catalog catalog) {
        int affected =
                dslContext
                        .update(CATALOG)
                        .set(CATALOG.NAME, catalog.getName().value())
                        .set(
                                CATALOG.UPDATED_AT,
                                OffsetDateTime.ofInstant(catalog.getUpdatedAt(), ZoneOffset.UTC))
                        .where(CATALOG.ID.eq(catalog.getId().value()))
                        .execute();
        if (affected == 0) {
            throw new CatalogNotFoundException();
        }
    }

    @Override
    public Optional<Catalog> findById(Id<Catalog> catalogId) {
        return dslContext
                .selectFrom(CATALOG)
                .where(CATALOG.ID.eq(catalogId.value()))
                .fetchOptional()
                .map(this::toCatalog);
    }

    @Override
    public Optional<Catalog> findByName(String name) {
        return dslContext
                .selectFrom(CATALOG)
                .where(CATALOG.NAME.eq(name))
                .fetchOptional()
                .map(this::toCatalog);
    }

    @Override
    public void delete(Id<Catalog> catalogId) {
        dslContext.deleteFrom(CATALOG).where(CATALOG.ID.eq(catalogId.value())).execute();
    }

    private Catalog toCatalog(CatalogRecord catalogRecord) {
        return Catalog.restore(
                new Id<>(catalogRecord.getId()),
                new Text(catalogRecord.getName()),
                catalogRecord.getCreatedAt().toInstant(),
                catalogRecord.getUpdatedAt().toInstant());
    }
}
