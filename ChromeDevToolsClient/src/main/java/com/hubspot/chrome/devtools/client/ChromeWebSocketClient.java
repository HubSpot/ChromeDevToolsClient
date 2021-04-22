package com.hubspot.chrome.devtools.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.hubspot.chrome.devtools.base.ChromeResponse;
import com.hubspot.chrome.devtools.base.ChromeResponseErrorBody;
import com.hubspot.chrome.devtools.client.core.Event;
import com.hubspot.chrome.devtools.client.core.EventType;
import com.hubspot.chrome.devtools.client.exceptions.ChromeDevToolsException;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChromeWebSocketClient extends WebSocketClient {
  private final Logger LOG = LoggerFactory.getLogger(ChromeWebSocketClient.class);
  private static final Map<String, EventType> EVENT_TYPES = Arrays.stream(EventType.values())
      .collect(Collectors.toMap(
          EventType::getType,
          Function.identity()
      ));

  private final Retryer<ChromeResponse> actionRetryer;

  private final Map<Integer, ChromeResponseErrorBody> errorsReceived;
  private final Map<Integer, ChromeResponse> messagesReceived;
  private final ObjectMapper objectMapper;
  private final Map<String, ChromeEventListener> chromeEventListeners;
  private final ExecutorService executorService;

  public ChromeWebSocketClient(URI uri,
                               ObjectMapper objectMapper,
                               Map<String, ChromeEventListener> chromeEventListeners,
                               ExecutorService executorService,
                               long actionTimeoutMillis) {
    super(uri);
    this.objectMapper = objectMapper;
    this.chromeEventListeners = chromeEventListeners;
    this.executorService = executorService;
    this.errorsReceived = new HashMap<>();
    this.messagesReceived = new HashMap<>();

    // The timeout here is merely a safety net in case the user doesn't complete the futures
    // this returns with their own timeout.
    this.actionRetryer = RetryerBuilder.<ChromeResponse>newBuilder()
        .retryIfResult(Objects::isNull)
        .withStopStrategy(StopStrategies.stopAfterDelay(actionTimeoutMillis))
        .withWaitStrategy(WaitStrategies.exponentialWait(100, TimeUnit.MILLISECONDS))
        .build();
  }

  @Override
  public void onOpen(ServerHandshake handshakedata) {
    LOG.debug("Connected ({})", handshakedata.getHttpStatusMessage());

    // Connection checking triggers false positive lost connection checks, which causes premature
    // websocket disconnects. Chrome doesn't seem to respond to websocket pings with a pong response,
    // so disabling this is only our option at the current time.
    disableConnectionLostChecking();
  }

  private void disableConnectionLostChecking() {
    this.setConnectionLostTimeout(-1);
  }

  @Override
  public void onClose(int code, String reason, boolean remote) {
    LOG.debug("Disconnected from session ({}: {})", code, reason);
    messagesReceived.clear();
  }

  @Override
  public void onMessage(String message) {
    LOG.trace("Received message: {}", message);

    try {
      ChromeResponse response = objectMapper.readValue(message, ChromeResponse.class);
      if (response.isResponse()) {
        messagesReceived.put(response.getId(), response);
      } else if (response.isEvent()) {
        Event event = objectMapper.readValue(message, Event.class);
        for (ChromeEventListener eventListener : chromeEventListeners.values()) {
          EventType type = EVENT_TYPES.get(response.getMethod());
          executorService.submit(() -> eventListener.onEvent(type, event));
        }
      } else if (response.isError()) {
        LOG.error(response.getError().toString());
        errorsReceived.put(response.getId(), response.getError());
      }
    } catch (IOException ioe) {
      LOG.error("Could not parse response from chrome", ioe);
    }
  }

  @Override
  public void onMessage(ByteBuffer message) {
    LOG.warn("Not set up to handle byte buffer, received buffer of size {}", message.array().length);
  }

  @Override
  public void onError(Exception ex) {
    LOG.error("Websocket exception for session", ex);
  }

  public ChromeResponse getResponse(int id) {
    try {
      ChromeResponse response = actionRetryer.call(() -> {
        if (errorsReceived.containsKey(id)) {
          ChromeResponseErrorBody error = errorsReceived.get(id);
          throw new ChromeDevToolsException(error.getMessage(), error.getCode());
        }
        return messagesReceived.get(id);
      });
      messagesReceived.remove(id);
      return response;
    } catch (ExecutionException | RetryException e) {
      throw new ChromeDevToolsException(e);
    }
  }
}
