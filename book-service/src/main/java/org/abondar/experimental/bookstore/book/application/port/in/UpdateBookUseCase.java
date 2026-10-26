package org.abondar.experimental.bookstore.book.application.port.in;

import org.abondar.experimental.bookstore.book.domain.Book;

public interface UpdateBookUseCase {
  Book updateBook(UpdateBookCommand command);
}
