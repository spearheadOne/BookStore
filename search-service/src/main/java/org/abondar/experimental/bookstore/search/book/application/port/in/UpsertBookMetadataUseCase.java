package org.abondar.experimental.bookstore.search.book.application.port.in;

import org.abondar.experimental.bookstore.search.book.domain.BookMetadataDocument;

public interface UpsertBookMetadataUseCase {

  void upsertBookMetadata(BookMetadataDocument bookMetadataDocument);
}
