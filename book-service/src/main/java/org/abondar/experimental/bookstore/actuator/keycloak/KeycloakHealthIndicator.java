package org.abondar.experimental.bookstore.actuator.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class KeycloakHealthIndicator implements HealthIndicator {

  private final String issuer;
  private final RestClient client;

  public KeycloakHealthIndicator(
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer) {
    this.issuer = issuer;
    var requests = new SimpleClientHttpRequestFactory();
    requests.setConnectTimeout(Duration.ofSeconds(2));
    requests.setReadTimeout(Duration.ofSeconds(2));
    this.client = RestClient.builder().requestFactory(requests).build();
  }

  @Override
  public Health health() {
    var discoveryUrl = issuer.replaceAll("/+$", "") + "/.well-known/openid-configuration";
    try {
      var discovery = client.get().uri(discoveryUrl).retrieve().body(Discovery.class);
      if (discovery == null || !issuer.equals(discovery.issuer())) {
        return Health.down().withDetail("reason", "Unexpected or missing realm issuer").build();
      }
      return Health.up().withDetail("issuer", issuer).build();
    } catch (RestClientException exception) {
      return Health.down().withDetail("reason", "Realm discovery endpoint unavailable").build();
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Discovery(String issuer) {}
}
