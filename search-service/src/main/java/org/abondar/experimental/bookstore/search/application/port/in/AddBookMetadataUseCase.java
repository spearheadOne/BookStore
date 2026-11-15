package org.abondar.experimental.bookstore.search.application.port.in;

import org.abondar.experimental.bookstore.search.domain.BookMetadata;

public interface AddBookMetadataUseCase {

  void upsertBookMetadata(BookMetadata bookMetadata);
}
