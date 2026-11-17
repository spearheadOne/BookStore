package org.abondar.experimental.bookstore.search.book.domain;

public record BookContent(
    String bookId,
    String format,
    String storageKey,
    String contentType,
    String originalFileName,
    long sizeBytes) {}
