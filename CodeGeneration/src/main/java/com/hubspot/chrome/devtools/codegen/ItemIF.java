package com.hubspot.chrome.devtools.codegen;

import java.util.Optional;

import org.immutables.value.Value.Immutable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hubspot.chrome.devtools.base.ChromeStyle;

@Immutable
@ChromeStyle
public interface ItemIF {
  @JsonProperty("$ref")
  Optional<String> getRef();
  Optional<String> getType();

}
