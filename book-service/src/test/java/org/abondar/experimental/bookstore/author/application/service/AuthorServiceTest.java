package org.abondar.experimental.bookstore.author.application.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import org.abondar.experimental.bookstore.author.application.exception.AuthorAlreadyExistsException;
import org.abondar.experimental.bookstore.author.application.exception.AuthorNotFoundException;
import org.abondar.experimental.bookstore.author.application.port.in.AddBooksToAuthorCommand;
import org.abondar.experimental.bookstore.author.application.port.in.UpdateAuthorCommand;
import org.abondar.experimental.bookstore.author.application.port.out.AuthorCacheRepository;
import org.abondar.experimental.bookstore.author.application.port.out.AuthorRepository;
import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.book.application.exception.BookNotFoundException;
import org.abondar.experimental.bookstore.book.application.port.out.BookCacheRepository;
import org.abondar.experimental.bookstore.book.application.port.out.BookRepository;
import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.book.domain.Isbn;
import org.abondar.experimental.bookstore.catalog.domain.Catalog;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.common.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
public class AuthorServiceTest {

  @Mock private AuthorRepository authorRepository;

  @Mock private BookRepository bookRepository;

  @Mock private AuthorCacheRepository authorCacheRepository;

  @Mock private BookCacheRepository bookCacheRepository;

  @InjectMocks private AuthorService authorService;

