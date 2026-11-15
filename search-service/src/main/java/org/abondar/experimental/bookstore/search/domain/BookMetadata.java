package org.abondar.experimental.bookstore.search.domain;

public record BookMetadata(
    String bookId, String isbn, String title, String description, String catalogId) {}
