package org.abondar.experimental.bookstore.search.book.application.port.out;

import java.io.InputStream;
import org.abondar.experimental.bookstore.search.book.domain.BookContent;

public interface BookStorageRepository {

  InputStream downloadBookContent(BookContent bookContent);
}
