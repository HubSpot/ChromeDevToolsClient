package com.hubspot.chrome.devtools.codegen;

import java.util.List;
import java.util.Map;

import org.immutables.value.Value.Immutable;

import com.hubspot.chrome.devtools.base.ChromeStyle;

@Immutable
@ChromeStyle
public interface ProtocolIF {
  Map<String, String> getVersion();
  List<Domain> getDomains();
}
