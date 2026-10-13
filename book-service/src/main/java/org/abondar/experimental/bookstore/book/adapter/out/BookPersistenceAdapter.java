package org.abondar.experimental.bookstore.book.adapter.out;

import lombok.RequiredArgsConstructor;
import org.abondar.experimental.bookstore.adapter.out.persistence.generated.tables.records.BookRecord;
import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.book.application.port.out.BookRepository;
import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.book.domain.Isbn;
import org.abondar.experimental.bookstore.catalog.domain.Catalog;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.common.Text;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.abondar.experimental.bookstore.adapter.out.persistence.generated.Tables.BOOK;
import static org.abondar.experimental.bookstore.adapter.out.persistence.generated.tables.BookAuthor.BOOK_AUTHOR;


@Repository
@RequiredArgsConstructor
public class BookPersistenceAdapter implements BookRepository {

    private final DSLContext dslContext;

    @Override
    public void save(Book book) {
        var catalogId = book.getCatalogId() == null
                ? null : book.getCatalogId().value();
        var issueYear = book.getIssueYear() == null
                ? null : book.getIssueYear().getValue();
        var createdAt = OffsetDateTime.ofInstant(book.getCreatedAt(), ZoneOffset.UTC);
        var updatedAt = OffsetDateTime.ofInstant(book.getUpdatedAt(), ZoneOffset.UTC);

        dslContext.transaction(configuration -> {
            var tx = DSL.using(configuration);

            tx.insertInto(BOOK)
                    .set(BOOK.ID, book.getId().value())
                    .set(BOOK.TITLE, book.getTitle().value())
                    .set(BOOK.ISBN, book.getIsbn().value())
                    .set(BOOK.DESCRIPTION, book.getDescription())
                    .set(BOOK.CATALOG_ID, catalogId)
                    .set(BOOK.ISSUE_YEAR, issueYear)
                    .set(BOOK.CREATED_AT, createdAt)
                    .set(BOOK.UPDATED_AT, updatedAt)
                    .onConflict(BOOK.ID)
                    .doUpdate()
                    .set(BOOK.ISBN, book.getIsbn().value())
                    .set(BOOK.TITLE, book.getTitle().value())
                    .set(BOOK.DESCRIPTION, book.getDescription())
                    .set(BOOK.ISSUE_YEAR, issueYear)
                    .set(BOOK.UPDATED_AT, updatedAt)
                    .set(BOOK.CATALOG_ID, catalogId)
                    .execute();

            tx.deleteFrom(BOOK_AUTHOR)
                    .where(BOOK_AUTHOR.BOOK_ID.eq(book.getId().value()))
                    .execute();

            for (int i = 0; i < book.getAuthorIds().size(); i++) {
                tx.insertInto(BOOK_AUTHOR)
                        .set(BOOK_AUTHOR.BOOK_ID, book.getId().value())
                        .set(BOOK_AUTHOR.AUTHOR_ID, book.getAuthorIds().get(i).value())
                        .set(BOOK_AUTHOR.AUTHOR_ORDER, i + 1)
                        .execute();
            }
        });
    }

