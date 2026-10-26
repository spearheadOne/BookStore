package org.abondar.experimental.bookstore.catalog.application.exception;

public class CatalogAlreadyExistsException extends RuntimeException {
    public CatalogAlreadyExistsException(String name) {
        super(String.format("Catalog already exists with name: %s", name));
    }
}
