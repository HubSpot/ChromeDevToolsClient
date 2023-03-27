package com.hubspot.chrome.devtools.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hubspot.chrome.devtools.client.core.EventType;
import com.hubspot.chrome.devtools.client.core.runtime.ConsoleAPICalledEvent;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import org.junit.Test;

public class EventListenerTest {

  @Test
  public void itCapturesEventsToCollections() throws Exception {
    ObjectMapper objectMapper = ChromeDevToolsClientDefaults.DEFAULT_OBJECT_MAPPER;
    ExecutorService executorService =
      ChromeDevToolsClientDefaults.DEFAULT_EXECUTOR_SERVICE;
    Map<String, ChromeEventListener> listeners = new ConcurrentHashMap<>();

    ChromeWebSocketClient client = new ChromeWebSocketClient(
      URI.create(""),
      objectMapper,
      listeners,
      executorService,
      1000L
    );

    ChromeDevToolsSession chromeDevToolsSession = new ChromeDevToolsSession(
      listeners,
      client,
      objectMapper,
      executorService
    );

    EventType eventType = EventType.RUNTIME_CONSOLE_APICALLED;
    List<ConsoleAPICalledEvent> events = new ArrayList<>();
    chromeDevToolsSession.collectEvents(eventType, events);

    String json =
      "{\"method\":\"" +
      eventType.getType() +
      "\",\"params\":" +
      objectMapper.writeValueAsString(
        new ConsoleAPICalledEvent(
          "myType",
          Collections.emptyList(),
          null,
          null,
          null,
          "myContext"
        )
      ) +
      "}";

    client.onMessage(json);

    Thread.sleep(10); // debounce for the executor service handling messages

    assertThat(events.size()).isEqualTo(1);
    ConsoleAPICalledEvent consoleAPICalledEvent = events.get(0);
    assertThat(consoleAPICalledEvent.getType()).isEqualTo("myType");
    assertThat(consoleAPICalledEvent.getContext()).isEqualTo("myContext");
  }

  @Test
  public void itAddsCustomConsumers() throws Exception {
    ObjectMapper objectMapper = ChromeDevToolsClientDefaults.DEFAULT_OBJECT_MAPPER;
    ExecutorService executorService =
      ChromeDevToolsClientDefaults.DEFAULT_EXECUTOR_SERVICE;
    Map<String, ChromeEventListener> listeners = new ConcurrentHashMap<>();

    ChromeWebSocketClient client = new ChromeWebSocketClient(
      URI.create(""),
      objectMapper,
      listeners,
      executorService,
      1000L
    );

    ChromeDevToolsSession chromeDevToolsSession = new ChromeDevToolsSession(
      listeners,
      client,
      objectMapper,
      executorService
    );

    EventType eventType = EventType.RUNTIME_CONSOLE_APICALLED;
    List<ConsoleAPICalledEvent> events = new ArrayList<>();
    chromeDevToolsSession.addEventConsumer(
      eventType,
      e -> events.add((ConsoleAPICalledEvent) e)
    );

    String json =
      "{\"method\":\"" +
      eventType.getType() +
      "\",\"params\":" +
      objectMapper.writeValueAsString(
        new ConsoleAPICalledEvent(
          "myType",
          Collections.emptyList(),
          null,
          null,
          null,
          "myContext"
        )
      ) +
      "}";

    client.onMessage(json);

    Thread.sleep(10); // debounce for the executor service handling messages

    assertThat(events.size()).isEqualTo(1);
    ConsoleAPICalledEvent consoleAPICalledEvent = events.get(0);
    assertThat(consoleAPICalledEvent.getType()).isEqualTo("myType");
    assertThat(consoleAPICalledEvent.getContext()).isEqualTo("myContext");
  }
}
