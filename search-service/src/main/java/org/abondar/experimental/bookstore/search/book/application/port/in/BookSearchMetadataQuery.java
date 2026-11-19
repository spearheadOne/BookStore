package org.abondar.experimental.bookstore.search.book.application.port.in;

public record BookSearchMetadataQuery(
    String bookId, String isbn, String catalogId, String q, int limit) {}
