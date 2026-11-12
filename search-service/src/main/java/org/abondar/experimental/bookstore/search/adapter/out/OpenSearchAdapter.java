package org.abondar.experimental.bookstore.search.adapter.out;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.abondar.experimental.bookstore.search.application.exception.IndexCreationFailedException;
import org.abondar.experimental.bookstore.search.application.exception.IndexDeletionFailedException;
import org.abondar.experimental.bookstore.search.application.port.out.IndexRepository;
import org.abondar.experimental.bookstore.search.config.OpenSearchProperties;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OpenSearchAdapter implements IndexRepository {

  private final OpenSearchProperties properties;
  private final OpenSearchClient client;

  @Override
  public void createIndexes() {
    for (var index : properties.getIndexNames()) {
      try {
        var response =
            client
                .indices()
                .create(
                    request ->
                        request
                            .index(index)
                            .settings(
                                settings ->
                                    settings
                                        .numberOfShards(properties.shards())
                                        .numberOfReplicas(properties.replicas()))
                            .mappings(typeMapping(index)));
        if (!response.acknowledged()) {
          throw new IndexCreationFailedException();
        }
        log.debug("Created index {}", index);
      } catch (OpenSearchException exception) {
        if (!"resource_already_exists_exception".equals(exception.error().type())) {
          throw new IndexCreationFailedException(index, exception);
        }
        log.debug("Index {} already exists", index);
      } catch (IOException exception) {
        throw new IndexCreationFailedException(index, exception);
      }
    }
  }

  private TypeMapping typeMapping(String index) {
    if (index.equals(properties.bookMetadataIndex())) {
      return TypeMapping.of(
          mapping ->
              mapping
                  .properties("bookId", p -> p.keyword(k -> k))
                  .properties("isbn", p -> p.keyword(k -> k))
                  .properties("title", p -> p.text(t -> t))
                  .properties("description", p -> p.text(t -> t))
                  .properties("catalogId", p -> p.keyword(k -> k)));
    }

    if (index.equals(properties.bookContentIndex())) {
      return TypeMapping.of(
          mapping ->
              mapping
                  .properties("bookId", p -> p.keyword(k -> k))
                  .properties("format", p -> p.keyword(k -> k))
                  .properties(
                      "originalFilename",
                      p -> p.text(text -> text.fields("keyword", field -> field.keyword(k -> k))))
                  .properties("content", p -> p.text(t -> t)));
    }

    if (index.equals(properties.catalogIndex())) {
      return TypeMapping.of(
          mapping ->
              mapping
                  .properties("catalogId", p -> p.keyword(k -> k))
                  .properties(
                      "name",
                      p -> p.text(text -> text.fields("keyword", field -> field.keyword(k -> k)))));
    }

    throw new IllegalArgumentException("Unknown index type");
  }

  @Override
  public void deleteIndexes() {
    try {
      var response =
          client
              .indices()
              .delete(
                  request ->
                      request
                          .index(properties.getIndexNames())
                          .ignoreUnavailable(true)
                          .allowNoIndices(true));
      if (!response.acknowledged()) {
        throw new IndexDeletionFailedException();
      }
      log.debug("Deleted configured indexes {}", properties.getIndexNames());
    } catch (IOException | OpenSearchException exception) {
      throw new IndexDeletionFailedException(exception);
    }
  }
}
