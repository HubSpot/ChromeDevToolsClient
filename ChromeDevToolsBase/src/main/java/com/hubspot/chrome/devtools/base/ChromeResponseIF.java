package com.hubspot.chrome.devtools.base;

import javax.annotation.Nullable;

import org.immutables.value.Value.Immutable;

import com.fasterxml.jackson.databind.JsonNode;

@Immutable
@ChromeStyle
public interface ChromeResponseIF {
  @Nullable
  Integer getId();
  @Nullable
  JsonNode getResult();
  @Nullable
  String getMethod();
  @Nullable
  JsonNode getParams();
  @Nullable
  ChromeResponseErrorBody getError();

  default boolean isResponse() {
    return getId() != null && getResult() != null;
  }

  default boolean isEvent() {
    return getMethod() != null && getParams() != null;
  }

  default boolean isError() {
    return getError() != null;
  }
}
