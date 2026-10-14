package org.abondar.experimental.bookstore.author.application.service;

import lombok.RequiredArgsConstructor;
import org.abondar.experimental.bookstore.author.application.port.in.AuthorOperations;
import org.abondar.experimental.bookstore.author.application.port.out.AuthorRepository;
import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.author.domain.AuthorWithBooks;
import org.abondar.experimental.bookstore.book.application.port.out.BookRepository;
import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.common.Id;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthorService implements AuthorOperations {

    private final AuthorRepository authorRepository;

    private final BookRepository bookRepository;

    @Override
    public void saveAuthor(Author author) {
        if (authorExists(author)) {
            throw new IllegalArgumentException("Author already exists");
        }

        authorRepository.save(author);
    }

    @Override
    @Transactional
    public void saveAuthorWithBooks(Author author, List<Book> books) {
        if (authorExists(author)) {
            throw new IllegalArgumentException("Author already exists");
        }

        var booksWithAuthor = books.stream()
                .map(book -> book.withAuthor(author.getId()))
                .toList();

        authorRepository.save(author);
        bookRepository.saveMany(booksWithAuthor);
    }

    @Override
    public void updateAuthor(Author author) {
        if (authorRepository.findById(author.getId()).isPresent()) {
            authorRepository.save(author);
        } else {
            throw new IllegalArgumentException("Author doesn't exist");
        }
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

}
