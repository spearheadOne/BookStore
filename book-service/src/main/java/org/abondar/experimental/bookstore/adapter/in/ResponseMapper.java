package org.abondar.experimental.bookstore.adapter.in;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;
import org.abondar.experimental.bookstore.adapter.in.generated.bookservice.model.AuthorResponse;
import org.abondar.experimental.bookstore.adapter.in.generated.bookservice.model.AuthorWithBooksResponse;
import org.abondar.experimental.bookstore.adapter.in.generated.bookservice.model.BookResponse;
import org.abondar.experimental.bookstore.adapter.in.generated.bookservice.model.CatalogResponse;
import org.abondar.experimental.bookstore.adapter.in.generated.bookservice.model.CatalogWithBooksResponse;
import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.author.domain.AuthorWithBooks;
import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.catalog.domain.Catalog;
import org.abondar.experimental.bookstore.catalog.domain.CatalogWithBooks;
import org.abondar.experimental.bookstore.common.Id;

public final class ResponseMapper {

  private ResponseMapper() {}

  public static BookResponse toBookResponse(Book book) {
    return BookResponse.builder()
        .id(book.getId().value())
        .title(book.getTitle().value())
        .isbn(book.getIsbn().value())
        .description(book.getDescription())
        .authorIds(
            book.getAuthorIds().stream()
                .map(Id::value)
                .collect(Collectors.toCollection(LinkedHashSet::new)))
        .catalogId(book.getCatalogId() == null ? null : book.getCatalogId().value())
        .issueYear(book.getIssueYear() == null ? null : book.getIssueYear().getValue())
        .createdAt(toOffsetDate(book.getCreatedAt()))
        .updatedAt(toOffsetDate(book.getUpdatedAt()))
        .build();
  }

  public static AuthorWithBooksResponse toAuthorWithBooksResponse(AuthorWithBooks authorWithBooks) {
    return AuthorWithBooksResponse.builder()
        .author(toAuthorResponse(authorWithBooks.author()))
        .books(authorWithBooks.books().stream().map(ResponseMapper::toBookResponse).toList())
        .build();
  }

  public static AuthorResponse toAuthorResponse(Author author) {
    return AuthorResponse.builder()
        .id(author.getId().value())
        .firstName(author.getFirstName().value())
        .lastName(author.getLastName().value())
        .sortName(author.getSortName())
        .dateOfBirth(author.getDateOfBirth())
        .dateOfDeath(author.getDateOfDeath())
        .countryCode(author.getCountryCode())
        .biography(author.getBiography())
        .createdAt(toOffsetDate(author.getCreatedAt()))
        .updatedAt(toOffsetDate(author.getUpdatedAt()))
        .build();
  }

  public static CatalogResponse toCatalogResponse(Catalog catalog) {
    return CatalogResponse.builder()
        .id(catalog.getId().value())
        .name(catalog.getName().value())
        .createdAt(toOffsetDate(catalog.getCreatedAt()))
        .updatedAt(toOffsetDate(catalog.getUpdatedAt()))
        .build();
  }

  public static CatalogWithBooksResponse toCatalogWithBooksResponse(
      CatalogWithBooks catalogWithBooks) {
    return CatalogWithBooksResponse.builder()
        .catalog(toCatalogResponse(catalogWithBooks.catalog()))
        .books(catalogWithBooks.books().stream().map(ResponseMapper::toBookResponse).toList())
        .build();
  }

  private static OffsetDateTime toOffsetDate(Instant instantValue) {
    return OffsetDateTime.ofInstant(instantValue, ZoneOffset.UTC);
  }
}
