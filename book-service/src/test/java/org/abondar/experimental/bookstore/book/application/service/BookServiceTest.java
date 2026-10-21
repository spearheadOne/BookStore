package org.abondar.experimental.bookstore.book.application.service;

import org.abondar.experimental.bookstore.book.application.exception.BookAlreadyExistsException;
import org.abondar.experimental.bookstore.book.application.exception.BookNotFoundException;
import org.abondar.experimental.bookstore.book.application.port.out.BookRepository;
import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.book.domain.Isbn;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.common.Text;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class BookServiceTest {

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private BookService bookService;

    @Test
    public void registerBook() {
        var book = Book.create(
                new Isbn("0306406152"),
                new Text("test"),
                null,
                null,
                List.of(),
                null
        );

        when(bookRepository.findByIsbn(book.getIsbn())).thenReturn(Optional.empty());
        bookService.registerBook(book);

        verify(bookRepository).save(book);
    }

    @Test
    public void registerBookAlready() {
        var book = Book.create(
                new Isbn("0306406152"),
                new Text("test"),
                null,
                null,
                List.of(),
                null
        );

        when(bookRepository.findByIsbn(book.getIsbn())).thenReturn(Optional.of(book));

        var exception = assertThrows(BookAlreadyExistsException.class,
                () -> bookService.registerBook(book));

        assertTrue(exception.getMessage().contains("already exists"));
        verify(bookRepository, never()).save(book);
    }

    @Test
    public void getBookTest() {
        var book = Book.create(
                new Isbn("0306406152"),
                new Text("test"),
                null,
                null,
                List.of(),
                null
        );

        Id<Book> id = book.getId();
        when(bookRepository.findById(id)).thenReturn(Optional.of(book));

        var result = bookService.getBook(id);

        assertSame(book, result.orElseThrow());
        verify(bookRepository).findById(id);
    }

    @Test
    public void getBookNotFound() {
        Id<Book> id = Id.generate();
        when(bookRepository.findById(id)).thenReturn(Optional.empty());

        var result = bookService.getBook(id);

        assertTrue(result.isEmpty());
        verify(bookRepository).findById(id);
    }

    @Test
    public void updateBookWhenFound() {
        var existingBook = Book.create(
                new Isbn("0306406152"),
                new Text("original title"),
                null,
                null,
                List.of(),
                null
        );
        var updatedBook = Book.restore(
                existingBook.getId(),
                existingBook.getIsbn(),
                new Text("updated title"),
                existingBook.getDescription(),
                existingBook.getIssueYear(),
                existingBook.getAuthorIds(),
                existingBook.getCatalogId(),
                existingBook.getCreatedAt(),
                existingBook.getUpdatedAt().plusSeconds(1)
        );
        when(bookRepository.findById(updatedBook.getId())).thenReturn(Optional.of(existingBook));

        bookService.updateBook(updatedBook);

        verify(bookRepository).findById(updatedBook.getId());
        verify(bookRepository).save(updatedBook);
    }

    @Test
    public void updateBookWhenNotFound() {
        var book = Book.create(
                new Isbn("0306406152"),
                new Text("updated title"),
                null,
                null,
                List.of(),
                null
        );
        when(bookRepository.findById(book.getId())).thenReturn(Optional.empty());

        var exception = assertThrows(BookNotFoundException.class,
                () -> bookService.updateBook(book));

        assertTrue(exception.getMessage().contains("does not exist"));
        verify(bookRepository).findById(book.getId());
        verify(bookRepository, never()).save(book);
    }

    @Test
    public void deleteTest() {
        Id<Book> id = Id.generate();
        bookService.deleteBook(id);

        verify(bookRepository).delete(id);
    }

}
