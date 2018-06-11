package com.hubspot.chrome.devtools.base;

import org.immutables.value.Value.Immutable;

@Immutable
@ChromeStyle
public interface ChromeSessionInfoIF {
  String getDescription();
  String getDevtoolsFrontendUrl();
  String getId();
  String getTitle();
  String getType();
  String getUrl();
  String getWebSocketDebuggerUrl();
}
