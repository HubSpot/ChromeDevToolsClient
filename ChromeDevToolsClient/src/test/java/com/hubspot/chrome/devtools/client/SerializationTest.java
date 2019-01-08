package com.hubspot.chrome.devtools.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Collections;
import java.util.Objects;

import org.junit.Test;

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

public class SerializationTest {
  @Test
  public void itIgnoresNullValues() throws Exception {
    ObjectMapper objectMapper = ChromeDevToolsClientDefaults.DEFAULT_OBJECT_MAPPER;

    assertThat(objectMapper.writeValueAsString(new CallArgument("test", null, null))).isEqualTo("{\"value\":\"test\"}");
    assertThat(objectMapper.writeValueAsString(new CallArgument("test", null, null))).isEqualTo("{\"value\":\"test\"}");
  }

  @Test
  public void itDeserializesEventsIntoCorrectTypes() throws Exception {
    ObjectMapper objectMapper = ChromeDevToolsClientDefaults.DEFAULT_OBJECT_MAPPER;

    String jsonA = "{\"method\":\"DOM.documentUpdated\",\"params\":{}}";
    assertThat(objectMapper.readValue(jsonA, Event.class)).isInstanceOf(DocumentUpdatedEvent.class);

    String jsonB = "{\"method\":\"DOM.childNodeRemoved\",\"params\":{\"parentNodeId\": 1, \"nodeId\": 2}}";
    assertThat(objectMapper.readValue(jsonB, Event.class)).isInstanceOf(ChildNodeRemovedEvent.class);
  }

  @Test
  public void itDeserializesEventsFromBrowserProtocol() throws Exception {
    SpyListener listener = new SpyListener();
    ChromeWebSocketClient client = new ChromeWebSocketClient(
        URI.create(""),
        ChromeDevToolsClientDefaults.DEFAULT_OBJECT_MAPPER,
        Collections.singletonMap("listenerId1", listener),
        ChromeDevToolsClientDefaults.DEFAULT_EXECUTOR_SERVICE,
        1000L);

    String json = "{\"method\":\"Page.domContentEventFired\",\"params\":{\"timestamp\":904169.746022}}";
    client.onMessage(json);

    Retryer<EventAndType> retryer = RetryerBuilder.<EventAndType>newBuilder()
        .retryIfResult(Objects::isNull)
        .withStopStrategy(StopStrategies.stopAfterDelay(1000))
        .build();

    EventAndType args = retryer.call(listener::getLastOnEventCallArgs);
    EventType eventType = args.getType();
    assertThat(eventType.getClazz()).isEqualTo(DomContentEventFiredEvent.class);
  }

  @Test
  public void itDeserializesEventsFromJsProtocol() throws Exception {
    SpyListener listener = new SpyListener();
    ChromeWebSocketClient client = new ChromeWebSocketClient(
        URI.create(""),
        ChromeDevToolsClientDefaults.DEFAULT_OBJECT_MAPPER,
        Collections.singletonMap("listenerId1", listener),
        ChromeDevToolsClientDefaults.DEFAULT_EXECUTOR_SERVICE,
        1000L);

    String json = "{\"method\":\"Runtime.exceptionRevoked\",\"params\":{\"reason\":\"my reason\",\"exceptionId\":1}}";
    client.onMessage(json);

    Retryer<EventAndType> retryer = RetryerBuilder.<EventAndType>newBuilder()
        .retryIfResult(Objects::isNull)
        .withStopStrategy(StopStrategies.stopAfterDelay(1000))
        .build();

    EventAndType args = retryer.call(listener::getLastOnEventCallArgs);
    EventType eventType = args.getType();
    assertThat(eventType.getClazz()).isEqualTo(ExceptionRevokedEvent.class);
  }

  @Test
  public void itIgnoresUnknownFields() throws Exception {
    ObjectMapper objectMapper = ChromeDevToolsClientDefaults.DEFAULT_OBJECT_MAPPER;

    String json = "{\"method\":\"CSS.fontsUpdated\",\"params\":{\"dummy_field_that_definitely_should_not_exist\":\"value\"}}";
    assertThat(objectMapper.readValue(json, Event.class)).isInstanceOf(FontsUpdatedEvent.class);
  }

  class SpyListener implements ChromeEventListener {
    private EventAndType lastOnEventCallParams;

    @Override
    public void onEvent(EventType type, Event event) {
      lastOnEventCallParams = new EventAndType(event, type);
    }

    public EventAndType getLastOnEventCallArgs() {
      return lastOnEventCallParams;
    }
  }

  private static class EventAndType {
    private final Event event;
    private final EventType type;

    public EventAndType(Event event, EventType type) {
      this.event = event;
      this.type = type;
    }

    public Event getEvent() {
      return event;
    }

    public EventType getType() {
      return type;
    }
  }
}
