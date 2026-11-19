package org.abondar.experimental.bookstore.search.book.domain;

public record BookMetadataDocument(
    String bookId, String isbn, String title, String description, String catalogId) {}
