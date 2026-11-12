package org.abondar.experimental.bookstore.search.config;

import java.net.URI;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.Timeout;
import org.opensearch.client.json.jackson3.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
public class OpenSearchConfig {

  @Bean(destroyMethod = "close")
  public OpenSearchTransport openSearchTransport(OpenSearchProperties properties) {
    var host = HttpHost.create(URI.create(properties.url()));
    var connectTimeout = Timeout.ofMilliseconds(properties.connectTimeout().toMillis());
    var responseTimeout = Timeout.ofMilliseconds(properties.responseTimeout().toMillis());

    return ApacheHttpClient5TransportBuilder.builder(host)
        .setMapper(new JacksonJsonpMapper())
        .setRequestConfigCallback(
            request ->
                request
                    .setConnectionRequestTimeout(connectTimeout)
                    .setResponseTimeout(responseTimeout))
        .setHttpClientConfigCallback(
            httpClient -> {
              var connections =
                  PoolingAsyncClientConnectionManagerBuilder.create()
                      .setDefaultConnectionConfig(
                          ConnectionConfig.custom().setConnectTimeout(connectTimeout).build())
                      .build();
              httpClient.setConnectionManager(connections);
              if (StringUtils.hasText(properties.username())) {
                var credentials = new BasicCredentialsProvider();
                credentials.setCredentials(
                    new AuthScope(host),
                    new UsernamePasswordCredentials(
                        properties.username(), properties.password().toCharArray()));
                httpClient.setDefaultCredentialsProvider(credentials);
              }
              return httpClient;
            })
        .build();
  }

  @Bean
  public OpenSearchClient openSearchClient(OpenSearchTransport transport) {
    return new OpenSearchClient(transport);
  }
}
