package org.abondar.experimental.bookstore.search.adapter.in.index;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.abondar.experimental.bookstore.search.application.exception.IndexCreationFailedException;
import org.abondar.experimental.bookstore.search.application.exception.IndexDeletionFailedException;
import org.abondar.experimental.bookstore.search.application.port.in.CreateIndexUseCase;
import org.abondar.experimental.bookstore.search.application.port.in.DeleteIndexUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.client.RestTestClient;

@ExtendWith(MockitoExtension.class)
class IndexApiAdapterTest {

  private static final String PATH = "/internal/api/v1/indexes";
  @Mock private CreateIndexUseCase createIndexUseCase;
  @Mock private DeleteIndexUseCase deleteIndexUseCase;
  private RestTestClient client;

  @BeforeEach
  void setUp() {
    client =
        RestTestClient.bindToController(new IndexApiAdapter(createIndexUseCase, deleteIndexUseCase))
            .configureServer(server -> server.setControllerAdvice(new IndexApiExceptionHandler()))
            .build();
  }

  @Test
  void createsIndexes() {
    client.post().uri(PATH).exchange().expectStatus().isCreated().expectBody().isEmpty();
    verify(createIndexUseCase).createIndexes();
    verifyNoInteractions(deleteIndexUseCase);
  }

  @Test
  void deletesIndexes() {
    client.delete().uri(PATH).exchange().expectStatus().isNoContent().expectBody().isEmpty();
    verify(deleteIndexUseCase).deleteIndexes();
    verifyNoInteractions(createIndexUseCase);
  }

  @Test
  void creationFailureReturnsServiceUnavailable() {
    doThrow(new IndexCreationFailedException()).when(createIndexUseCase).createIndexes();
    client.post().uri(PATH).exchange().expectStatus().isEqualTo(503).expectBody().isEmpty();
  }

  @Test
  void deletionFailureReturnsServiceUnavailable() {
    doThrow(new IndexDeletionFailedException()).when(deleteIndexUseCase).deleteIndexes();
    client.delete().uri(PATH).exchange().expectStatus().isEqualTo(503).expectBody().isEmpty();
  }

  @Test
  void rejectsUnsupportedMethod() {
    client.get().uri(PATH).exchange().expectStatus().isEqualTo(405);
    verifyNoInteractions(createIndexUseCase, deleteIndexUseCase);
  }
}
