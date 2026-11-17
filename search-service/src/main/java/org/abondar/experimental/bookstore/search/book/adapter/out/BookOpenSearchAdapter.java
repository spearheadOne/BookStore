package org.abondar.experimental.bookstore.search.book.adapter.out;

import java.io.IOException;
import java.util.List;
import org.abondar.experimental.bookstore.search.book.application.exception.BookEventException;
import org.abondar.experimental.bookstore.search.book.application.port.out.BookContentRepository;
import org.abondar.experimental.bookstore.search.book.application.port.out.BookMetadataRepository;
import org.abondar.experimental.bookstore.search.book.domain.BookContentDocument;
import org.abondar.experimental.bookstore.search.book.domain.BookMetadata;
import org.abondar.experimental.bookstore.search.config.OpenSearchProperties;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.BuiltinScriptLanguage;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.core.BulkRequest;
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
        throw new BookEventException("Could not delete all content formats for book " + bookId);
      }
    } catch (IOException | OpenSearchException exception) {
      throw new BookEventException("Could not delete book metadata " + bookId, exception);
    }
  }

  @Override
  public void upsertBookMetadata(BookMetadata bookMetadata) {
    try {
      client.index(
          request ->
              request
                  .index(properties.bookMetadataIndex())
                  .id(bookMetadata.bookId())
                  .document(bookMetadata));

    } catch (IOException | OpenSearchException exception) {
      throw new BookEventException(
          "Could not index book metadata " + bookMetadata.bookId(), exception);
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
        throw new BookEventException(
            "Could not unlink catalog " + catalogId + " from book metadata");
      }

    } catch (IOException | OpenSearchException exception) {
      throw new BookEventException(
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
      throw new BookEventException(
          "Could not index book content " + bookContentDocument.bookId(), exception);
    }
  }
}
