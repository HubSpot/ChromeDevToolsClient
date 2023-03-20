package com.hubspot.chrome.devtools.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.hubspot.chrome.devtools.base.ChromeSessionInfo;
import com.hubspot.chrome.devtools.client.core.target.TargetID;
import com.hubspot.chrome.devtools.client.exceptions.ChromeDevToolsException;
import com.hubspot.horizon.HttpClient;
import com.hubspot.horizon.HttpRequest;
import com.hubspot.horizon.HttpRequest.Method;
import com.hubspot.horizon.HttpResponse;
import com.hubspot.horizon.HttpRuntimeException;
import java.io.Closeable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChromeDevToolsClient implements Closeable {
  private static final Logger LOG = LoggerFactory.getLogger(ChromeDevToolsClient.class);

  private static final String WEBSOCKET_URL_TEMPLATE = "ws://%s:%s/devtools/page/%s";

  private final Retryer<TargetID> httpRetryer;

  private final ExecutorService executorService;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final long actionTimeoutMillis;
  private final boolean defaultStartNewTarget;

  private ChromeDevToolsClient(
    ObjectMapper objectMapper,
    ExecutorService executorService,
    HttpClient httpClient,
    long actionTimeoutMillis,
    long sessionConnectTimeoutMillis,
    boolean defaultStartNewTarget
  ) {
    this.executorService = executorService;
    this.objectMapper = objectMapper;
    this.httpClient = httpClient;
    this.actionTimeoutMillis = actionTimeoutMillis;
    this.defaultStartNewTarget = defaultStartNewTarget;
    this.httpRetryer =
      RetryerBuilder
        .<TargetID>newBuilder()
        .retryIfExceptionOfType(ChromeDevToolsException.class)
        .retryIfExceptionOfType(HttpRuntimeException.class)
        .withStopStrategy(StopStrategies.stopAfterDelay(sessionConnectTimeoutMillis))
        .withWaitStrategy(WaitStrategies.exponentialWait(100, TimeUnit.MILLISECONDS))
        .build();
  }

  public static ChromeDevToolsClient defaultClient() {
    return new Builder().build();
  }

  public ChromeDevToolsSession connect(String host, int port) throws URISyntaxException {
    TargetID targetId;
    try {
      targetId = httpRetryer.call(() -> getFirstAvailableTargetId(host, port));
    } catch (ExecutionException | RetryException e) {
      throw new ChromeDevToolsException(e);
    }
    String uri = String.format(WEBSOCKET_URL_TEMPLATE, host, port, targetId);
    return new ChromeDevToolsSession(
      new URI(uri),
      objectMapper,
      executorService,
      actionTimeoutMillis
    );
  }

  @Override
  public void close() {
    try {
      executorService.shutdown();
      httpClient.close();
    } catch (Throwable t) {
      LOG.error("Could not properly close chrome client", t);
    }
  }

  private TargetID getFirstAvailableTargetId(String host, int port) {
    if (defaultStartNewTarget) {
      return startNewTarget(host, port);
    }
    String url = String.format("http://%s:%d/json/list", host, port);
    HttpRequest httpRequest = HttpRequest
      .newBuilder()
      .setUrl(url)
      .setMethod(Method.GET)
      .build();

    HttpResponse response = httpClient.execute(httpRequest);

    if (response.isError()) {
      throw new ChromeDevToolsException("Unable to find available chrome session info.");
    }

    List<ChromeSessionInfo> sessions = response.getAs(new TypeReference<>() {});
    if (sessions.size() == 0) {
      return startNewTarget(host, port);
    }
    return new TargetID(sessions.get(0).getId());
  }

  private TargetID startNewTarget(String host, int port) {
    String url = String.format("http://%s:%d/json/new", host, port);
    HttpRequest httpRequest = HttpRequest
      .newBuilder()
      .setUrl(url)
      .setMethod(Method.PUT)
      .build();
    HttpResponse response = httpClient.execute(httpRequest);

    if (response.isError()) {
      throw new ChromeDevToolsException("Unable to find available chrome session info.");
    }

    TargetID targetID = response.getAs(new TypeReference<>() {});
    LOG.debug("new TargetID: {}", targetID);

    return targetID;
  }

  public static class Builder {
    private ExecutorService executorService;
    private ObjectMapper objectMapper;
    private HttpClient httpClient;
    private long actionTimeoutMillis;
    private long sessionConnectTimeoutMillis;
    private boolean defaultStartNewTarget;

    public Builder() {
      this.executorService = ChromeDevToolsClientDefaults.DEFAULT_EXECUTOR_SERVICE;
      this.objectMapper = ChromeDevToolsClientDefaults.DEFAULT_OBJECT_MAPPER;
      this.httpClient = ChromeDevToolsClientDefaults.DEFAULT_HTTP_CLIENT;
      this.actionTimeoutMillis =
        ChromeDevToolsClientDefaults.DEFAULT_CHROME_ACTION_TIMEOUT_MILLIS;
      this.sessionConnectTimeoutMillis =
        ChromeDevToolsClientDefaults.DEFAULT_HTTP_CONNECTION_RETRY_TIMEOUT_MILLIS;
      this.defaultStartNewTarget = ChromeDevToolsClientDefaults.DEFAULT_START_NEW_TARGET;
    }

    public ChromeDevToolsClient.Builder setExecutorService(
      ExecutorService executorService
    ) {
      this.executorService = executorService;
      return this;
    }

    /**
     * @deprecated
     * Contains implementation specific details regarding event deserialization.
     * Never should have been exposed in the first place.
     */
    @Deprecated
    public ChromeDevToolsClient.Builder setObjectMapper(ObjectMapper objectMapper) {
      this.objectMapper = objectMapper;
      return this;
    }

    public ChromeDevToolsClient.Builder setHttpClient(HttpClient httpClient) {
      this.httpClient = httpClient;
      return this;
    }

    public ChromeDevToolsClient.Builder setActionTimeoutMillis(long actionTimeoutMillis) {
      this.actionTimeoutMillis = actionTimeoutMillis;
      return this;
    }

    public Builder setSessionConnectTimeoutMillis(long sessionConnectTimeoutMillis) {
      this.sessionConnectTimeoutMillis = sessionConnectTimeoutMillis;
      return this;
    }

    public Builder setDefaultStartNewTarget(boolean defaultStartNewTarget) {
      this.defaultStartNewTarget = defaultStartNewTarget;
      return this;
    }

    public ChromeDevToolsClient build() {
      return new ChromeDevToolsClient(
        objectMapper,
        executorService,
        httpClient,
        actionTimeoutMillis,
        sessionConnectTimeoutMillis,
        defaultStartNewTarget
      );
    }
  }
}
