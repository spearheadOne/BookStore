package org.abondar.experimental.bookstore.search.catalog.application.port.in;

import java.util.List;
import org.abondar.experimental.bookstore.search.catalog.application.domain.CatalogDocument;

public interface SearchCatalogUseCase {

  List<CatalogDocument> searchCatalogs(CatalogSearchQuery query);
}
