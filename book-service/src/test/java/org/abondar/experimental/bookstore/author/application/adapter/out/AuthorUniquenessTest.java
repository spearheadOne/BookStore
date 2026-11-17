package org.abondar.experimental.bookstore.author.application.adapter.out;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.UUID;
import org.abondar.experimental.bookstore.BasePersistenceAdapterTest;
import org.abondar.experimental.bookstore.author.adapter.out.AuthorPersistenceAdapter;
import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.common.Text;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AuthorUniquenessTest extends BasePersistenceAdapterTest {

  @Autowired private AuthorPersistenceAdapter adapter;

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void duplicateNameAndBirthDateIsRejectedIncludingNullBirthDate(boolean hasBirthDate) {
    var firstName = new Text(UUID.randomUUID().toString());
    var lastName = new Text("Unique author");
    var dob = hasBirthDate ? LocalDate.of(1980, 1, 1) : null;
    var first = Author.create(firstName, lastName, null, dob, null, null, null);
    var duplicate = Author.create(firstName, lastName, null, dob, null, null, null);
    adapter.save(first);

    assertThrows(DuplicateKeyException.class, () -> adapter.save(duplicate));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void updateCannotDuplicateAnotherAuthor(boolean hasBirthDate) {
    var firstName = new Text(UUID.randomUUID().toString());
    var dob = hasBirthDate ? LocalDate.of(1980, 1, 1) : null;
    var first = Author.create(firstName, new Text("Original"), null, dob, null, null, null);
    var second = Author.create(firstName, new Text("Other"), null, dob, null, null, null);
    adapter.save(first);
    adapter.save(second);
    var duplicate =
        Author.restore(
            second.getId(),
            first.getFirstName(),
            first.getLastName(),
            null,
            dob,
            null,
            null,
            null,
            second.getCreatedAt(),
            second.getUpdatedAt());

    assertThrows(DuplicateKeyException.class, () -> adapter.update(duplicate));
  }
}
