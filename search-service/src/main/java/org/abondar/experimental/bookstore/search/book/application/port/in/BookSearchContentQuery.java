package org.abondar.experimental.bookstore.search.book.application.port.in;

public record BookSearchContentQuery(
    String bookId, String format, String originalFilename, String q, int limit) {}
