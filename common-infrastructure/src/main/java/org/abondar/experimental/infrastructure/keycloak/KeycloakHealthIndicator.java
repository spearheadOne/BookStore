package org.abondar.experimental.infrastructure.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component("keycloakHealthIndicator")
public class KeycloakHealthIndicator implements HealthIndicator {

  private final String issuer;
  private final RestClient client;
  private final String discoveryUrl;

  public KeycloakHealthIndicator(
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
      @Value("${keycloak.health.discovery-uri:}") String discoveryUri) {
    this.issuer = issuer;
    this.discoveryUrl =
        discoveryUri.isBlank()
            ? issuer.replaceAll("/+$", "") + "/.well-known/openid-configuration"
            : discoveryUri;
    var requests = new SimpleClientHttpRequestFactory();
    requests.setConnectTimeout(Duration.ofSeconds(2));
    requests.setReadTimeout(Duration.ofSeconds(2));
    this.client = RestClient.builder().requestFactory(requests).build();
  }

  @Override
  public Health health() {
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
