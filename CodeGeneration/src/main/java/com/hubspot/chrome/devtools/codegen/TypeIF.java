package com.hubspot.chrome.devtools.codegen;

import java.util.List;
import java.util.Optional;

import org.immutables.value.Value.Immutable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hubspot.chrome.devtools.base.ChromeStyle;

@Immutable
@ChromeStyle
public interface TypeIF {
  @JsonProperty("id")
  String getName();
  Optional<String> getDescription();

  String getType();
  Optional<Item> getItems();
  Optional<List<Property>> getProperties();
  Optional<List<String>> getEnum();

  Optional<Boolean> getExperimental();
  Optional<Boolean> getDeprecated();
}
