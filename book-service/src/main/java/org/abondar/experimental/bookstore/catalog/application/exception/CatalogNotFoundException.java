package org.abondar.experimental.bookstore.catalog.application.exception;

public class CatalogNotFoundException extends RuntimeException {
  public CatalogNotFoundException() {
    super("Catalog not found");
  }
}
