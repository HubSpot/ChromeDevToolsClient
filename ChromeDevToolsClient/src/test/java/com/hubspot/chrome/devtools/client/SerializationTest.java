package com.hubspot.chrome.devtools.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.hubspot.chrome.devtools.client.core.Event;
import com.hubspot.chrome.devtools.client.core.EventType;
import com.hubspot.chrome.devtools.client.core.css.FontsUpdatedEvent;
import com.hubspot.chrome.devtools.client.core.dom.ChildNodeRemovedEvent;
import com.hubspot.chrome.devtools.client.core.dom.DocumentUpdatedEvent;
import com.hubspot.chrome.devtools.client.core.page.DomContentEventFiredEvent;
import com.hubspot.chrome.devtools.client.core.runtime.CallArgument;
import com.hubspot.chrome.devtools.client.core.runtime.ExceptionRevokedEvent;
import com.hubspot.chrome.devtools.client.core.target.SessionID;
import java.net.URI;
import java.util.Collections;
import java.util.Objects;
import org.junit.Test;

public class SerializationTest {

  @Test
  public void itIgnoresNullValues() throws Exception {
    ObjectMapper objectMapper = ChromeDevToolsClientDefaults.DEFAULT_OBJECT_MAPPER;

    assertThat(objectMapper.writeValueAsString(new CallArgument("test", null, null)))
      .isEqualTo("{\"value\":\"test\"}");
    assertThat(objectMapper.writeValueAsString(new CallArgument("test", null, null)))
      .isEqualTo("{\"value\":\"test\"}");
  }

  @Test
  public void itDeserializesEventsIntoCorrectTypes() throws Exception {
    ObjectMapper objectMapper = ChromeDevToolsClientDefaults.DEFAULT_OBJECT_MAPPER;

    String jsonA = "{\"method\":\"DOM.documentUpdated\",\"params\":{}}";
    assertThat(objectMapper.readValue(jsonA, Event.class))
      .isInstanceOf(DocumentUpdatedEvent.class);

    String jsonB =
      "{\"method\":\"DOM.childNodeRemoved\",\"params\":{\"parentNodeId\": 1, \"nodeId\": 2}}";
    assertThat(objectMapper.readValue(jsonB, Event.class))
      .isInstanceOf(ChildNodeRemovedEvent.class);
  }

  @Test
  public void itDeserializesEventsFromBrowserProtocol() throws Exception {
    SpyListener listener = new SpyListener();
    ChromeWebSocketClient client = new ChromeWebSocketClient(
      URI.create(""),
      ChromeDevToolsClientDefaults.DEFAULT_OBJECT_MAPPER,
      Collections.singletonMap("listenerId1", listener),
      ChromeDevToolsClientDefaults.DEFAULT_EXECUTOR_SERVICE,
      1000L
    );

    String json =
      "{\"method\":\"Page.domContentEventFired\",\"params\":{\"timestamp\":904169.746022}}";
    client.onMessage(json);

    Retryer<OnEventInvocation> retryer = RetryerBuilder
      .<OnEventInvocation>newBuilder()
      .retryIfResult(Objects::isNull)
      .withStopStrategy(StopStrategies.stopAfterDelay(1000))
      .build();

    OnEventInvocation args = retryer.call(listener::getLastOnEventCallArgs);
    assertThat(args.type.getClazz()).isEqualTo(DomContentEventFiredEvent.class);
    assertThat(args.event.getClass()).isEqualTo(DomContentEventFiredEvent.class);
    assertThat(args.sessionId).isNull();
  }

  @Test
  public void itDeserializesEventsFromBrowserProtocolInFlatMode() throws Exception {
    SpyListener listener = new SpyListener();
    ChromeWebSocketClient client = new ChromeWebSocketClient(
      URI.create(""),
      ChromeDevToolsClientDefaults.DEFAULT_OBJECT_MAPPER,
      Collections.singletonMap("listenerId1", listener),
      ChromeDevToolsClientDefaults.DEFAULT_EXECUTOR_SERVICE,
      1000L
    );

    String json =
      "{\"method\":\"Page.domContentEventFired\",\"params\":{\"timestamp\":904169.746022},\"sessionId\":\"CE684D56A54B9E99D1C91E14FDB80417\"}";
    client.onMessage(json);

    Retryer<OnEventInvocation> retryer = RetryerBuilder
      .<OnEventInvocation>newBuilder()
      .retryIfResult(Objects::isNull)
      .withStopStrategy(StopStrategies.stopAfterDelay(1000))
      .build();

    OnEventInvocation args = retryer.call(listener::getLastOnEventCallArgs);
    assertThat(args.type.getClazz()).isEqualTo(DomContentEventFiredEvent.class);
    assertThat(args.event.getClass()).isEqualTo(DomContentEventFiredEvent.class);
    assertThat(args.sessionId.getValue()).isEqualTo("CE684D56A54B9E99D1C91E14FDB80417");
  }

  @Test
  public void itDeserializesEventsFromJsProtocol() throws Exception {
    SpyListener listener = new SpyListener();
    ChromeWebSocketClient client = new ChromeWebSocketClient(
      URI.create(""),
      ChromeDevToolsClientDefaults.DEFAULT_OBJECT_MAPPER,
      Collections.singletonMap("listenerId1", listener),
      ChromeDevToolsClientDefaults.DEFAULT_EXECUTOR_SERVICE,
      1000L
    );

    String json =
      "{\"method\":\"Runtime.exceptionRevoked\",\"params\":{\"reason\":\"my reason\",\"exceptionId\":1}}";
    client.onMessage(json);

    Retryer<OnEventInvocation> retryer = RetryerBuilder
      .<OnEventInvocation>newBuilder()
      .retryIfResult(Objects::isNull)
      .withStopStrategy(StopStrategies.stopAfterDelay(1000))
      .build();

    OnEventInvocation args = retryer.call(listener::getLastOnEventCallArgs);
    assertThat(args.type.getClazz()).isEqualTo(ExceptionRevokedEvent.class);
  }

  @Test
  public void itIgnoresUnknownFields() throws Exception {
    ObjectMapper objectMapper = ChromeDevToolsClientDefaults.DEFAULT_OBJECT_MAPPER;

    String json =
      "{\"method\":\"CSS.fontsUpdated\",\"params\":{\"dummy_field_that_definitely_should_not_exist\":\"value\"}}";
    assertThat(objectMapper.readValue(json, Event.class))
      .isInstanceOf(FontsUpdatedEvent.class);
  }

  static class SpyListener implements ChromeEventListener {
    private OnEventInvocation lastOnEventCallParams;

    @Override
    public void onEvent(EventType type, Event event) {
      lastOnEventCallParams = new OnEventInvocation(event, type);
    }

    @Override
    public void onEvent(SessionID sessionId, EventType type, Event event) {
      lastOnEventCallParams = new OnEventInvocation(sessionId, event, type);
    }

    private OnEventInvocation getLastOnEventCallArgs() {
      return lastOnEventCallParams;
    }
  }

  private static class OnEventInvocation {
    private final SessionID sessionId;
    private final Event event;
    private final EventType type;

    public OnEventInvocation(Event event, EventType type) {
      this(null, event, type);
    }

    public OnEventInvocation(SessionID sessionId, Event event, EventType type) {
      this.sessionId = sessionId;
      this.event = event;
      this.type = type;
    }
  }
}
