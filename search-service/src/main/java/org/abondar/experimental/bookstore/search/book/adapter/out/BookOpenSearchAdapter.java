package org.abondar.experimental.bookstore.search.book.adapter.out;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.abondar.experimental.bookstore.search.book.application.exception.BookMetadataEventException;
import org.abondar.experimental.bookstore.search.book.application.exception.BookSearchException;
import org.abondar.experimental.bookstore.search.book.application.port.in.BookSearchContentQuery;
import org.abondar.experimental.bookstore.search.book.application.port.in.BookSearchMetadataQuery;
import org.abondar.experimental.bookstore.search.book.application.port.out.BookContentRepository;
import org.abondar.experimental.bookstore.search.book.application.port.out.BookMetadataRepository;
import org.abondar.experimental.bookstore.search.book.domain.BookContentDocument;
import org.abondar.experimental.bookstore.search.book.domain.BookMetadataDocument;
import org.abondar.experimental.bookstore.search.config.OpenSearchProperties;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.BuiltinScriptLanguage;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.stereotype.Component;

@Component
public class BookOpenSearchAdapter implements BookMetadataRepository, BookContentRepository {

  private final OpenSearchProperties properties;
  private final OpenSearchClient client;

  public BookOpenSearchAdapter(OpenSearchProperties properties, OpenSearchClient client) {
    this.properties = properties;
    this.client = client;
  }

  @Override
  public List<BookMetadataDocument> searchBookMetadata(BookSearchMetadataQuery query) {
    try {
      var metadataQuery = buildMetadataQuery(query);
      var response =
          client.search(
              request ->
                  request
                      .index(properties.bookMetadataIndex())
                      .query(metadataQuery)
                      .size(query.limit()),
              BookMetadataDocument.class);
      return response.hits().hits().stream().map(Hit::source).filter(Objects::nonNull).toList();
    } catch (IOException | OpenSearchException ex) {
      throw new BookSearchException(
          "Error finding books in index: " + properties.bookMetadataIndex(), ex);
    }
  }

  @Override
  public List<BookMetadataDocument> searchBookContent(BookSearchContentQuery query) {
    try {
      var contentQuery = buildContentQuery(query);
      var contentResponse =
          client.search(
              request ->
                  request
                      .index(properties.bookContentIndex())
                      .query(contentQuery)
                      .size(query.limit())
                      .source(source -> source.filter(filter -> filter.includes("bookId"))),
              BookContentDocument.class);
      var bookIds =
          contentResponse.hits().hits().stream()
              .map(Hit::source)
              .filter(Objects::nonNull)
              .map(BookContentDocument::bookId)
              .filter(Objects::nonNull)
              .distinct()
              .toList();
      if (bookIds.isEmpty()) {
        return List.of();
      }

      var metadataQuery = buildBookIdsQuery(bookIds);
      var metadataResponse =
          client.search(
              request ->
                  request
                      .index(properties.bookMetadataIndex())
                      .query(metadataQuery)
                      .size(query.limit()),
              BookMetadataDocument.class);
      return metadataResponse.hits().hits().stream()
          .map(Hit::source)
          .filter(Objects::nonNull)
          .toList();
    } catch (IOException | OpenSearchException ex) {
      throw new BookSearchException("Error finding book metadata by content", ex);
    }
  }

  private Query buildContentQuery(BookSearchContentQuery query) {
    var boolQuery = new BoolQuery.Builder();

    if (query.bookId() != null) {
      boolQuery.filter(q -> q.term(t -> t.field("bookId").value(FieldValue.of(query.bookId()))));
    }

    if (query.format() != null) {
      boolQuery.filter(q -> q.term(t -> t.field("format").value(FieldValue.of(query.format()))));
    }

    if (query.originalFilename() != null) {
      boolQuery.filter(
          q ->
              q.term(
                  t ->
                      t.field("originalFilename.keyword")
                          .value(FieldValue.of(query.originalFilename()))));
    }

    if (query.q() != null) {
      boolQuery.must(q -> q.match(m -> m.field("content").query(FieldValue.of(query.q()))));
    }

    return Query.of(q -> q.bool(boolQuery.build()));
  }

