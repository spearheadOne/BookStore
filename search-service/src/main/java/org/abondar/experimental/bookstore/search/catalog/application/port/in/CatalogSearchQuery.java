package org.abondar.experimental.bookstore.search.catalog.application.port.in;

public record CatalogSearchQuery(String catalogId, String name, int limit) {}
