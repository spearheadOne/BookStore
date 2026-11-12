package org.abondar.experimental.bookstore.search.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.abondar.experimental.bookstore.search.application.port.in.CreateIndexUseCase;
import org.abondar.experimental.bookstore.search.application.port.in.DeleteIndexUseCase;
import org.abondar.experimental.bookstore.search.application.port.out.IndexRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexService implements CreateIndexUseCase, DeleteIndexUseCase {

  private final IndexRepository indexRepository;

  @Override
  public void createIndexes() {
    indexRepository.createIndexes();
  }

  @Override
  public void deleteIndexes() {
    indexRepository.deleteIndexes();
  }
}