  private Query buildBookIdsQuery(List<String> bookIds) {
    return Query.of(
        q ->
            q.terms(
                t ->
                    t.field("bookId")
                        .terms(
                            values ->
                                values.value(bookIds.stream().map(FieldValue::of).toList()))));
  }

  private Query buildMetadataQuery(BookSearchMetadataQuery query) {
    var boolQuery = new BoolQuery.Builder();

    if (query.bookId() != null) {
      boolQuery.filter(q -> q.term(t -> t.field("bookId").value(FieldValue.of(query.bookId()))));
    }

    if (query.isbn() != null) {
      boolQuery.filter(q -> q.term(t -> t.field("isbn").value(FieldValue.of(query.isbn()))));
    }

    if (query.catalogId() != null) {
      boolQuery.filter(
          q -> q.term(t -> t.field("catalogId").value(FieldValue.of(query.catalogId()))));
    }

    if (query.q() != null) {
      boolQuery.must(q -> q.multiMatch(m -> m.fields("title", "description").query(query.q())));
    }

    return Query.of(q -> q.bool(boolQuery.build()));
  }

  @Override
  public void deleteBook(String bookId) {
    try {
      client.delete(request -> request.index(properties.bookMetadataIndex()).id(bookId));
      var deletions = new BulkRequest.Builder();
      for (var format : List.of("epub", "rtf", "pdf")) {
        deletions.operations(
            operation ->
                operation.delete(
                    delete ->
                        delete.index(properties.bookContentIndex()).id(bookId + ":" + format)));
      }
      var response = client.bulk(deletions.build());
      if (response.errors()) {
        throw new BookMetadataEventException(
            "Could not delete all content formats for book " + bookId);
      }
    } catch (IOException | OpenSearchException exception) {
      throw new BookMetadataEventException("Could not delete book metadata " + bookId, exception);
    }
  }

  @Override
  public void upsertBookMetadata(BookMetadataDocument bookMetadataDocument) {
    try {
      client.index(
          request ->
              request
                  .index(properties.bookMetadataIndex())
                  .id(bookMetadataDocument.bookId())
                  .document(bookMetadataDocument));

    } catch (IOException | OpenSearchException exception) {
      throw new BookMetadataEventException(
          "Could not index book metadata " + bookMetadataDocument.bookId(), exception);
    }
  }

  @Override
  public void unlinkCatalogId(String catalogId) {
    try {
      // The query must see prior writes.
      client.indices().refresh(request -> request.index(properties.bookMetadataIndex()));
      var response =
          client.updateByQuery(
              request ->
                  request
                      .index(properties.bookMetadataIndex())
                      .query(
                          query ->
                              query.term(
                                  term -> term.field("catalogId").value(FieldValue.of(catalogId))))
                      .script(
                          script ->
                              script.inline(
                                  inline ->
                                      inline
                                          .lang(
                                              language ->
                                                  language.builtin(BuiltinScriptLanguage.Painless))
                                          .source("ctx._source.catalogId = null"))));

      if (Boolean.TRUE.equals(response.timedOut())
          || !response.failures().isEmpty()
          || (response.versionConflicts() != null && response.versionConflicts() > 0)) {
        throw new BookMetadataEventException(
            "Could not unlink catalog " + catalogId + " from book metadata");
      }

    } catch (IOException | OpenSearchException exception) {
      throw new BookMetadataEventException(
          "Could not update documents in " + properties.bookMetadataIndex(), exception);
    }
  }

  @Override
  public void upsertBookContent(BookContentDocument bookContentDocument) {
    try {
      client.index(
          request ->
              request
                  .index(properties.bookContentIndex())
                  .id(bookContentDocument.bookId() + ":" + bookContentDocument.format())
                  .document(bookContentDocument));

    } catch (IOException | OpenSearchException exception) {
      throw new BookMetadataEventException(
          "Could not index book content " + bookContentDocument.bookId(), exception);
    }
  }
}
