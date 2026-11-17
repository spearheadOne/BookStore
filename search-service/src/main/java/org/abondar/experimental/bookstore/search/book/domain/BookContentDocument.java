package org.abondar.experimental.bookstore.search.book.domain;

public record BookContentDocument(
    String bookId, String format, String originalFilename, String content) {}
