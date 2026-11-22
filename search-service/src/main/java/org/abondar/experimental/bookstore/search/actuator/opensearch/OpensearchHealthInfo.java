package org.abondar.experimental.bookstore.search.actuator.opensearch;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class OpensearchHealthInfo {

  private boolean healthy;

  private String luceneVersion;

  private String version;

  private String buildDate;

  private String buildHash;

  private String clusterName;
  private String status;
  private int numberOfNodes;
  private int numberOfDataNodes;
  private int activePrimaryShards;
  private int unassignedShards;
  private boolean timedOut;
}
