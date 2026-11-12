package org.abondar.experimental.bookstore.search.application.port.out;


public interface IndexRepository {

  void createIndexes();

  void deleteIndexes();
}
