package org.abondar.experimental.bookstore.author.adapter.out;

import lombok.RequiredArgsConstructor;
import org.abondar.experimental.bookstore.adapter.out.persistence.generated.tables.records.AuthorRecord;
import org.abondar.experimental.bookstore.author.application.port.out.AuthorRepository;
import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.common.Text;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.abondar.experimental.bookstore.adapter.out.persistence.generated.tables.Author.AUTHOR;

@Repository
@RequiredArgsConstructor
public class AuthorPersistenceAdapter implements AuthorRepository {

    private final DSLContext dslContext;

    @Override
    public void save(Author author) {
        var createdAt = OffsetDateTime.ofInstant(author.getCreatedAt(), ZoneOffset.UTC);
        var updatedAt = OffsetDateTime.ofInstant(author.getUpdatedAt(), ZoneOffset.UTC);

        dslContext.insertInto(AUTHOR)
                .set(AUTHOR.ID, author.getId().value())
                .set(AUTHOR.SORT_NAME, author.getSortName())
                .set(AUTHOR.FIRST_NAME, author.getFirstName().value())
                .set(AUTHOR.LAST_NAME, author.getLastName().value())
                .set(AUTHOR.BIOGRAPHY, author.getBiography())
                .set(AUTHOR.COUNTRY_CODE, author.getCountryCode())
                .set(AUTHOR.DATE_OF_BIRTH, author.getDateOfBirth())
                .set(AUTHOR.DATE_OF_DEATH, author.getDateOfDeath())
                .set(AUTHOR.CREATED_AT, createdAt)
                .set(AUTHOR.UPDATED_AT, updatedAt)
                .execute();
    }

    @Override
    public void update(Author author) {
        int affected = dslContext.update(AUTHOR)
                .set(AUTHOR.SORT_NAME, author.getSortName())
                .set(AUTHOR.FIRST_NAME, author.getFirstName().value())
                .set(AUTHOR.LAST_NAME, author.getLastName().value())
                .set(AUTHOR.BIOGRAPHY, author.getBiography())
                .set(AUTHOR.COUNTRY_CODE, author.getCountryCode())
                .set(AUTHOR.DATE_OF_BIRTH, author.getDateOfBirth())
                .set(AUTHOR.DATE_OF_DEATH, author.getDateOfDeath())
                .set(AUTHOR.UPDATED_AT, OffsetDateTime.ofInstant(author.getUpdatedAt(), ZoneOffset.UTC))
                .where(AUTHOR.ID.eq(author.getId().value()))
                .execute();
        if (affected == 0) {
            throw new IllegalArgumentException("Author doesn't exist");
        }
    }

    @Override
    public Optional<Author> findByNameAndDob(Text firstName, Text lastName, LocalDate dob) {
        return dslContext.selectFrom(AUTHOR)
                .where(AUTHOR.FIRST_NAME.eq(firstName.value()))
                .and(AUTHOR.LAST_NAME.eq(lastName.value()))
                .and(dob == null ? AUTHOR.DATE_OF_BIRTH.isNull() : AUTHOR.DATE_OF_BIRTH.eq(dob))
                .fetchOptional()
                .map(this::toAuthor);
    }

    @Override
    public Optional<Author> findById(Id<Author> id) {
        return dslContext.selectFrom(AUTHOR)
                .where(AUTHOR.ID.eq(id.value()))
                .fetchOptional()
                .map(this::toAuthor);
    }

    @Override
    public List<Author> findBySortName(String sortName) {
        return dslContext.selectFrom(AUTHOR)
                .where(AUTHOR.SORT_NAME.eq(sortName))
                .orderBy(AUTHOR.FIRST_NAME)
                .fetch()
                .stream()
                .map(this::toAuthor)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(Id<Author> id) {
        dslContext.delete(AUTHOR)
                .where(AUTHOR.ID.eq(id.value()))
                .execute();
    }

    private Author toAuthor(AuthorRecord authorRecord) {
        return Author.restore(
                new Id<>(authorRecord.getId()),
                new Text(authorRecord.getFirstName()),
                new Text(authorRecord.getLastName()),
                authorRecord.getSortName(),
                authorRecord.getDateOfBirth(),
                authorRecord.getDateOfDeath(),
                authorRecord.getCountryCode(),
                authorRecord.getBiography(),
                authorRecord.getCreatedAt().toInstant(),
                authorRecord.getUpdatedAt().toInstant()
        );
    }

}
