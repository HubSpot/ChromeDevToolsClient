package com.hubspot.chrome.devtools.base;

import java.util.Optional;

import org.immutables.value.Value.Default;
import org.immutables.value.Value.Immutable;

@Immutable
@ChromeStyle
public interface ChromiumSessionRequestIF {
  int DEFAULT_SCREEN_WIDTH = 1366;  // WXGA width
  int DEFAULT_SCREEN_HEIGHT = 768;  // WXGA height

  Optional<String> getUserAgent();

  @Default
  default boolean getUseTransparentBackground() {
    return false;
  }

  @Default
  default boolean getHideScrollBars() {
    return true;
  }

  @Default
  default int getScreenHeight() {
    return DEFAULT_SCREEN_HEIGHT;
  }

  @Default
  default int getScreenWidth() {
    return DEFAULT_SCREEN_WIDTH;
  }
}
