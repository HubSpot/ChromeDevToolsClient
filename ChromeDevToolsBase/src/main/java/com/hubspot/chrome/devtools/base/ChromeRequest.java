package com.hubspot.chrome.devtools.base;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ChromeRequest {

  // It's *much* easier to implement this as a POJO instead of an immutable for two reasons:
  //
  //  1. Immutable::putParams does not allow setting null values (even with
  //     @AllowNulls and @Nullable annotations).
  //
  //  2. Using a Map<String, Optional<Object>> for params gets around the
  //     above, but then tries to serialize the optionals as nulls, even with
  //
  //       objectMapper.registerModule(new Jdk8Module());
  //       objectMapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
  //       objectMapper.setSerializationInclusion(Include.NON_NULL);
  //
  private static AtomicInteger requestNumber = new AtomicInteger();

  private final Integer id;
  private String method;
  private Map<String, Object> params;

  public ChromeRequest(String method) {
    this.id = requestNumber.getAndIncrement();
    this.method = method;
    this.params = new HashMap<>();
  }

  public Integer getId() {
    return id;
  }

  public String getMethod() {
    return method;
  }

  @JsonProperty
  public Map<String, Object> getParams() {
    return params;
  }

  public ChromeRequest setMethod(String methodName) {
    this.method = methodName;
    return this;
  }

  public ChromeRequest putParams(String key, Object value) {
    if (value != null) {
      this.params.put(key, value);
    }
    return this;
  }
}
