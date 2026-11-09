package org.abondar.experimental.bookstore.book.application.port.in;

import java.io.InputStream;

public record DownloadedBookContent(
    InputStream stream, String originalFilename, String contentType) {}
