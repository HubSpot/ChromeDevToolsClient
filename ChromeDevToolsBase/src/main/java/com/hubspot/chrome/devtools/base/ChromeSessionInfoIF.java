package com.hubspot.chrome.devtools.base;

import org.immutables.value.Value.Immutable;

import javax.annotation.Nullable;

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

  @Nullable
  String getFaviconUrl();
}
