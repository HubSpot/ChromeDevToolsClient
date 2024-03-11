package com.hubspot.chrome.devtools.base;

import javax.annotation.Nullable;
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

  /**
   * Nullable because it's not always present on devtools targets, even in 112+.
   */
  @Nullable
  String getFaviconUrl();
}
