package org.abondar.experimental.bookstore.book.application.port.in;

import java.io.InputStream;
import java.util.UUID;

public interface UploadBookUseCase {

  void uploadBookContent(
      UUID id, InputStream file, String format, String originalFilename, long size);
}
