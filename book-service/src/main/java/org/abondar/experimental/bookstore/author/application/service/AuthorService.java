package org.abondar.experimental.bookstore.author.application.service;

import lombok.RequiredArgsConstructor;
import org.abondar.experimental.bookstore.author.application.exception.AuthorAlreadyExistsException;
import org.abondar.experimental.bookstore.author.application.exception.AuthorNotFoundException;
import org.abondar.experimental.bookstore.author.application.port.in.DeleteAuthorUseCase;
import org.abondar.experimental.bookstore.author.application.port.in.GetAuthorByIdUseCase;
import org.abondar.experimental.bookstore.author.application.port.in.GetAuthorWithBooksByIdUseCase;
import org.abondar.experimental.bookstore.author.application.port.in.GetAuthorsBySortNameUseCase;
import org.abondar.experimental.bookstore.author.application.port.in.SaveAuthorUseCase;
import org.abondar.experimental.bookstore.author.application.port.in.SaveAuthorWithBooksUseCase;
import org.abondar.experimental.bookstore.author.application.port.in.UpdateAuthorCommand;
import org.abondar.experimental.bookstore.author.application.port.in.UpdateAuthorUseCase;
import org.abondar.experimental.bookstore.author.application.port.out.AuthorRepository;
import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.author.domain.AuthorWithBooks;
import org.abondar.experimental.bookstore.book.application.port.out.BookRepository;
import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.common.Id;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthorService
        implements SaveAuthorUseCase,
        SaveAuthorWithBooksUseCase,
        UpdateAuthorUseCase,
        GetAuthorByIdUseCase,
        GetAuthorWithBooksByIdUseCase,
        GetAuthorsBySortNameUseCase,
        DeleteAuthorUseCase {

    private final AuthorRepository authorRepository;

    private final BookRepository bookRepository;

    @Override
    public void saveAuthor(Author author) {
        if (authorExists(author)) {
            throw new AuthorAlreadyExistsException();
        }

        authorRepository.save(author);
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
        return new AuthorWithBooks(author, bookRepository.findByAuthor(author.getId()));
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
        return updated;
    }

    @Override
    public Optional<Author> getAuthorById(Id<Author> id) {
        return authorRepository.findById(id);
    }

    @Override
    public Optional<AuthorWithBooks> getAuthorWithBooksById(Id<Author> id) {
        var author = authorRepository.findById(id);
        if (author.isEmpty()) {
            return Optional.empty();
        }

        var books = bookRepository.findByAuthor(id);

        return Optional.of(new AuthorWithBooks(author.get(), books));
    }

    @Override
    public List<Author> getBySortName(String sortName) {
        return authorRepository.findBySortName(sortName);
    }

    @Override
    public void deleteAuthor(Id<Author> id) {
        authorRepository.delete(id);
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
