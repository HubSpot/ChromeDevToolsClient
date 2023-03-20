package com.hubspot.chrome.devtools.base;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.immutables.value.Value.Immutable;

import javax.annotation.Nullable;
import java.util.Optional;

@Immutable
@ChromeStyle
@JsonIgnoreProperties(ignoreUnknown = true) // Ignore unknown properties to handle chrome adding fields to base models
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