    @Override
    public void saveMany(List<Book> books) {
        if (books.isEmpty()) {
            return;
        }

        dslContext.transaction(configuration -> {
            var tx = DSL.using(configuration);

            var bookInsert = tx.insertInto(BOOK,
                    BOOK.ID, BOOK.ISBN, BOOK.TITLE, BOOK.DESCRIPTION,
                    BOOK.CATALOG_ID, BOOK.ISSUE_YEAR, BOOK.CREATED_AT, BOOK.UPDATED_AT);
            for (var book : books) {
                bookInsert.values(
                        book.getId().value(),
                        book.getIsbn().value(),
                        book.getTitle().value(),
                        book.getDescription(),
                        book.getCatalogId() == null ? null : book.getCatalogId().value(),
                        book.getIssueYear() == null ? null : book.getIssueYear().getValue(),
                        OffsetDateTime.ofInstant(book.getCreatedAt(), ZoneOffset.UTC),
                        OffsetDateTime.ofInstant(book.getUpdatedAt(), ZoneOffset.UTC)
                );
            }

            bookInsert
                    .onConflict(BOOK.ID)
                    .doUpdate()
                    .set(BOOK.ISBN, DSL.excluded(BOOK.ISBN))
                    .set(BOOK.TITLE, DSL.excluded(BOOK.TITLE))
                    .set(BOOK.DESCRIPTION, DSL.excluded(BOOK.DESCRIPTION))
                    .set(BOOK.ISSUE_YEAR, DSL.excluded(BOOK.ISSUE_YEAR))
                    .set(BOOK.UPDATED_AT, DSL.excluded(BOOK.UPDATED_AT))
                    .set(BOOK.CATALOG_ID, DSL.excluded(BOOK.CATALOG_ID))
                    .execute();

            tx.deleteFrom(BOOK_AUTHOR)
                    .where(BOOK_AUTHOR.BOOK_ID.in(
                            books.stream().map(book -> book.getId().value()).toList()))
                    .execute();

            var authorInsert = tx.insertInto(BOOK_AUTHOR,
                    BOOK_AUTHOR.BOOK_ID, BOOK_AUTHOR.AUTHOR_ID, BOOK_AUTHOR.AUTHOR_ORDER);
            boolean hasAuthors = false;
            for (var book : books) {
                for (int i = 0; i < book.getAuthorIds().size(); i++) {
                    authorInsert.values(book.getId().value(), book.getAuthorIds().get(i).value(), i + 1);
                    hasAuthors = true;
                }
            }
            if (hasAuthors) {
                authorInsert.execute();
            }
        });
    }

    @Override
    public Optional<Book> findById(Id<Book> id) {
        return dslContext.selectFrom(BOOK)
                .where(BOOK.ID.eq(id.value()))
                .fetchOptional()
                .map(this::toBook);
    }

    @Override
    public Optional<Book> findByIsbn(Isbn isbn) {
        return dslContext.selectFrom(BOOK)
                .where(BOOK.ISBN.eq(isbn.value()))
                .fetchOptional()
                .map(this::toBook);
    }

    @Override
    public List<Book> findByAuthor(Id<Author> authorId) {
        return dslContext.select(BOOK.asterisk())
                .from(BOOK)
                .join(BOOK_AUTHOR).on(BOOK_AUTHOR.BOOK_ID.eq(BOOK.ID))
                .where(BOOK_AUTHOR.AUTHOR_ID.eq(authorId.value()))
                .fetch()
                .map(record -> toBook(record.into(BOOK)));
    }

    @Override
    public List<Id<Book>> findIdsByAuthorId(Id<Author> authorId) {
        return dslContext.select(BOOK_AUTHOR.BOOK_ID)
                .from(BOOK_AUTHOR)
                .where(BOOK_AUTHOR.AUTHOR_ID.eq(authorId.value()))
                .fetch(BOOK_AUTHOR.BOOK_ID)
                .stream()
                .map(uuid -> new Id<Book>(uuid))
                .toList();
    }

    @Override
    public List<Book> findByCatalogId(Id<Catalog> catalogId) {
        return dslContext.selectFrom(BOOK)
                .where(BOOK.CATALOG_ID.eq(catalogId.value()))
                .fetch()
                .map(this::toBook);
    }

    @Override
    public void delete(Id<Book> id) {
        dslContext.delete(BOOK)
                .where(BOOK.ID.eq(id.value()))
                .execute();
    }


    private Book toBook(BookRecord record) {
        var authorIds = dslContext.select(BOOK_AUTHOR.AUTHOR_ID)
                .from(BOOK_AUTHOR)
                .where(BOOK_AUTHOR.BOOK_ID.eq(record.getId()))
                .orderBy(BOOK_AUTHOR.AUTHOR_ORDER.asc())
                .fetch(BOOK_AUTHOR.AUTHOR_ID)
                .stream()
                .map(uuid -> new Id<Author>(uuid))
                .toList();


        return Book.restore(
                new Id<>(record.getId()),
                new Isbn(record.getIsbn()),
                new Text(record.getTitle()),
                record.getDescription(),
                record.getIssueYear() == null ? null : Year.of(record.getIssueYear()),
                authorIds,
                record.getCatalogId() == null ? null : new Id<>(record.getCatalogId()),
                record.getCreatedAt().toInstant(),
                record.getUpdatedAt().toInstant()
        );
    }
}
