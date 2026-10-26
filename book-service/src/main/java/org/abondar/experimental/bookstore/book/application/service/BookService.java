package org.abondar.experimental.bookstore.book.application.service;

import lombok.RequiredArgsConstructor;
import org.abondar.experimental.bookstore.book.application.exception.BookAlreadyExistsException;
import org.abondar.experimental.bookstore.book.application.port.in.DeleteBookUseCase;
import org.abondar.experimental.bookstore.book.application.port.in.GetBookUseCase;
import org.abondar.experimental.bookstore.book.application.port.in.RegisterBookUseCase;
import org.abondar.experimental.bookstore.book.application.port.in.UpdateBookUseCase;
import org.abondar.experimental.bookstore.book.application.port.out.BookRepository;
import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.common.Id;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BookService implements RegisterBookUseCase, GetBookUseCase, UpdateBookUseCase, DeleteBookUseCase {

    private final BookRepository bookRepository;

    @Override
    public void registerBook(Book book) {
        var exists = bookRepository.findByIsbn(book.getIsbn()).isPresent();

        if (!exists) {
            bookRepository.save(book);
        } else {
            throw new BookAlreadyExistsException(book.getIsbn());
        }
    }

    @Override
    public Optional<Book> getBook(Id<Book> id) {
        return bookRepository.findById(id);
    }

    @Override
    public void updateBook(Book book) {
        bookRepository.update(book);
    }


    @Override
    public void deleteBook(Id<Book> id) {
        bookRepository.delete(id);
    }
}
