package com.hubspot.chrome.devtools.client;

import com.hubspot.chrome.devtools.client.core.Event;
import com.hubspot.chrome.devtools.client.core.EventType;
import com.hubspot.chrome.devtools.client.core.target.SessionID;
import javax.annotation.Nullable;

public interface ChromeEventListener {
  void onEvent(EventType type, Event event);

  default void onEvent(@Nullable SessionID sessionId, EventType type, Event event) {
    onEvent(type, event);
  }
}
