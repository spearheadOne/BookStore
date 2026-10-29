package org.abondar.experimental.bookstore.author.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.abondar.experimental.bookstore.author.application.exception.AuthorAlreadyExistsException;
import org.abondar.experimental.bookstore.author.application.exception.AuthorNotFoundException;
import org.abondar.experimental.bookstore.author.application.port.in.AddBooksToAuthorCommand;
import org.abondar.experimental.bookstore.author.application.port.in.AddBooksToAuthorUseCase;
import org.abondar.experimental.bookstore.author.application.port.in.DeleteAuthorUseCase;
import org.abondar.experimental.bookstore.author.application.port.in.GetAuthorByIdUseCase;
import org.abondar.experimental.bookstore.author.application.port.in.GetAuthorWithBooksByIdUseCase;
import org.abondar.experimental.bookstore.author.application.port.in.GetAuthorsBySortNameUseCase;
import org.abondar.experimental.bookstore.author.application.port.in.SaveAuthorUseCase;
import org.abondar.experimental.bookstore.author.application.port.in.SaveAuthorWithBooksUseCase;
import org.abondar.experimental.bookstore.author.application.port.in.UpdateAuthorCommand;
import org.abondar.experimental.bookstore.author.application.port.in.UpdateAuthorUseCase;
import org.abondar.experimental.bookstore.author.application.port.out.AuthorCacheRepository;
import org.abondar.experimental.bookstore.author.application.port.out.AuthorRepository;
import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.author.domain.AuthorWithBooks;
import org.abondar.experimental.bookstore.book.application.exception.BookNotFoundException;
import org.abondar.experimental.bookstore.book.application.port.out.BookCacheRepository;
import org.abondar.experimental.bookstore.book.application.port.out.BookRepository;
import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.common.Id;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class AuthorService
    implements AddBooksToAuthorUseCase,
        SaveAuthorUseCase,
        SaveAuthorWithBooksUseCase,
        UpdateAuthorUseCase,
        GetAuthorByIdUseCase,
        GetAuthorWithBooksByIdUseCase,
        GetAuthorsBySortNameUseCase,
        DeleteAuthorUseCase {

  private final AuthorRepository authorRepository;

  private final BookRepository bookRepository;

  private final AuthorCacheRepository authorCacheRepository;

  private final BookCacheRepository bookCacheRepository;

  @Override
  public void saveAuthor(Author author) {
    if (authorExists(author)) {
      throw new AuthorAlreadyExistsException();
    }

    authorRepository.save(author);
    authorCacheRepository.save(author);
  }

  @Override
  @Transactional
  public AuthorWithBooks saveAuthorWithBooks(Author author, List<Book> books) {
    if (books == null || books.isEmpty()) {
      throw new IllegalArgumentException("At least one book is required");
    }
    if (authorExists(author)) {
      throw new AuthorAlreadyExistsException();
    }

    var booksWithAuthor =
        books.stream().map(book -> prepareBookForAuthor(book).withAuthor(author.getId())).toList();

    authorRepository.save(author);
    bookRepository.saveMany(booksWithAuthor);

    var savedBooks = bookRepository.findByAuthor(author.getId());
    var bookIds = savedBooks.stream().map(Book::getId).toList();
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            authorCacheRepository.save(author);
            bookCacheRepository.deleteByIds(bookIds);
            bookCacheRepository.saveMany(savedBooks);
          }
        });

    return new AuthorWithBooks(author, savedBooks);
  }

  @Override
  @Transactional
  public AuthorWithBooks addBooksToAuthor(AddBooksToAuthorCommand command) {
    var author =
        authorRepository
            .findById(command.authorId())
            .orElseThrow(() -> new AuthorNotFoundException(command.authorId()));
    var existingBooks = bookRepository.findByIds(command.bookIds());
    var foundIds = existingBooks.stream().map(Book::getId).collect(Collectors.toSet());
    for (var bookId : command.bookIds()) {
      if (!foundIds.contains(bookId)) {
        throw new BookNotFoundException(bookId);
      }
    }

    var booksToLink =
        existingBooks.stream().map(book -> book.withAuthor(command.authorId())).toList();
    bookRepository.addAuthorLinks(booksToLink);
    var savedBooks = bookRepository.findByAuthor(command.authorId());

    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            bookCacheRepository.deleteByIds(command.bookIds());
          }
        });

    return new AuthorWithBooks(author, savedBooks);
  }

  @Override
  public Author updateAuthor(UpdateAuthorCommand authorCommand) {
    var existing =
        authorRepository
            .findById(authorCommand.id())
            .orElseThrow(() -> new AuthorNotFoundException(authorCommand.id()));

    var now = Instant.now();

    var updated =
        Author.restore(
            authorCommand.id(),
            authorCommand.firstName() != null ? authorCommand.firstName() : existing.getFirstName(),
            authorCommand.lastName() != null ? authorCommand.lastName() : existing.getLastName(),
            authorCommand.sortName() != null ? authorCommand.sortName() : existing.getSortName(),
            authorCommand.dateOfBirth() != null
                ? authorCommand.dateOfBirth()
                : existing.getDateOfBirth(),
            authorCommand.dateOfDeath() != null
                ? authorCommand.dateOfDeath()
                : existing.getDateOfDeath(),
            authorCommand.countryCode() != null
                ? authorCommand.countryCode()
                : existing.getCountryCode(),
            authorCommand.biography() != null ? authorCommand.biography() : existing.getBiography(),
            existing.getCreatedAt(),
            now.isAfter(existing.getUpdatedAt()) ? now : existing.getUpdatedAt());

    authorRepository.update(updated);
    authorCacheRepository.save(updated);
    return updated;
  }

  @Override
  public Optional<Author> getAuthorById(Id<Author> id) {
    var author = authorCacheRepository.findById(id);
    if (author.isPresent()) {
      return author;
    }

    author = authorRepository.findById(id);
    author.ifPresent(authorCacheRepository::save);

    return author;
  }

  @Override
  public Optional<AuthorWithBooks> getAuthorWithBooksById(Id<Author> id) {
    return getAuthorById(id)
        .map(author -> new AuthorWithBooks(author, bookRepository.findByAuthor(id)));
  }

  @Override
  public List<Author> getBySortName(String sortName) {
    return authorRepository.findBySortName(sortName);
  }

  @Override
  public void deleteAuthor(Id<Author> id) {
    var bookIds = bookRepository.findByAuthor(id).stream().map(Book::getId).toList();
    authorRepository.delete(id);
    authorCacheRepository.delete(id);
    bookCacheRepository.deleteByIds(bookIds);
  }

  private boolean authorExists(Author author) {
    return authorRepository
        .findByNameAndDob(author.getFirstName(), author.getLastName(), author.getDateOfBirth())
        .isPresent();
  }

  private Book prepareBookForAuthor(Book supplied) {
    var existing = bookRepository.findById(supplied.getId());
    if (existing.isEmpty()) {
      return supplied;
    }

    var stored = existing.get();
    return Book.restore(
        supplied.getId(),
        supplied.getIsbn(),
        supplied.getTitle(),
        supplied.getDescription() != null ? supplied.getDescription() : stored.getDescription(),
        supplied.getIssueYear() != null ? supplied.getIssueYear() : stored.getIssueYear(),
        supplied.getAuthorIds(),
        supplied.getCatalogId() != null ? supplied.getCatalogId() : stored.getCatalogId(),
        stored.getCreatedAt(),
        stored.getUpdatedAt());
  }
}
