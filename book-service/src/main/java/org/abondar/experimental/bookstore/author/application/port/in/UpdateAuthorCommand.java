package org.abondar.experimental.bookstore.author.application.port.in;

import java.time.LocalDate;
import java.util.Objects;
import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.common.Text;

public record UpdateAuthorCommand(
    Id<Author> id,
    Text firstName,
    Text lastName,
    String sortName,
    LocalDate dateOfBirth,
    LocalDate dateOfDeath,
    String countryCode,
    String biography) {
  public UpdateAuthorCommand {
    Objects.requireNonNull(id, "Author ID must not be null");
  }
}
