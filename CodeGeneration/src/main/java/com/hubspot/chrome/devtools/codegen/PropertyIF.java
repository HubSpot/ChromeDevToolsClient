package com.hubspot.chrome.devtools.codegen;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hubspot.chrome.devtools.base.ChromeStyle;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value.Immutable;

@Immutable
@ChromeStyle
public interface PropertyIF {
  String getName();
  Optional<String> getDescription();
  Optional<Boolean> getOptional();
  Optional<Boolean> getExperimental();
  Optional<Boolean> getDeprecated();

  @JsonProperty("$ref")
  Optional<String> getRef();

  Optional<String> getType();
  Optional<Item> getItems();

  @JsonProperty("enum")
  Optional<List<String>> getEnumeration();
}
