package org.abondar.experimental.bookstore.book.adapter.in;

import org.abondar.experimental.bookstore.adapter.in.generated.bookservice.model.BookContentFormat;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
final class BookContentFormatConverter implements Converter<String, BookContentFormat> {

  @Override
  public BookContentFormat convert(String source) {
    return BookContentFormat.fromValue(source);
  }
}
