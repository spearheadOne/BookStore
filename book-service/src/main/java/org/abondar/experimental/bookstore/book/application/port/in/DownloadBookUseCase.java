package org.abondar.experimental.bookstore.book.application.port.in;

import java.util.Optional;
import java.util.UUID;

public interface DownloadBookUseCase {

  Optional<DownloadedBookContent> downloadBookContent(UUID id, String format);
}
