package com.hubspot.chrome.devtools.codegen;

import java.util.List;
import java.util.Optional;

import org.immutables.value.Value.Immutable;

import com.hubspot.chrome.devtools.base.ChromeStyle;

@Immutable
@ChromeStyle
public interface CommandIF {
  String getName();
  Optional<String> getDescription();
  Optional<List<Property>> getReturns();
  Optional<List<Property>> getParameters();

  Optional<Boolean> getExperimental();
  Optional<Boolean> getDeprecated();
  Optional<String> getRedirect();
}
