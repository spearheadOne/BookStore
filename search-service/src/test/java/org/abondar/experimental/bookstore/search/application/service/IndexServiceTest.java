package org.abondar.experimental.bookstore.search.application.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.abondar.experimental.bookstore.search.application.exception.IndexCreationFailedException;
import org.abondar.experimental.bookstore.search.application.exception.IndexDeletionFailedException;
import org.abondar.experimental.bookstore.search.application.port.out.IndexRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IndexServiceTest {

  @Mock private IndexRepository indexRepository;
  @InjectMocks private IndexService service;

  @Test
  void createIndexes() {
    service.createIndexes();
    verify(indexRepository).createIndexes();
    verifyNoMoreInteractions(indexRepository);
  }

  @Test
  void deleteIndexes() {
    service.deleteIndexes();
    verify(indexRepository).deleteIndexes();
    verifyNoMoreInteractions(indexRepository);
  }

  @Test
  void propagatesCreationFailure() {
    var failure = new IndexCreationFailedException();
    doThrow(failure).when(indexRepository).createIndexes();
    assertSame(failure, assertThrows(IndexCreationFailedException.class, service::createIndexes));
  }

  @Test
  void propagatesDeletionFailure() {
    var failure = new IndexDeletionFailedException();
    doThrow(failure).when(indexRepository).deleteIndexes();
    assertSame(failure, assertThrows(IndexDeletionFailedException.class, service::deleteIndexes));
  }
}
