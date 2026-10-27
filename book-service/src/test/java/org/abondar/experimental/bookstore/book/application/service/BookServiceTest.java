package org.abondar.experimental.bookstore.book.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Year;
import java.util.List;
import java.util.Optional;
import org.abondar.experimental.bookstore.book.application.exception.BookAlreadyExistsException;
import org.abondar.experimental.bookstore.book.application.exception.BookNotFoundException;
import org.abondar.experimental.bookstore.book.application.port.in.UpdateBookCommand;
import org.abondar.experimental.bookstore.book.application.port.out.BookCacheRepository;
import org.abondar.experimental.bookstore.book.application.port.out.BookRepository;
import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.book.domain.Isbn;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.common.Text;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class BookServiceTest {

  @Mock private BookRepository bookRepository;

  @Mock private BookCacheRepository bookCacheRepository;

  @InjectMocks private BookService bookService;

  @Test
  public void registerBook() {
    var book = Book.create(new Isbn("0306406152"), new Text("test"), null, null, List.of(), null);

    when(bookRepository.findByIsbn(book.getIsbn())).thenReturn(Optional.empty());
    bookService.registerBook(book);

    var writes = inOrder(bookRepository, bookCacheRepository);
    writes.verify(bookRepository).save(book);
    writes.verify(bookCacheRepository).save(book);
  }

  @Test
  public void registerBookAlready() {
    var book = Book.create(new Isbn("0306406152"), new Text("test"), null, null, List.of(), null);

    when(bookRepository.findByIsbn(book.getIsbn())).thenReturn(Optional.of(book));

    var exception =
        assertThrows(BookAlreadyExistsException.class, () -> bookService.registerBook(book));

    assertTrue(exception.getMessage().contains("already exists"));
    verify(bookRepository, never()).save(book);
    verifyNoInteractions(bookCacheRepository);
  }

  @Test
  public void getBookFromCache() {
    var book = Book.create(new Isbn("0306406152"), new Text("test"), null, null, List.of(), null);
    when(bookCacheRepository.findById(book.getId())).thenReturn(Optional.of(book));

    var result = bookService.getBook(book.getId());

    assertSame(book, result.orElseThrow());
    verify(bookRepository, never()).findById(book.getId());
  }

  @Test
  public void getBookFromRepositoryPopulatesCache() {
    var book = Book.create(new Isbn("0306406152"), new Text("test"), null, null, List.of(), null);

    Id<Book> id = book.getId();
    when(bookRepository.findById(id)).thenReturn(Optional.of(book));

    var result = bookService.getBook(id);

    assertSame(book, result.orElseThrow());
    verify(bookRepository).findById(id);
    verify(bookCacheRepository).save(book);
  }

  @Test
  public void getBookNotFound() {
    Id<Book> id = Id.generate();
    when(bookRepository.findById(id)).thenReturn(Optional.empty());

    var result = bookService.getBook(id);

    assertTrue(result.isEmpty());
    verify(bookRepository).findById(id);
    verify(bookCacheRepository, never()).save(any(Book.class));
  }

  @Test
  public void updateBookWhenFound() {
    var existingBook =
        Book.create(
            new Isbn("0306406152"),
            new Text("original title"),
            "Original description",
            Year.of(1980),
            List.of(),
            null);
    var command =
        new UpdateBookCommand(
            existingBook.getId(), null, new Text("updated title"), null, null, null, null);
    when(bookRepository.findById(existingBook.getId())).thenReturn(Optional.of(existingBook));

    var result = bookService.updateBook(command);
    var captor = ArgumentCaptor.forClass(Book.class);
    verify(bookRepository).update(captor.capture());

    var updated = captor.getValue();
    assertSame(updated, result);
    assertEquals(existingBook.getId(), updated.getId());
    assertEquals(existingBook.getIsbn(), updated.getIsbn());
    assertEquals(new Text("updated title"), updated.getTitle());
    assertEquals(existingBook.getDescription(), updated.getDescription());
    assertEquals(existingBook.getIssueYear(), updated.getIssueYear());
    assertEquals(existingBook.getAuthorIds(), updated.getAuthorIds());
    assertEquals(existingBook.getCreatedAt(), updated.getCreatedAt());
    assertTrue(!updated.getUpdatedAt().isBefore(existingBook.getUpdatedAt()));
    var writes = inOrder(bookRepository, bookCacheRepository);
    writes.verify(bookRepository).update(updated);
    writes.verify(bookCacheRepository).save(updated);
  }

  @Test
  public void updateBookWhenNotFound() {
    var book =
        Book.create(new Isbn("0306406152"), new Text("updated title"), null, null, List.of(), null);
    var command =
        new UpdateBookCommand(
            book.getId(),
            book.getIsbn(),
            book.getTitle(),
            book.getDescription(),
            book.getIssueYear(),
            book.getAuthorIds(),
            book.getCatalogId());
    when(bookRepository.findById(book.getId())).thenReturn(Optional.empty());

    var exception =
        assertThrows(BookNotFoundException.class, () -> bookService.updateBook(command));

    assertTrue(exception.getMessage().contains("does not exist"));
    verify(bookRepository, never()).update(any(Book.class));
    verifyNoInteractions(bookCacheRepository);
  }

  @Test
  public void updateBookDeletedAfterLookup() {
    var book =
        Book.create(new Isbn("0306406152"), new Text("Updated title"), null, null, List.of(), null);
    var command =
        new UpdateBookCommand(
            book.getId(), book.getIsbn(), book.getTitle(), null, null, List.of(), null);
    when(bookRepository.findById(book.getId())).thenReturn(Optional.of(book));
    doThrow(new BookNotFoundException(book.getId())).when(bookRepository).update(any(Book.class));

    assertThrows(BookNotFoundException.class, () -> bookService.updateBook(command));
    verifyNoInteractions(bookCacheRepository);
  }

  @Test
  public void deleteTest() {
    Id<Book> id = Id.generate();
    bookService.deleteBook(id);

    var writes = inOrder(bookRepository, bookCacheRepository);
    writes.verify(bookRepository).delete(id);
    writes.verify(bookCacheRepository).delete(id);
  }

  @Test
  public void deleteDoesNotEvictWhenRepositoryFails() {
    Id<Book> id = Id.generate();
    doThrow(new BookNotFoundException(id)).when(bookRepository).delete(id);

    assertThrows(BookNotFoundException.class, () -> bookService.deleteBook(id));

    verify(bookCacheRepository, never()).delete(id);
  }
}
