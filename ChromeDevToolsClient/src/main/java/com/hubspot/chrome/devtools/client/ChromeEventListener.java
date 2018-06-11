package com.hubspot.chrome.devtools.client;

import com.hubspot.chrome.devtools.client.core.Event;
import com.hubspot.chrome.devtools.client.core.EventType;

public interface ChromeEventListener {
  void onEvent(EventType type, Event event);
}
