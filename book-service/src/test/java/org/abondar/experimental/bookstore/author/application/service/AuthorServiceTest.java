package org.abondar.experimental.bookstore.author.application.service;

import org.abondar.experimental.bookstore.author.application.port.out.AuthorRepository;
import org.abondar.experimental.bookstore.author.domain.Author;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AuthorServiceTest {

    @Mock
    private AuthorRepository authorRepository;

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private AuthorService authorService;

    @Test
    public void saveAuthor() {
        var author = author();
        when(authorRepository.findByNameAndDob(author.getFirstName(), author.getLastName(),
                author.getDateOfBirth())).thenReturn(Optional.empty());

        authorService.saveAuthor(author);

        verify(authorRepository).save(author);
    }

    @Test
    public void saveAuthorAlreadyExists() {
        var author = author();
        when(authorRepository.findByNameAndDob(author.getFirstName(), author.getLastName(),
                author.getDateOfBirth())).thenReturn(Optional.of(author));

        assertThrows(IllegalArgumentException.class, () -> authorService.saveAuthor(author));

        verify(authorRepository, never()).save(author);
    }

    @Test
    public void saveAuthorWithBooksAddsAuthorToImmutableBooks() {
        var author = author();
        var existingAuthorId = Id.<Author>generate();
        var firstBook = Book.create(new Isbn("0306406152"), new Text("First book"),
                null, null, List.of(), null);
        var secondBook = Book.create(new Isbn("9780134685991"), new Text("Second book"),
                null, null, List.of(existingAuthorId), null);
        when(authorRepository.findByNameAndDob(author.getFirstName(), author.getLastName(),
                author.getDateOfBirth())).thenReturn(Optional.empty());

        authorService.saveAuthorWithBooks(author, List.of(firstBook, secondBook));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Book>> booksCaptor = ArgumentCaptor.forClass(List.class);
        verify(authorRepository).save(author);
        verify(bookRepository).saveMany(booksCaptor.capture());
        var savedBooks = booksCaptor.getValue();
        assertAll(
                () -> assertEquals(List.of(), firstBook.getAuthorIds()),
                () -> assertEquals(List.of(existingAuthorId), secondBook.getAuthorIds()),
                () -> assertEquals(List.of(author.getId()), savedBooks.get(0).getAuthorIds()),
                () -> assertEquals(List.of(existingAuthorId, author.getId()), savedBooks.get(1).getAuthorIds()),
                () -> assertEquals(firstBook.getId(), savedBooks.get(0).getId()),
                () -> assertEquals(secondBook.getId(), savedBooks.get(1).getId())
        );
    }

    @Test
    public void saveAuthorWithBooksRejectsExistingAuthor() {
        var author = author();
        when(authorRepository.findByNameAndDob(author.getFirstName(), author.getLastName(),
                author.getDateOfBirth())).thenReturn(Optional.of(author));

        assertThrows(IllegalArgumentException.class,
                () -> authorService.saveAuthorWithBooks(author, List.of()));

        verify(authorRepository, never()).save(author);
        verifyNoInteractions(bookRepository);
    }

    @Test
    public void updateExistingAuthor() {
        var author = author();

        authorService.updateAuthor(author);

        verify(authorRepository).update(author);
    }

    @Test
    public void updateMissingAuthor() {
        var author = author();
        doThrow(new IllegalArgumentException("Author doesn't exist"))
                .when(authorRepository).update(author);

        assertThrows(IllegalArgumentException.class, () -> authorService.updateAuthor(author));

        verify(authorRepository).update(author);
    }

    @Test
    public void getAuthorById() {
        var author = author();
        when(authorRepository.findById(author.getId())).thenReturn(Optional.of(author));

        var result = authorService.getAuthorById(author.getId());

        assertSame(author, result.orElseThrow());
    }

    @Test
    public void getAuthorByIdWhenMissing() {
        Id<Author> id = Id.generate();
        when(authorRepository.findById(id)).thenReturn(Optional.empty());

        var result = authorService.getAuthorById(id);

        assertTrue(result.isEmpty());
    }

    @Test
    public void getAuthorWithBooksById() {
        var author = author();
        var book = Book.create(new Isbn("9780201633610"), new Text("Book"),
                null, null, List.of(author.getId()), null);
        when(authorRepository.findById(author.getId())).thenReturn(Optional.of(author));
        when(bookRepository.findByAuthor(author.getId())).thenReturn(List.of(book));

        var result = authorService.getAuthorWithBooksById(author.getId());

        assertAll(
                () -> assertSame(author, result.orElseThrow().author()),
                () -> assertEquals(List.of(book), result.orElseThrow().books())
        );
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

        authorService.deleteAuthor(id);

        verify(authorRepository).delete(id);
    }

    private Author author() {
        return Author.create(new Text("Ada"), new Text("Lovelace"), "Lovelace, Ada",
                LocalDate.of(1815, 12, 10), null, "GB", null);
    }
}
