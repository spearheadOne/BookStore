package org.abondar.experimental.bookstore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.abondar.experimental.bookstore.author.application.port.in.UpdateAuthorCommand;
import org.abondar.experimental.bookstore.author.application.port.out.AuthorCacheRepository;
import org.abondar.experimental.bookstore.author.application.port.out.AuthorRepository;
import org.abondar.experimental.bookstore.author.application.service.AuthorService;
import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.book.application.port.in.UpdateBookCommand;
import org.abondar.experimental.bookstore.book.application.port.out.BookCacheRepository;
import org.abondar.experimental.bookstore.book.application.port.out.BookEventRepository;
import org.abondar.experimental.bookstore.book.application.port.out.BookRepository;
import org.abondar.experimental.bookstore.book.application.service.BookService;
import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.book.domain.Isbn;
import org.abondar.experimental.bookstore.common.Text;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ConcurrentWritesTest extends BasePersistenceAdapterTest {

  @Autowired private BookRepository books;
  @Autowired private AuthorRepository authors;
  @Autowired private BookService bookService;
  @Autowired private AuthorService authorService;
  @Autowired private PlatformTransactionManager transactionManager;

  @MockitoBean private BookCacheRepository bookCacheRepository;
  @MockitoBean private AuthorCacheRepository authorCacheRepository;
  @MockitoBean private BookEventRepository bookEventRepository;

  @Test
  void concurrentBookPatchesPreserveBothChanges() throws Exception {
    var book =
        Book.create(
            new Isbn("9783161484100"), new Text("Original"), "Original", null, List.of(), null);
    books.save(book);
    var title =
        new UpdateBookCommand(
            book.getId(), null, new Text("Updated title"), null, null, null, null);
    var description =
        new UpdateBookCommand(book.getId(), null, null, "Updated description", null, null, null);

    runCompetingWrites(
        () -> bookService.updateBook(title), () -> bookService.updateBook(description));

    var saved = books.findById(book.getId()).orElseThrow();
    assertEquals(new Text("Updated title"), saved.getTitle());
    assertEquals("Updated description", saved.getDescription());
  }

  @Test
  void concurrentAuthorPatchesPreserveBothChanges() throws Exception {
    var author = author("Patch author");
    authors.save(author);
    var firstName =
        new UpdateAuthorCommand(
            author.getId(), new Text("Updated first"), null, null, null, null, null, null);
    var biography =
        new UpdateAuthorCommand(
            author.getId(), null, null, null, null, null, null, "Updated biography");

    runCompetingWrites(
        () -> authorService.updateAuthor(firstName), () -> authorService.updateAuthor(biography));

    var saved = authors.findById(author.getId()).orElseThrow();
    assertEquals(new Text("Updated first"), saved.getFirstName());
    assertEquals("Updated biography", saved.getBiography());
  }

  @Test
  void concurrentAuthorLinksGetDistinctConsecutivePositions() throws Exception {
    var first = author("First linked author");
    var second = author("Second linked author");
    authors.save(first);
    authors.save(second);
    var book =
        Book.create(
            new Isbn("9780134685991"), new Text("Linked book"), null, null, List.of(), null);
    books.save(book);

    runCompetingWrites(
        () -> books.addAuthorLinks(List.of(book.withAuthor(first.getId()))),
        () -> books.addAuthorLinks(List.of(book.withAuthor(second.getId()))));

    var saved = books.findById(book.getId()).orElseThrow();
    assertEquals(List.of(first.getId(), second.getId()), saved.getAuthorIds());
  }

  private Author author(String firstName) {
    return Author.create(
        new Text(firstName),
        new Text("Lock test"),
        null,
        LocalDate.of(1980, 1, 1),
        null,
        null,
        null);
  }

  private void runCompetingWrites(Runnable firstWrite, Runnable secondWrite) throws Exception {
    var firstHasWritten = new CountDownLatch(1);
    var allowCommit = new CountDownLatch(1);
    var secondStarted = new CountDownLatch(1);
    var transactions = new TransactionTemplate(transactionManager);
    try (var executor = Executors.newFixedThreadPool(2)) {
      try {
        var first =
            executor.submit(
                () ->
                    transactions.executeWithoutResult(
                        status -> {
                          firstWrite.run();
                          firstHasWritten.countDown();
                          await(allowCommit);
                        }));
        await(firstHasWritten);
        var second =
            executor.submit(
                () -> {
                  secondStarted.countDown();
                  secondWrite.run();
                });
        await(secondStarted);
        assertThrows(TimeoutException.class, () -> second.get(200, TimeUnit.MILLISECONDS));
        allowCommit.countDown();
        first.get(10, TimeUnit.SECONDS);
        second.get(10, TimeUnit.SECONDS);
      } finally {
        allowCommit.countDown();
      }
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      assertTrue(
          latch.await(10, TimeUnit.SECONDS), "Concurrent write did not reach its checkpoint");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError("Interrupted waiting for concurrent write", exception);
    }
  }
}
