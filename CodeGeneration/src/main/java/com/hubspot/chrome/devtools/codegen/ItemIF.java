package com.hubspot.chrome.devtools.codegen;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hubspot.chrome.devtools.base.ChromeStyle;
import java.util.Optional;
import org.immutables.value.Value.Immutable;

@Immutable
@ChromeStyle
public interface ItemIF {
  @JsonProperty("$ref")
  Optional<String> getRef();

  Optional<String> getType();
}
