package org.abondar.experimental.bookstore.search.index.application.port.out;

public interface IndexRepository {

  void createIndexes();

  void deleteIndexes();
}
