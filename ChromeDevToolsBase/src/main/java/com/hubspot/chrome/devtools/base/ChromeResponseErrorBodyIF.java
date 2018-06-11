package com.hubspot.chrome.devtools.base;

import org.immutables.value.Value.Immutable;

@Immutable
@ChromeStyle
public interface ChromeResponseErrorBodyIF {
  Integer getCode();
  String getMessage();
}
