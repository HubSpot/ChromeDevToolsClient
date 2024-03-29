package com.hubspot.chrome.devtools.codegen;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hubspot.chrome.devtools.base.ChromeStyle;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value.Immutable;

@Immutable
@ChromeStyle
public interface DomainIF {
  @JsonProperty("domain")
  String getName();

  Optional<String> getDescription();
  Optional<Boolean> getExperimental();
  Optional<Boolean> getDeprecated();

  List<Command> getCommands();
  List<String> getDependencies();
  List<Command> getEvents();
  List<Type> getTypes();
}
