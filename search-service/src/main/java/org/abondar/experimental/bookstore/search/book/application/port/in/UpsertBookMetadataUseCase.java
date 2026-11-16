package org.abondar.experimental.bookstore.search.book.application.port.in;

import org.abondar.experimental.bookstore.search.book.domain.BookMetadata;

public interface UpsertBookMetadataUseCase {

  void upsertBookMetadata(BookMetadata bookMetadata);
}
