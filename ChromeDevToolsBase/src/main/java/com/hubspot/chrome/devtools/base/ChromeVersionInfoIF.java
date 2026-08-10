package com.hubspot.chrome.devtools.base;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.immutables.value.Value;

@Value.Immutable
@ChromeStyle
public interface ChromeVersionInfoIF {
  @JsonProperty("Browser")
  String getBrowser();

  @JsonProperty("Protocol-Version")
  String getProtocolVersion();

  @JsonProperty("User-Agent")
  String getUserAgent();

  @JsonProperty("V8-Version")
  String getV8Version();

  @JsonProperty("WebKit-Version")
  String getWebKitVersion();

  @JsonProperty("webSocketDebuggerUrl")
  String getWebSocketDebuggerUrl();
}