  @BeforeEach
  void beginTransactionSynchronization() {
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);
  }

  @AfterEach
  void endTransactionSynchronization() {
    TransactionSynchronizationManager.clearSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(false);
  }

  @Test
  public void saveAuthor() {
    var author = author();
    when(authorRepository.findByNameAndDob(
            author.getFirstName(), author.getLastName(), author.getDateOfBirth()))
        .thenReturn(Optional.empty());

    authorService.saveAuthor(author);

    verify(authorRepository).save(author);
    verify(authorCacheRepository).save(author);
  }

  @Test
  public void saveAuthorAlreadyExists() {
    var author = author();
    when(authorRepository.findByNameAndDob(
            author.getFirstName(), author.getLastName(), author.getDateOfBirth()))
        .thenReturn(Optional.of(author));

    assertThrows(AuthorAlreadyExistsException.class, () -> authorService.saveAuthor(author));

    verify(authorRepository, never()).save(author);
    verifyNoInteractions(authorCacheRepository);
  }

  @Test
  public void saveAuthorWithBooksAddsAuthorToImmutableBooks() {
    var author = author();
    var existingAuthorId = Id.<Author>generate();
    var firstBook =
        Book.create(new Isbn("0306406152"), new Text("First book"), null, null, List.of(), null);
    var secondBook =
        Book.create(
            new Isbn("9780134685991"),
            new Text("Second book"),
            null,
            null,
            List.of(existingAuthorId),
            null);
    when(authorRepository.findByNameAndDob(
            author.getFirstName(), author.getLastName(), author.getDateOfBirth()))
        .thenReturn(Optional.empty());
    var firstSavedBook = firstBook.withAuthor(author.getId());
    var secondSavedBook = secondBook.withAuthor(author.getId());
    when(bookRepository.findByAuthor(author.getId()))
        .thenReturn(List.of(firstSavedBook, secondSavedBook));

    var result = authorService.saveAuthorWithBooks(author, List.of(firstBook, secondBook));
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(TransactionSynchronization::afterCommit);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Book>> booksCaptor = ArgumentCaptor.forClass(List.class);
    verify(authorRepository).save(author);
    verify(bookRepository).saveMany(booksCaptor.capture());
    verify(bookCacheRepository).deleteByIds(List.of(firstBook.getId(), secondBook.getId()));
    verify(bookCacheRepository).saveMany(booksCaptor.getValue());
    var savedBooks = booksCaptor.getValue();
    assertAll(
        () -> assertEquals(List.of(), firstBook.getAuthorIds()),
        () -> assertEquals(List.of(existingAuthorId), secondBook.getAuthorIds()),
        () -> assertEquals(List.of(author.getId()), savedBooks.get(0).getAuthorIds()),
        () ->
            assertEquals(
                List.of(existingAuthorId, author.getId()), savedBooks.get(1).getAuthorIds()),
        () -> assertEquals(firstBook.getId(), savedBooks.get(0).getId()),
        () -> assertEquals(secondBook.getId(), savedBooks.get(1).getId()));
    assertSame(author, result.author());
    assertEquals(List.of(firstSavedBook, secondSavedBook), result.books());
  }

  @Test
  public void saveAuthorWithBooksUpdatesCacheAfterCommit() {
    var author = author();
    var book = Book.create(new Isbn("0306406152"), new Text("Book"), null, null, List.of(), null);
    when(bookRepository.findByAuthor(author.getId()))
        .thenReturn(List.of(book.withAuthor(author.getId())));

    authorService.saveAuthorWithBooks(author, List.of(book));

    verifyNoInteractions(authorCacheRepository, bookCacheRepository);
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(TransactionSynchronization::afterCommit);

    verify(authorCacheRepository).save(author);
    verify(bookCacheRepository).deleteByIds(List.of(book.getId()));
    verify(bookCacheRepository).saveMany(List.of(book.withAuthor(author.getId())));
  }

  @Test
  public void saveAuthorWithBooksCachesPersistedAuthorLinks() {
    var author = author();
    var existingAuthorId = Id.<Author>generate();
    var stored =
        Book.create(
            new Isbn("0306406152"), new Text("Book"), null, null, List.of(existingAuthorId), null);
    var supplied =
        Book.restore(
            stored.getId(),
            stored.getIsbn(),
            stored.getTitle(),
            null,
            null,
            List.of(),
            null,
            stored.getCreatedAt(),
            stored.getUpdatedAt());
    when(bookRepository.findById(stored.getId())).thenReturn(Optional.of(stored));
    when(bookRepository.findByAuthor(author.getId()))
        .thenReturn(List.of(stored.withAuthor(author.getId())));

    authorService.saveAuthorWithBooks(author, List.of(supplied));
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(TransactionSynchronization::afterCommit);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Book>> cachedBooks = ArgumentCaptor.forClass(List.class);
    verify(bookCacheRepository).saveMany(cachedBooks.capture());
    assertEquals(
        List.of(existingAuthorId, author.getId()),
        cachedBooks.getValue().getFirst().getAuthorIds());
  }

  @Test
  public void saveAuthorWithBooksDoesNotUpdateCacheOnRollback() {
    var author = author();
    var book = Book.create(new Isbn("0306406152"), new Text("Book"), null, null, List.of(), null);

    authorService.saveAuthorWithBooks(author, List.of(book));

    verifyNoInteractions(authorCacheRepository, bookCacheRepository);
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(
            synchronization ->
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

    verifyNoInteractions(authorCacheRepository, bookCacheRepository);
  }

  @Test
  public void saveAuthorWithBooksRejectsExistingAuthor() {
    var author = author();
    when(authorRepository.findByNameAndDob(
            author.getFirstName(), author.getLastName(), author.getDateOfBirth()))
        .thenReturn(Optional.of(author));

    var book = Book.create(new Isbn("0306406152"), new Text("Book"), null, null, List.of(), null);
    assertThrows(
        AuthorAlreadyExistsException.class,
        () -> authorService.saveAuthorWithBooks(author, List.of(book)));

    verify(authorRepository, never()).save(author);
    verifyNoInteractions(bookRepository);
  }

  @Test
  public void saveAuthorWithBooksPreservesOmittedExistingBookFields() {
    var author = author();
    var catalogId = Id.<Catalog>generate();
    var stored =
        Book.create(
            new Isbn("0306406152"),
            new Text("Stored title"),
            "Stored description",
            Year.of(1980),
            List.of(),
            catalogId);
    var supplied =
        Book.restore(
            stored.getId(),
            stored.getIsbn(),
            new Text("New title"),
            null,
            null,
            List.of(),
            null,
            stored.getCreatedAt(),
            stored.getUpdatedAt());
    when(bookRepository.findById(stored.getId())).thenReturn(Optional.of(stored));
    when(bookRepository.findByAuthor(author.getId()))
        .thenReturn(List.of(stored.withAuthor(author.getId())));

    authorService.saveAuthorWithBooks(author, List.of(supplied));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Book>> captor = ArgumentCaptor.forClass(List.class);
    verify(bookRepository).saveMany(captor.capture());
    var merged = captor.getValue().getFirst();
    assertEquals(new Text("New title"), merged.getTitle());
    assertEquals(stored.getDescription(), merged.getDescription());
    assertEquals(stored.getIssueYear(), merged.getIssueYear());
    assertEquals(stored.getCatalogId(), merged.getCatalogId());
    assertEquals(stored.getCreatedAt(), merged.getCreatedAt());
    assertEquals(List.of(author.getId()), merged.getAuthorIds());
  }

  @Test
  public void saveAuthorWithBooksRejectsEmptyBooks() {
    assertThrows(
        IllegalArgumentException.class,
        () -> authorService.saveAuthorWithBooks(author(), List.of()));

    verifyNoInteractions(authorRepository, bookRepository);
  }

  @Test
  public void addExistingBooksToAuthor() {
    var author = author();
    var otherAuthorId = Id.<Author>generate();
    var existing =
        Book.create(
            new Isbn("0306406152"), new Text("Existing"), null, null, List.of(otherAuthorId), null);
    var second =
        Book.create(new Isbn("9780134685991"), new Text("Second"), null, null, List.of(), null);
    var unchanged =
        Book.create(
            new Isbn("9780201633610"),
            new Text("Unchanged"),
            null,
            null,
            List.of(author.getId()),
            null);
    var persistedExisting = existing.withAuthor(author.getId());
    var persistedSecond = second.withAuthor(author.getId());
    when(authorRepository.findById(author.getId())).thenReturn(Optional.of(author));
    when(bookRepository.findByIds(List.of(existing.getId(), second.getId())))
        .thenReturn(List.of(second, existing));
    when(bookRepository.findByAuthor(author.getId()))
        .thenReturn(List.of(unchanged, persistedExisting, persistedSecond));

    var result =
        authorService.addBooksToAuthor(
            new AddBooksToAuthorCommand(author.getId(), List.of(existing.getId(), second.getId())));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Book>> savedCaptor = ArgumentCaptor.forClass(List.class);
    verify(bookRepository).addAuthorLinks(savedCaptor.capture());
    var linkedExisting =
        savedCaptor.getValue().stream()
            .filter(book -> book.getId().equals(existing.getId()))
            .findFirst()
            .orElseThrow();
    var linkedSecond =
        savedCaptor.getValue().stream()
            .filter(book -> book.getId().equals(second.getId()))
            .findFirst()
            .orElseThrow();
    assertEquals(List.of(otherAuthorId, author.getId()), linkedExisting.getAuthorIds());
    assertEquals(List.of(author.getId()), linkedSecond.getAuthorIds());
    assertEquals(List.of(unchanged, persistedExisting, persistedSecond), result.books());
    verifyNoInteractions(bookCacheRepository);

    TransactionSynchronizationManager.getSynchronizations()
        .forEach(TransactionSynchronization::afterCommit);

    verify(bookCacheRepository).deleteByIds(List.of(existing.getId(), second.getId()));
  }

  @Test
  public void addBooksToMissingAuthor() {
    var authorId = Id.<Author>generate();
    var bookId = Id.<Book>generate();
    when(authorRepository.findById(authorId)).thenReturn(Optional.empty());

    assertThrows(
        AuthorNotFoundException.class,
        () ->
            authorService.addBooksToAuthor(new AddBooksToAuthorCommand(authorId, List.of(bookId))));

    verifyNoInteractions(bookRepository, bookCacheRepository);
  }

  @Test
  public void addMissingExistingBookToAuthor() {
    var author = author();
    var existingBook =
        Book.create(new Isbn("0306406152"), new Text("Existing"), null, null, List.of(), null);
    var missingBookId = Id.<Book>generate();
    when(authorRepository.findById(author.getId())).thenReturn(Optional.of(author));
    when(bookRepository.findByIds(List.of(existingBook.getId(), missingBookId)))
        .thenReturn(List.of(existingBook));

    assertThrows(
        BookNotFoundException.class,
        () ->
            authorService.addBooksToAuthor(
                new AddBooksToAuthorCommand(
                    author.getId(), List.of(existingBook.getId(), missingBookId))));

    verify(bookRepository, never()).addAuthorLinks(anyList());
    verifyNoInteractions(bookCacheRepository);
  }

  @Test
  public void updateExistingAuthor() {
    var author = author();
    var command =
        new UpdateAuthorCommand(
            author.getId(), new Text("Augusta"), null, null, null, null, null, null);
    when(authorRepository.findById(author.getId())).thenReturn(Optional.of(author));

    var result = authorService.updateAuthor(command);

    var captor = ArgumentCaptor.forClass(Author.class);
    verify(authorRepository).update(captor.capture());
    var updated = captor.getValue();
    assertSame(updated, result);
    verify(authorCacheRepository).save(updated);
    assertEquals(new Text("Augusta"), updated.getFirstName());
    assertEquals(author.getLastName(), updated.getLastName());
    assertEquals(author.getSortName(), updated.getSortName());
    assertEquals(author.getDateOfBirth(), updated.getDateOfBirth());
    assertEquals(author.getCountryCode(), updated.getCountryCode());
    assertEquals(author.getCreatedAt(), updated.getCreatedAt());
    assertTrue(!updated.getUpdatedAt().isBefore(author.getUpdatedAt()));
  }

  @Test
  public void updateMissingAuthor() {
    var author = author();
    var command = new UpdateAuthorCommand(author.getId(), null, null, null, null, null, null, null);
    when(authorRepository.findById(author.getId())).thenReturn(Optional.empty());

    assertThrows(AuthorNotFoundException.class, () -> authorService.updateAuthor(command));

    verify(authorRepository, never()).update(any(Author.class));
  }

  @Test
  public void getAuthorById() {
    var author = author();
    when(authorRepository.findById(author.getId())).thenReturn(Optional.of(author));

    var result = authorService.getAuthorById(author.getId());

    assertSame(author, result.orElseThrow());
    verify(authorCacheRepository).save(author);
  }

  @Test
  public void getAuthorByIdFromCache() {
    var author = author();
    when(authorCacheRepository.findById(author.getId())).thenReturn(Optional.of(author));

    var result = authorService.getAuthorById(author.getId());

    assertSame(author, result.orElseThrow());
    verifyNoInteractions(authorRepository);
  }

  @Test
  public void getAuthorByIdWhenMissing() {
    Id<Author> id = Id.generate();
    when(authorRepository.findById(id)).thenReturn(Optional.empty());

    var result = authorService.getAuthorById(id);

    assertTrue(result.isEmpty());
    verify(authorCacheRepository, never()).save(any(Author.class));
  }

  @Test
  public void getAuthorWithBooksById() {
    var author = author();
    var book =
        Book.create(
            new Isbn("9780201633610"), new Text("Book"), null, null, List.of(author.getId()), null);
    when(authorRepository.findById(author.getId())).thenReturn(Optional.of(author));
    when(bookRepository.findByAuthor(author.getId())).thenReturn(List.of(book));

    var result = authorService.getAuthorWithBooksById(author.getId());

    assertAll(
        () -> assertSame(author, result.orElseThrow().author()),
        () -> assertEquals(List.of(book), result.orElseThrow().books()));
  }

  @Test
  public void getAuthorWithBooksWhenMissing() {
    Id<Author> id = Id.generate();
    when(authorRepository.findById(id)).thenReturn(Optional.empty());

    var result = authorService.getAuthorWithBooksById(id);

    assertTrue(result.isEmpty());
    verifyNoInteractions(bookRepository);
  }

  @Test
  public void getAuthorsBySortName() {
    var author = author();
    when(authorRepository.findBySortName(author.getSortName())).thenReturn(List.of(author));

    var result = authorService.getBySortName(author.getSortName());

    assertEquals(List.of(author), result);
  }

  @Test
  public void deleteAuthor() {
    Id<Author> id = Id.generate();
    var book = Book.create(new Isbn("0306406152"), new Text("Book"), null, null, List.of(id), null);
    when(bookRepository.findByAuthor(id)).thenReturn(List.of(book));

    authorService.deleteAuthor(id);

    verify(authorRepository).delete(id);
    verify(authorCacheRepository).delete(id);
    verify(bookCacheRepository).deleteByIds(List.of(book.getId()));
  }

  private Author author() {
    return Author.create(
        new Text("Ada"),
        new Text("Lovelace"),
        "Lovelace, Ada",
        LocalDate.of(1815, 12, 10),
        null,
        "GB",
        null);
  }
}
