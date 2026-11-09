package org.abondar.experimental.bookstore.book.adapter.in;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import org.abondar.experimental.bookstore.book.application.port.in.DeleteBookUseCase;
import org.abondar.experimental.bookstore.book.application.port.in.DownloadBookUseCase;
import org.abondar.experimental.bookstore.book.application.port.in.GetBookUseCase;
import org.abondar.experimental.bookstore.book.application.port.in.RegisterBookUseCase;
import org.abondar.experimental.bookstore.book.application.port.in.UpdateBookUseCase;
import org.abondar.experimental.bookstore.book.application.port.in.UploadBookUseCase;
import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.book.domain.Isbn;
import org.abondar.experimental.bookstore.common.Text;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.servlet.autoconfigure.MultipartAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.format.FormatterRegistry;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = BookContentUploadLimitTest.WebConfig.class,
    properties = {
      "spring.servlet.multipart.max-file-size=1KB",
      "spring.servlet.multipart.max-request-size=2KB",
      "management.server.port=-1"
    })
@MockitoBean(
    types = {
      RegisterBookUseCase.class,
      UpdateBookUseCase.class,
      DeleteBookUseCase.class,
      DownloadBookUseCase.class
    })
class BookContentUploadLimitTest {

  private static final int FILE_LIMIT = 1024;

  @LocalServerPort private int port;
  @MockitoBean private GetBookUseCase getBookUseCase;
  @MockitoBean private UploadBookUseCase uploadBookUseCase;

  private RestTestClient client;
  private Book book;

  @BeforeEach
  void setUp() {
    client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    book = Book.create(new Isbn("0306406152"), new Text("Book"), null, null, List.of(), null);
  }

  @Test
  void acceptsAFileExactlyAtTheConfiguredLimit() {
    when(getBookUseCase.getBook(book.getId())).thenReturn(Optional.of(book));

    upload(FILE_LIMIT, 0).expectStatus().isNoContent();

    verify(uploadBookUseCase)
        .uploadBookContent(
            eq(book.getId().value()),
            any(InputStream.class),
            eq("pdf"),
            eq("book.pdf"),
            eq((long) FILE_LIMIT));
  }

  @Test
  void rejectsAFileOneByteOverTheConfiguredLimit() {
    upload(FILE_LIMIT + 1, 0).expectStatus().isEqualTo(413);

    verifyNoInteractions(getBookUseCase, uploadBookUseCase);
  }

  @Test
  void rejectsAnOversizedRequestWithIndividuallyAllowedParts() {
    upload(FILE_LIMIT, FILE_LIMIT).expectStatus().isEqualTo(413);

    verifyNoInteractions(getBookUseCase, uploadBookUseCase);
  }

  private RestTestClient.ResponseSpec upload(int fileSize, int extraPartSize) {
    var body = new MultipartBodyBuilder();
    body.part("file", new byte[fileSize], MediaType.APPLICATION_PDF).filename("book.pdf");
    body.part("format", "pdf");
    if (extraPartSize > 0) {
      body.part("extra", new byte[extraPartSize], MediaType.APPLICATION_OCTET_STREAM)
          .filename("extra.bin");
    }
    return client
        .post()
        .uri("/api/v1/books/{id}/content", book.getId().value())
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(body.build())
        .exchange();
  }

  @Configuration(proxyBeanMethods = false)
  @EnableWebMvc
  @Import({BookApiAdapter.class, BookApiExceptionHandler.class})
  @ImportAutoConfiguration({
    TomcatServletWebServerAutoConfiguration.class,
    DispatcherServletAutoConfiguration.class,
    MultipartAutoConfiguration.class
  })
  static class WebConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
      registry.addConverter(new BookContentFormatConverter());
    }
  }
}
