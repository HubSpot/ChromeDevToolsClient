package com.hubspot.chrome.devtools.codegen;

import com.hubspot.chrome.devtools.base.ChromeStyle;
import java.util.List;
import java.util.Map;
import org.immutables.value.Value.Immutable;

@Immutable
@ChromeStyle
public interface ProtocolIF {
  Map<String, String> getVersion();
  List<Domain> getDomains();
}
