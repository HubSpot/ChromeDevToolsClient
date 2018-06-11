package com.hubspot.chrome.devtools.client;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.hubspot.chrome.devtools.client.core.Event;
import com.hubspot.chrome.devtools.client.core.EventDeserializer;
import com.hubspot.horizon.HttpClient;
import com.hubspot.horizon.ning.NingHttpClient;

public class ChromeDevToolsClientDefaults {
  public static final ExecutorService DEFAULT_EXECUTOR_SERVICE = new ThreadPoolExecutor(10, 10, 5 * 60, TimeUnit.SECONDS, new LinkedTransferQueue<>());
  public static final ObjectMapper DEFAULT_OBJECT_MAPPER = new ObjectMapper();
  static {
    DEFAULT_OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    DEFAULT_OBJECT_MAPPER.setSerializationInclusion(Include.NON_NULL);
    SimpleModule module = new SimpleModule();
    module.addDeserializer(Event.class, new EventDeserializer());
    DEFAULT_OBJECT_MAPPER.registerModule(module);
  }

  public static final HttpClient DEFAULT_HTTP_CLIENT = new NingHttpClient();
  public static final int DEFAULT_CHROME_ACTION_TIMEOUT_MILLIS = 60 * 1000;
  public static final int DEFAULT_HTTP_CONNECTION_RETRY_TIMEOUT_MILLIS = 5 * 1000;
}
