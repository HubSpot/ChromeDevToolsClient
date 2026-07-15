package com.hubspot.chrome.devtools.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hubspot.chrome.devtools.client.exceptions.ChromeDevToolsException;
import java.net.ServerSocket;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import org.junit.Test;

public class ChromeDevToolsSessionTest {

  @Test
  public void itFailsFastWhenTheTargetCannotBeConnectedTo() throws Exception {
    ObjectMapper objectMapper = ChromeDevToolsClientDefaults.DEFAULT_OBJECT_MAPPER;
    ExecutorService executorService =
      ChromeDevToolsClientDefaults.DEFAULT_EXECUTOR_SERVICE;

    int unusedPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      unusedPort = socket.getLocalPort();
    }

    URI uri = URI.create(
      String.format("ws://localhost:%d/devtools/page/dead-target", unusedPort)
    );

    assertThatThrownBy(() ->
        new ChromeDevToolsSession(uri, objectMapper, executorService, 1000L)
      )
      .isInstanceOf(ChromeDevToolsException.class);
  }
}
