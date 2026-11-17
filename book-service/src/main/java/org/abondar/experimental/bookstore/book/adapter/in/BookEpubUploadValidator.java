package org.abondar.experimental.bookstore.book.adapter.in;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipInputStream;
import lombok.RequiredArgsConstructor;
import org.abondar.experimental.bookstore.config.EpubProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

@Component
@RequiredArgsConstructor
public class BookEpubUploadValidator {

  private final EpubProperties epubProperties;

  public boolean isEpubValid(InputStream content) throws IOException {
    long maxExpandedBytes = DataSize.ofMegabytes(epubProperties.maxExpandedSizeMb()).toBytes();

    try (var zip = new ZipInputStream(content)) {
      byte[] buffer = new byte[8192];
      long expandedBytes = 0;
      long entryCount = 0;

      while (zip.getNextEntry() != null) {
        if (++entryCount > epubProperties.maxZipEntries()) {
          return false;
        }

        int bytesRead;
        while ((bytesRead = zip.read(buffer)) != -1) {
          expandedBytes += bytesRead;
          if (expandedBytes > maxExpandedBytes) {
            return false;
          }
        }
      }

      if (entryCount == 0) {
        throw new IOException("Empty/non-ZIP content");
      }
    }

    return true;
  }
}
