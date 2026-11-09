package org.abondar.experimental.bookstore.book.application.port.out;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import org.abondar.experimental.bookstore.book.application.port.in.DownloadedBookContent;

public interface BookContentRepository {

  void uploadBookContent(
      String key, InputStream content, String originalFilename, long size, String contentType);

  Optional<DownloadedBookContent> downloadBookContent(String key);

  void deleteBookContent(List<String> keys);
}
