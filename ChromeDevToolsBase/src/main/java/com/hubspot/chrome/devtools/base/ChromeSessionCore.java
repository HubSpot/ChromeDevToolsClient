package com.hubspot.chrome.devtools.base;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.concurrent.CompletableFuture;

public interface ChromeSessionCore extends AutoCloseable {
  void send(ChromeRequest request);
  <T> T send(ChromeRequest request, TypeReference<T> valueType);
  CompletableFuture<Void> sendAsync(ChromeRequest request);
  <T> CompletableFuture<T> sendAsync(ChromeRequest request, TypeReference<T> valueType);
}
